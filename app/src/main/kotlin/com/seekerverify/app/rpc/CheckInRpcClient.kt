package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.model.CheckInStreak
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * Scans on-chain memo transactions to reconstruct check-in streak after reinstall.
 * Looks for SV:CI:$date:$streak memos signed by the wallet via Seed Vault.
 *
 * Uses the `memo` field from getSignaturesForAddress (no need to fetch full tx details).
 */
object CheckInRpcClient {

    private const val TAG = "SeekerVerify"
    private const val SIGNATURE_BATCH_SIZE = 1000
    private const val MAX_BATCHES = 3           // up to 3000 signatures — enough for check-in history
    private const val RETRY_ATTEMPTS = 5
    private const val RETRY_BASE_DELAY_MS = 5000L

    /**
     * Result of on-chain check-in scan.
     */
    data class OnChainCheckInResult(
        val streak: CheckInStreak,
        val lastSignature: String,
        val lastDate: String
    )

    /**
     * Scan the wallet's signature history for SV:CI memos and reconstruct check-in state.
     * The memo field is included directly in getSignaturesForAddress results,
     * so no full transaction fetching is needed — makes this very fast.
     *
     * Returns null if no on-chain check-ins found.
     */
    suspend fun restoreFromChain(
        walletAddress: String,
        rpcUrl: String
    ): OnChainCheckInResult? {
        val checkInEntries = mutableListOf<Triple<String, String, String>>() // (date, streak, signature)

        try {
            var beforeSignature: String? = null
            var batches = 0

            while (batches < MAX_BATCHES) {
                val params = buildJsonArray {
                    add(JsonPrimitive(walletAddress))
                    add(buildJsonObject {
                        put("limit", SIGNATURE_BATCH_SIZE)
                        beforeSignature?.let { put("before", it) }
                    })
                }

                // Retry wrapper for rate limit resilience
                var success = false
                for (attempt in 0 until RETRY_ATTEMPTS) {
                    if (attempt > 0) {
                        val waitMs = RETRY_BASE_DELAY_MS * attempt
                        Log.w(TAG, "CheckIn scan: retry $attempt, waiting ${waitMs}ms")
                        delay(waitMs)
                    }

                    val result = RpcProvider.call(rpcUrl, "getSignaturesForAddress", params)
                    result.fold(
                        onSuccess = { response ->
                            val signatures = response.jsonArray
                            if (signatures.isEmpty()) {
                                batches = MAX_BATCHES
                            } else {
                                for (sig in signatures) {
                                    val sigObj = sig.jsonObject
                                    val signature = sigObj["signature"]?.jsonPrimitive?.content ?: continue
                                    val memo = sigObj["memo"]?.jsonPrimitive?.content

                                    // Memo format from RPC: "[18] SV:CI:2026-03-05:1" or similar
                                    if (memo != null && memo.contains("SV:CI:")) {
                                        // Strip the "[N] " prefix if present
                                        val svPart = memo.substringAfter("SV:CI:")
                                        val parts = svPart.split(":")
                                        if (parts.isNotEmpty()) {
                                            val date = parts[0]
                                            val streakStr = parts.getOrElse(1) { "0" }
                                            checkInEntries.add(Triple(date, streakStr, signature))
                                        }
                                    }
                                }
                                beforeSignature = signatures.last().jsonObject["signature"]?.jsonPrimitive?.content
                                if (signatures.size < SIGNATURE_BATCH_SIZE) batches = MAX_BATCHES
                            }
                            success = true
                        },
                        onFailure = {
                            val is429 = it.message?.contains("429") == true
                            if (!is429 || attempt == RETRY_ATTEMPTS - 1) {
                                Log.w(TAG, "CheckIn scan signature batch failed: ${it.message}")
                                batches = MAX_BATCHES
                                success = true // exit retry loop
                            }
                        }
                    )
                    if (success) break
                }

                batches++
                // Pace between batches
                if (batches < MAX_BATCHES) delay(2000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "CheckIn scan failed: ${e.message}")
            return null
        }

        if (checkInEntries.isEmpty()) {
            Log.w(TAG, "CheckIn scan: no SV:CI memos found on-chain")
            return null
        }

        Log.w(TAG, "CheckIn scan: found ${checkInEntries.size} on-chain check-ins")

        // Reconstruct streak from sorted unique dates
        val uniqueDates = checkInEntries.map { it.first }.distinct().sortedDescending()
        val totalCheckIns = uniqueDates.size

        // Compute current streak (consecutive days ending at most recent check-in)
        var currentStreak = 1
        for (i in 1 until uniqueDates.size) {
            val current = try { LocalDate.parse(uniqueDates[i - 1]) } catch (_: Exception) { break }
            val previous = try { LocalDate.parse(uniqueDates[i]) } catch (_: Exception) { break }
            if (current.minusDays(1) == previous) {
                currentStreak++
            } else {
                break
            }
        }

        // Check if the most recent check-in was today or yesterday (streak is still active)
        val mostRecentDate = try { LocalDate.parse(uniqueDates[0]) } catch (_: Exception) { null }
        val today = LocalDate.now()
        if (mostRecentDate != null && mostRecentDate != today && mostRecentDate != today.minusDays(1)) {
            currentStreak = 0
        }

        // Compute longest streak from all dates
        val sortedAsc = uniqueDates.sortedBy { it }
        var longestStreak = 1
        var runLength = 1
        for (i in 1 until sortedAsc.size) {
            val prev = try { LocalDate.parse(sortedAsc[i - 1]) } catch (_: Exception) { break }
            val curr = try { LocalDate.parse(sortedAsc[i]) } catch (_: Exception) { break }
            if (prev.plusDays(1) == curr) {
                runLength++
                if (runLength > longestStreak) longestStreak = runLength
            } else {
                runLength = 1
            }
        }

        val lastDate = uniqueDates[0]
        val lastSig = checkInEntries.first { it.first == lastDate }.third

        val streak = CheckInStreak(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastCheckInDate = lastDate,
            totalCheckIns = totalCheckIns
        )

        Log.w(TAG, "CheckIn scan: restored streak=$currentStreak, longest=$longestStreak, total=$totalCheckIns, last=$lastDate")
        return OnChainCheckInResult(streak = streak, lastSignature = lastSig, lastDate = lastDate)
    }
}
