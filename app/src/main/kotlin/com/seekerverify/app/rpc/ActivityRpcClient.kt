package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.engine.PredictorEngine
import com.seekerverify.app.model.TransactionRecord
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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
 * Uses getSignaturesForAddress + getTransaction (batch) to get real program usage data.
 */
object ActivityRpcClient {

    private const val TAG = "SeekerVerify"
    private const val SIGNATURE_BATCH_SIZE = 1000
    private const val MAX_BATCHES = 10               // up to 10,000 signatures
    private const val MAX_PARSE_TX = 100             // parse full details for top 100 most recent txs
    private const val RPC_BATCH_SIZE = 50            // sub-requests per callBatch HTTP call

    /** System/infrastructure programs that don't count as dApp interactions. */
    private val SYSTEM_PROGRAMS = setOf(
        "11111111111111111111111111111111",
        "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
        "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJe1bS8ot",
        "ComputeBudget111111111111111111111111111111",
        "Vote111111111111111111111111111111111111111h",
        "SysvarRent111111111111111111111111111111111",
        "SysvarC1ock11111111111111111111111111111111",
        "TokenzQdBNbTqyn7Zekh3F5Bvd1pNtnNrBNj1xyX3cx",
        "Stake11111111111111111111111111111111111111",
        "StakeConfig11111111111111111111111111111111",
        "AddressLookupTab1e1111111111111111111111111",
        "BPFLoaderUpgradeab1e11111111111111111111111",
        "KeccakSecp256k11111111111111111111111111111"
    )

    /** Known program IDs → human-readable names for the activity feed. */
    private val KNOWN_PROGRAMS = mapOf(
        "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4" to "Jupiter",
        "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN" to "Jupiter v3",
        "jupoNjAxXgZ4rjzxzPMP4XXi1yoQQBkEkV1SBSi3bgf" to "Jupiter Limit",
        "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc" to "Orca Whirlpool",
        "9W959DqEETiGZocYWCQPaJ6sBmUzgfxXfqGeTEdp3aQP" to "Orca",
        "DjVE6JNiYqPL2QXyCUUh8rNjHrbz9hXHNYt99MQ59qw1" to "Orca v1",
        "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8" to "Raydium AMM",
        "RVKd61ztZW9GUwhRbbLoYVRE5Xf1B2tVscKqwZqXgEr" to "Raydium CLMM",
        "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK" to "Raydium CPMM",
        "srmqPvymJeFKQ4zGQed1GFppgkRHL9kaELCbyksJtPX" to "OpenBook",
        "M2mx93ekt1fmXSVkTrUL9xVFHkmME8HTUi5Cyc5aF7K" to "Magic Eden",
        "TSWAPaqyCSx2KABk68Shruf4rp7CxcAi9utXiFDour" to "Tensor",
        "PhoeNiXZ8ByJGLkxNfZRnkUfjvmuYqLR89jjFHGqdXY" to "Phoenix",
        "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s" to "Metaplex",
        "p1exdMJcjVao65QdewkaZRUnU6VPSXhus9n2GzWfh98" to "Metaplex Auction",
        "cndy3Z4yapfJBmL3ShUp5exZkqLc1VPjwAny8kKbNtA" to "Candy Machine",
        "stkitrT1Uoy18Dk1fTrgPw8W6MVzoCfYoAFT4MLsmhq" to "SKR Staking",
        "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ" to "SKR Staking",
        "DCA265Vj8a9CEuX1eb1LWRnDT7uK72pqsIF4K1VJgnP" to "Jupiter DCA",
        "MERLuDFBMmsHnsBPZw2sDQZHvXFMwp8EdjudcU2HKky" to "Mercurial",
        "LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo" to "Meteora DLMM",
        "Eo7WjKq67rjJQDd81erLE2hGeBLUkoqkudXcrqSkbpAo" to "Meteora AMM",
        "ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK" to "ANS Domains",
        "TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S" to ".skr Domains",
        "So11111111111111111111111111111111111111112" to "wSOL"
    )

    /**
     * Wrapper returned by getActivityMetrics — includes both the scoring metrics
     * and the parsed recent transaction records for the activity feed.
     */
    data class ActivityResult(
        val metrics: PredictorEngine.ActivityMetrics,
        val recentTransactions: List<TransactionRecord>
    )

