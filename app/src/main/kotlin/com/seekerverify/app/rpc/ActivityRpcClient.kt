package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.engine.PredictorEngine
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Fetches on-chain activity metrics for the Predictor engine.
 * Uses getSignaturesForAddress (sampling) and getTokenAccountsByOwner.
 */
object ActivityRpcClient {

    private const val TAG = "SeekerVerify"
    private const val SIGNATURE_BATCH_SIZE = 100 // per request limit

    /**
     * Gather activity metrics for the predictor.
     * We sample recent signatures and token accounts to build a profile.
     */
    suspend fun getActivityMetrics(
        walletAddress: String,
        rpcUrl: String,
        isStaked: Boolean,
        hasSkrDomain: Boolean
    ): PredictorEngine.ActivityMetrics {
        var totalTransactions = 0
        val uniquePrograms = mutableSetOf<String>()
        var tokenDiversity = 0
        var nftCount = 0
        var walletAgeDays = 0
        var dappInteractions = 0

        // 1. Get recent signatures (up to 3 batches = 300 signatures)
        try {
            var beforeSignature: String? = null
            var batches = 0
            var oldestTimestamp: Long? = null

            while (batches < 3) {
                val params = buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(walletAddress))
                    add(buildJsonObject {
                        put("limit", SIGNATURE_BATCH_SIZE)
                        beforeSignature?.let { put("before", it) }
                    })
                }

                val result = RpcProvider.call(rpcUrl, "getSignaturesForAddress", params)
                result.fold(
                    onSuccess = { response ->
                        val signatures = response.jsonArray
                        if (signatures.isEmpty()) {
                            batches = 3 // stop
                            return@fold
                        }

                        totalTransactions += signatures.size

                        for (sig in signatures) {
                            val sigObj = sig.jsonObject
                            val blockTime = sigObj["blockTime"]?.jsonPrimitive?.content?.toLongOrNull()
                            if (blockTime != null) {
                                if (oldestTimestamp == null || blockTime < oldestTimestamp!!) {
                                    oldestTimestamp = blockTime
                                }
                            }

                            // Check for errors — successful TXs are more valuable
                            val err = sigObj["err"]
                            if (err == null || err.toString() == "null") {
                                dappInteractions++
                            }
                        }

                        // Get the last signature for pagination
                        beforeSignature = signatures.last().jsonObject["signature"]?.jsonPrimitive?.content
                    },
                    onFailure = {
                        batches = 3 // stop on error
                    }
                )
                batches++
            }

            // Calculate wallet age from oldest transaction
            oldestTimestamp?.let { oldest ->
                val oldestInstant = Instant.ofEpochSecond(oldest)
                walletAgeDays = ChronoUnit.DAYS.between(oldestInstant, Instant.now()).toInt()
            }

            Log.d(TAG, "Activity: $totalTransactions txs, ${walletAgeDays}d old, $dappInteractions interactions")
        } catch (e: Exception) {
            Log.e(TAG, "Signature fetch failed: ${e.message}")
        }

        // 2. Get token accounts for diversity count
        try {
            val tokenParams = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(walletAddress))
                add(buildJsonObject {
                    put("programId", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
                })
                add(buildJsonObject {
                    put("encoding", "jsonParsed")
                })
            }

            val tokenResult = RpcProvider.call(rpcUrl, "getTokenAccountsByOwner", tokenParams)
            tokenResult.fold(
                onSuccess = { response ->
                    val accounts = response.jsonObject["value"]?.jsonArray ?: return@fold
                    tokenDiversity = accounts.size

                    // Count NFTs (amount == 1, decimals == 0)
                    for (account in accounts) {
                        val parsed = account.jsonObject["account"]?.jsonObject
                            ?.get("data")?.jsonObject
                            ?.get("parsed")?.jsonObject
                            ?.get("info")?.jsonObject
                        val tokenAmount = parsed?.get("tokenAmount")?.jsonObject
                        val amount = tokenAmount?.get("amount")?.jsonPrimitive?.content
                        val decimals = tokenAmount?.get("decimals")?.jsonPrimitive?.content?.toIntOrNull()

                        if (amount == "1" && decimals == 0) {
                            nftCount++
                        }
                    }

                    Log.d(TAG, "Tokens: $tokenDiversity unique, $nftCount NFTs")
                },
                onFailure = {
                    Log.e(TAG, "Token accounts fetch failed: ${it.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token diversity fetch failed: ${e.message}")
        }

        // 3. Estimate unique programs from transaction count (approximation)
        // In reality we'd parse each transaction, but that's too expensive.
        // Estimate: sqrt(totalTx) as a rough proxy
        val estimatedPrograms = kotlin.math.sqrt(totalTransactions.toDouble()).toInt()
            .coerceIn(0, 50)

        return PredictorEngine.ActivityMetrics(
            totalTransactions = totalTransactions,
            uniquePrograms = estimatedPrograms,
            tokenDiversity = tokenDiversity,
            stakingDurationDays = if (isStaked) 90 else 0, // estimate, would need stake timestamp
            skrStaked = isStaked,
            hasSkrDomain = hasSkrDomain,
            nftCount = nftCount,
            walletAgeDays = walletAgeDays,
            dappInteractions = dappInteractions,
            season1Tier = null // will be set by PredictorViewModel if available
        )
    }
}
