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
    private const val SIGNATURE_BATCH_SIZE = 1000 // max allowed per request
    private const val MAX_BATCHES = 10 // up to 10,000 signatures

    /**
     * Gather activity metrics for the predictor.
     * Scans wallet signature history and token accounts.
     *
     * @param maxBatches Number of signature batches to fetch (1 batch = 1000 sigs).
     *   Default 10 (10K sigs) for full S2 prediction. Use 3 for lighter S1 analysis.
     */
    suspend fun getActivityMetrics(
        walletAddress: String,
        rpcUrl: String,
        isStaked: Boolean,
        hasSkrDomain: Boolean,
        maxBatches: Int = MAX_BATCHES
    ): PredictorEngine.ActivityMetrics {
        var totalTransactions = 0
        var tokenDiversity = 0
        var nftCount = 0
        var walletAgeDays = 0
        var dappInteractions = 0

        // 1. Get signature history (up to 10 batches x 1000 = 10,000 signatures)
        try {
            var beforeSignature: String? = null
            var batches = 0
            var oldestTimestamp: Long? = null

            while (batches < maxBatches) {
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
                            batches = maxBatches // stop — no more history
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

                            // Count successful transactions as dApp interactions
                            val err = sigObj["err"]
                            if (err == null || err.toString() == "null") {
                                dappInteractions++
                            }
                        }

                        // Get the last signature for pagination
                        beforeSignature = signatures.last().jsonObject["signature"]?.jsonPrimitive?.content

                        // If we got fewer than the batch size, we've reached the end
                        if (signatures.size < SIGNATURE_BATCH_SIZE) {
                            batches = maxBatches // stop
                        }
                    },
                    onFailure = {
                        Log.w(TAG, "Signature batch $batches failed: ${it.message}")
                        batches = maxBatches // stop on error
                    }
                )
                batches++
            }

            // Calculate wallet age from oldest transaction
            oldestTimestamp?.let { oldest ->
                val oldestInstant = Instant.ofEpochSecond(oldest)
                walletAgeDays = ChronoUnit.DAYS.between(oldestInstant, Instant.now()).toInt()
            }

            Log.d(TAG, "Activity: $totalTransactions txs, ${walletAgeDays}d old, $dappInteractions interactions (${batches} batches)")
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

                    // Count only fungible tokens with non-zero balance (exclude NFTs and empty ATAs)
                    var fungibleCount = 0
                    for (account in accounts) {
                        val parsed = account.jsonObject["account"]?.jsonObject
                            ?.get("data")?.jsonObject
                            ?.get("parsed")?.jsonObject
                            ?.get("info")?.jsonObject
                        val tokenAmount = parsed?.get("tokenAmount")?.jsonObject
                        val amount = tokenAmount?.get("amount")?.jsonPrimitive?.content
                        val decimals = tokenAmount?.get("decimals")?.jsonPrimitive?.content?.toIntOrNull()
                        val uiAmount = tokenAmount?.get("uiAmount")?.jsonPrimitive?.content?.toDoubleOrNull()

                        if (amount == null || amount == "0") continue // skip zero-balance ATAs

                        if (decimals == 0 && amount == "1") {
                            // NFT: amount == 1 with 0 decimals
                            nftCount++
                        } else if (decimals != null && decimals > 0 && (uiAmount ?: 0.0) > 0.0) {
                            // Fungible token with non-zero balance
                            fungibleCount++
                        }
                    }
                    tokenDiversity = fungibleCount

                    Log.d(TAG, "Tokens: $tokenDiversity fungible, $nftCount NFTs (${accounts.size} total ATAs)")
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
            stakingDurationDays = 0, // set by PredictorViewModel from share price data
            skrStaked = isStaked,
            hasSkrDomain = hasSkrDomain,
            nftCount = nftCount,
            walletAgeDays = walletAgeDays,
            dappInteractions = dappInteractions,
            season1Tier = null // will be set by PredictorViewModel if available
        )
    }
}