    /**
     * Gather activity metrics for the predictor + recent transaction records.
     *
     * @param maxBatches Number of signature batches to fetch (1 batch = 1000 sigs).
     *   Default 10 (10K sigs) for full S2 prediction. Use 3 for lighter S1 analysis.
     * @param periodEndEpoch If set, only count transactions with blockTime <= this value.
     *   Pass AppConfig.Season2 start epoch for S1-only analysis.
     */
    suspend fun getActivityMetrics(
        walletAddress: String,
        rpcUrl: String,
        isStaked: Boolean,
        hasSkrDomain: Boolean,
        maxBatches: Int = MAX_BATCHES,
        periodEndEpoch: Long? = null
    ): ActivityResult {
        var totalTransactions = 0
        var tokenDiversity = 0
        var nftCount = 0
        var walletAgeDays = 0

        val collectedSignatures = mutableListOf<String>() // for program parsing
        val activeDays = mutableSetOf<String>() // unique days with ≥1 tx (last 90 days)
        val ninetyDaysAgoEpoch = Instant.now().minus(90, ChronoUnit.DAYS).epochSecond

        // ── 1. Signature history ─────────────────────────────────────────────
        try {
            var beforeSignature: String? = null
            var batches = 0
            var oldestTimestamp: Long? = null

            while (batches < maxBatches) {
                val params = buildJsonArray {
                    add(JsonPrimitive(walletAddress))
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
                            batches = maxBatches
                            return@fold
                        }

                        for (sig in signatures) {
                            val sigObj = sig.jsonObject
                            val blockTime = sigObj["blockTime"]?.jsonPrimitive?.content?.toLongOrNull()

                            // Period filter: skip transactions after periodEndEpoch
                            if (periodEndEpoch != null && blockTime != null && blockTime > periodEndEpoch) {
                                continue
                            }
                            // If we've gone past the period start (blockTime well before), stop
                            if (periodEndEpoch != null && blockTime != null && blockTime < (periodEndEpoch - 3 * 365 * 86400L)) {
                                batches = maxBatches // 3 years before period end — no point scanning further
                                return@fold
                            }

                            totalTransactions++

                            if (blockTime != null) {
                                if (oldestTimestamp == null || blockTime < oldestTimestamp!!) {
                                    oldestTimestamp = blockTime
                                }
                                // Track unique active days (last 90 days)
                                if (blockTime >= ninetyDaysAgoEpoch) {
                                    val day = Instant.ofEpochSecond(blockTime)
                                        .atZone(java.time.ZoneOffset.UTC)
                                        .toLocalDate().toString()
                                    activeDays.add(day)
                                }
                            }

                            val signature = sigObj["signature"]?.jsonPrimitive?.content
                            if (signature != null) {
                                collectedSignatures.add(signature)
                            }
                        }

                        beforeSignature = signatures.last().jsonObject["signature"]?.jsonPrimitive?.content

                        if (signatures.size < SIGNATURE_BATCH_SIZE) {
                            batches = maxBatches
                        }
                    },
                    onFailure = {
                        Log.w(TAG, "Signature batch $batches failed: ${it.message}")
                        batches = maxBatches
                    }
                )
                batches++
            }

            oldestTimestamp?.let { oldest ->
                val oldestInstant = Instant.ofEpochSecond(oldest)
                walletAgeDays = ChronoUnit.DAYS.between(oldestInstant, Instant.now()).toInt()
            }

            Log.d(TAG, "Activity: $totalTransactions txs, ${walletAgeDays}d old, ${collectedSignatures.size} sigs, ${activeDays.size} active days (90d)")
        } catch (e: Exception) {
            Log.e(TAG, "Signature fetch failed: ${e.message}")
        }

        // ── 2. Token accounts for diversity count ────────────────────────────
        try {
            val tokenParams = buildJsonArray {
                add(JsonPrimitive(walletAddress))
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

                        if (amount == null || amount == "0") continue

                        if (decimals == 0 && amount == "1") {
                            nftCount++
                        } else if (decimals != null && decimals > 0 && (uiAmount ?: 0.0) > 0.0) {
                            fungibleCount++
                        }
                    }
                    tokenDiversity = fungibleCount
                    Log.d(TAG, "Tokens: $tokenDiversity fungible, $nftCount NFTs")
                },
                onFailure = { Log.e(TAG, "Token accounts fetch failed: ${it.message}") }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token diversity fetch failed: ${e.message}")
        }

        // ── 3. Parse transaction details for real program data ───────────────
        val (uniquePrograms, dappInteractions, recentTxRecords) = parseTransactionDetails(
            collectedSignatures.take(MAX_PARSE_TX),
            rpcUrl
        )

        Log.d(TAG, "Parsed ${collectedSignatures.size.coerceAtMost(MAX_PARSE_TX)} txs: " +
            "$uniquePrograms unique programs, $dappInteractions dApp interactions")

        return ActivityResult(
            metrics = PredictorEngine.ActivityMetrics(
                totalTransactions = totalTransactions,
                uniquePrograms = uniquePrograms,
                tokenDiversity = tokenDiversity,
                stakingDurationDays = 0, // set by PredictorViewModel from share price data
                skrStaked = isStaked,
                hasSkrDomain = hasSkrDomain,
                nftCount = nftCount,
                walletAgeDays = walletAgeDays,
                dappInteractions = dappInteractions,
                uniqueActiveDays = activeDays.size,
                season1Tier = null // set by PredictorViewModel if available
            ),
            recentTransactions = recentTxRecords
        )
    }

    /**
     * Parse full transaction details for a list of signatures using batch RPC calls.
     * Returns (uniquePrograms count, dappInteractions count, TransactionRecord list).
     */
    private suspend fun parseTransactionDetails(
        signatures: List<String>,
        rpcUrl: String
    ): Triple<Int, Int, List<TransactionRecord>> {
        if (signatures.isEmpty()) return Triple(0, 0, emptyList())

        val uniqueProgramIds = mutableSetOf<String>()
        var dappInteractionCount = 0
        val records = mutableListOf<TransactionRecord>()

        // Send in batches of RPC_BATCH_SIZE to stay within response size limits
        val chunks = signatures.chunked(RPC_BATCH_SIZE)

        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val requests: List<Pair<String, JsonElement>> = chunk.map { sig ->
                "getTransaction" to buildJsonArray {
                    add(JsonPrimitive(sig))
                    add(buildJsonObject {
                        put("encoding", "jsonParsed")
                        put("maxSupportedTransactionVersion", 0)
                    })
                }
            }

            val results = RpcProvider.callBatch(rpcUrl, requests)
            Log.d(TAG, "Parsed tx chunk ${chunkIndex + 1}/${chunks.size} (${chunk.size} txs)")

            for ((i, result) in results.withIndex()) {
                result.fold(
                    onSuccess = { response ->
                        val tx = response.jsonObject

                        val blockTime = tx["blockTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        val success = tx["meta"]?.jsonObject?.get("err").let {
                            it == null || it.toString() == "null"
                        }

                        val programs = mutableSetOf<String>()

                        // Top-level instructions
                        tx["transaction"]?.jsonObject
                            ?.get("message")?.jsonObject
                            ?.get("instructions")?.jsonArray
                            ?.forEach { ix ->
                                ix.jsonObject["programId"]?.jsonPrimitive?.content
                                    ?.let { programs.add(it) }
                            }

                        // Inner instructions
                        tx["meta"]?.jsonObject
                            ?.get("innerInstructions")?.jsonArray
                            ?.forEach { inner ->
                                inner.jsonObject["instructions"]?.jsonArray
                                    ?.forEach { ix ->
                                        ix.jsonObject["programId"]?.jsonPrimitive?.content
                                            ?.let { programs.add(it) }
                                    }
                            }

                        val dappPrograms = programs.filter { it !in SYSTEM_PROGRAMS }
                        val isDapp = dappPrograms.isNotEmpty()

                        uniqueProgramIds.addAll(dappPrograms)
                        if (isDapp) dappInteractionCount++

                        // Human-readable program names for display (top 3 non-system programs)
                        val programNames = dappPrograms.take(3).map { id ->
                            KNOWN_PROGRAMS[id] ?: "${id.take(8)}..."
                        }

                        records.add(
                            TransactionRecord(
                                signature = chunk[i].take(16),
                                blockTime = blockTime,
                                success = success,
                                topPrograms = programNames.ifEmpty { listOf("System") },
                                isDapp = isDapp
                            )
                        )
                    },
                    onFailure = { /* skip individual tx parse failures silently */ }
                )
            }
        }

        return Triple(uniqueProgramIds.size, dappInteractionCount, records)
    }
}
