package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.AppConfig
import com.seekerverify.app.model.AirdropTier
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Detects the user's Season 1 airdrop tier by scanning on-chain claim transactions.
 *
 * Approach:
 * 1. Find the wallet's SKR ATA (Associated Token Account)
 * 2. Fetch transaction signatures for that ATA within the Season 1 claim window
 * 3. Parse candidate transactions for incoming SPL Token transfers matching tier amounts
 * 4. Fallback: check current balance + staked amount against tier amounts
 */
object Season1RpcClient {

    private const val TAG = "SeekerVerify"
    private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private const val SIGNATURE_BATCH_SIZE = 1000 // max per RPC call

    // Known Season 1 tier amounts in raw units (6 decimals)
    private val TIER_AMOUNTS = setOf(
        5_000_000_000L,     // Scout: 5,000 SKR
        10_000_000_000L,    // Prospector: 10,000 SKR
        40_000_000_000L,    // Vanguard: 40,000 SKR
        125_000_000_000L,   // Luminary: 125,000 SKR
        750_000_000_000L    // Sovereign / Developer: 750,000 SKR
    )

    data class Season1ClaimResult(
        val tier: AirdropTier?,
        val claimSignature: String?,
        val claimTimestamp: Long?,
        val rawAmount: Long?
    )

    /**
     * Detect the user's Season 1 airdrop tier from on-chain data.
     */
    suspend fun detectSeason1Tier(
        walletAddress: String,
        rpcUrl: String
    ): Result<Season1ClaimResult> {
        return try {
            // Step 1: Find the SKR ATA
            val ataResult = findSkrAta(walletAddress, rpcUrl)
            val ataAddress = ataResult
                ?: return Result.success(Season1ClaimResult(null, null, null, null))

            Log.d(TAG, "S1: Found SKR ATA: ${ataAddress.take(8)}...")

            // Step 2: Scan ATA signatures within claim window
            val claimResult = scanForClaimTransaction(ataAddress, rpcUrl)
            if (claimResult.tier != null) {
                Log.d(TAG, "S1: Detected tier from claim TX: ${claimResult.tier.displayName}")
                return Result.success(claimResult)
            }

            // Step 3: Fallback — check balance + staked against tier amounts
            Log.d(TAG, "S1: No claim TX found, trying balance fallback")
            val fallbackResult = tryBalanceFallback(walletAddress, rpcUrl)
            Result.success(fallbackResult)
        } catch (e: Exception) {
            Log.e(TAG, "S1 detection error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Find the wallet's SKR Associated Token Account address.
     */
    private suspend fun findSkrAta(walletAddress: String, rpcUrl: String): String? {
        val params = buildJsonArray {
            add(JsonPrimitive(walletAddress))
            add(buildJsonObject {
                put("mint", AppConfig.Tokens.SKR_MINT)
            })
            add(buildJsonObject {
                put("encoding", "jsonParsed")
            })
        }

        val result = RpcProvider.call(rpcUrl, "getTokenAccountsByOwner", params)
        return result.fold(
            onSuccess = { response ->
                val accounts = response.jsonObject["value"]?.jsonArray
                if (accounts == null || accounts.isEmpty()) null
                else accounts.first().jsonObject["pubkey"]?.jsonPrimitive?.content
            },
            onFailure = { null }
        )
    }

    /**
     * Scan ATA transaction signatures for a claim transaction within the Season 1 window.
     * Looks for incoming transfers matching known tier amounts.
     */
    private suspend fun scanForClaimTransaction(
        ataAddress: String,
        rpcUrl: String
    ): Season1ClaimResult {
        var beforeSignature: String? = null
        var batches = 0

        // Collect candidate signatures within the claim window
        val candidates = mutableListOf<String>()

        while (batches < 5) {
            val params = buildJsonArray {
                add(JsonPrimitive(ataAddress))
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
                        Log.d(TAG, "S1: Batch $batches — empty (no more signatures)")
                        batches = 5 // stop
                        return@fold
                    }

                    // Log the time range of this batch
                    val firstTime = signatures.first().jsonObject["blockTime"]?.jsonPrimitive?.content?.toLongOrNull()
                    val lastTime = signatures.last().jsonObject["blockTime"]?.jsonPrimitive?.content?.toLongOrNull()
                    Log.d(TAG, "S1: Batch $batches — ${signatures.size} sigs, time range: $lastTime to $firstTime " +
                        "(window: ${AppConfig.Season1.CLAIM_START_EPOCH} to ${AppConfig.Season1.CLAIM_END_EPOCH})")

                    for (sig in signatures) {
                        val sigObj = sig.jsonObject
                        val blockTime = sigObj["blockTime"]?.jsonPrimitive?.content?.toLongOrNull()
                            ?: continue

                        // Only look within the claim window
                        if (blockTime < AppConfig.Season1.CLAIM_START_EPOCH) {
                            Log.d(TAG, "S1: Reached before claim window at blockTime=$blockTime, stopping")
                            batches = 5 // past the window, stop scanning
                            break
                        }
                        if (blockTime <= AppConfig.Season1.CLAIM_END_EPOCH) {
                            val signature = sigObj["signature"]?.jsonPrimitive?.content
                            val err = sigObj["err"]
                            // Only consider successful transactions
                            if (signature != null && (err == null || err.toString() == "null")) {
                                candidates.add(signature)
                                Log.d(TAG, "S1: Candidate sig: ${signature.take(16)}... at blockTime=$blockTime")
                            }
                        }
                    }

                    beforeSignature = signatures.last().jsonObject["signature"]?.jsonPrimitive?.content

                    // If we got fewer than batch size, we've reached the end of history
                    if (signatures.size < SIGNATURE_BATCH_SIZE) {
                        Log.d(TAG, "S1: Batch $batches had ${signatures.size} < $SIGNATURE_BATCH_SIZE, end of history")
                        batches = 5
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "S1: Batch $batches failed: ${e.message}")
                    batches = 5 // stop on error
                }
            )
            batches++
        }

        Log.d(TAG, "S1: Found ${candidates.size} candidate signatures in claim window")

        // Parse each candidate transaction for tier-amount transfers
        for (signature in candidates) {
            val claimResult = parseTransactionForClaim(signature, ataAddress, rpcUrl)
            if (claimResult != null) {
                return claimResult
            }
        }

        return Season1ClaimResult(null, null, null, null)
    }

    /**
     * Parse a single transaction to check if it contains an incoming SKR transfer
     * matching a known tier amount to the user's ATA.
     */
    private suspend fun parseTransactionForClaim(
        signature: String,
        ataAddress: String,
        rpcUrl: String
    ): Season1ClaimResult? {
        val params = buildJsonArray {
            add(JsonPrimitive(signature))
            add(buildJsonObject {
                put("encoding", "jsonParsed")
                put("maxSupportedTransactionVersion", 0)
            })
        }

        val result = RpcProvider.call(rpcUrl, "getTransaction", params)
        return result.fold(
            onSuccess = { response ->
                val tx = response.jsonObject
                val blockTime = tx["blockTime"]?.jsonPrimitive?.content?.toLongOrNull()

                // Check top-level instructions
                val instructions = tx["transaction"]?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("instructions")?.jsonArray

                val found = checkInstructionsForClaim(instructions, ataAddress)
                if (found != null) {
                    val tier = tierFromAmount(found)
                    if (tier != null) {
                        Log.d(TAG, "S1: Found claim in instructions: ${tier.displayName} ($found raw)")
                        return@fold Season1ClaimResult(tier, signature, blockTime, found)
                    }
                }

                // Check inner instructions (Merkle distributor claims use inner instructions)
                val innerInstructions = tx["meta"]?.jsonObject
                    ?.get("innerInstructions")?.jsonArray

                if (innerInstructions != null) {
                    for (inner in innerInstructions) {
                        val innerIxs = inner.jsonObject["instructions"]?.jsonArray
                        val innerFound = checkInstructionsForClaim(innerIxs, ataAddress)
                        if (innerFound != null) {
                            val tier = tierFromAmount(innerFound)
                            if (tier != null) {
                                Log.d(TAG, "S1: Found claim in inner instructions: ${tier.displayName}")
                                return@fold Season1ClaimResult(tier, signature, blockTime, innerFound)
                            }
                        }
                    }
                }

                // Also check post token balances for a simpler detection
                val postBalances = tx["meta"]?.jsonObject
                    ?.get("postTokenBalances")?.jsonArray
                val preBalances = tx["meta"]?.jsonObject
                    ?.get("preTokenBalances")?.jsonArray

                val balanceChange = detectBalanceChange(preBalances, postBalances, ataAddress)
                if (balanceChange != null) {
                    val tier = tierFromAmount(balanceChange)
                    if (tier != null) {
                        Log.d(TAG, "S1: Found claim via balance change: ${tier.displayName}")
                        return@fold Season1ClaimResult(tier, signature, blockTime, balanceChange)
                    }
                }

                null
            },
            onFailure = { null }
        )
    }

    /**
     * Check a list of parsed instructions for an SPL Token transfer to our ATA
     * matching a known tier amount.
     */
    private fun checkInstructionsForClaim(
        instructions: kotlinx.serialization.json.JsonArray?,
        ataAddress: String
    ): Long? {
        if (instructions == null) return null

        for (ix in instructions) {
            val ixObj = ix.jsonObject
            val program = ixObj["program"]?.jsonPrimitive?.content ?: continue

            if (program != "spl-token") continue

            val parsed = ixObj["parsed"]?.jsonObject ?: continue
            val type = parsed["type"]?.jsonPrimitive?.content ?: continue

            if (type != "transfer" && type != "transferChecked") continue

            val info = parsed["info"]?.jsonObject ?: continue
            val destination = info["destination"]?.jsonPrimitive?.content

            if (destination != ataAddress) continue

            // For "transfer": amount is a string
            // For "transferChecked": tokenAmount.amount is a string
            val amount = if (type == "transfer") {
                info["amount"]?.jsonPrimitive?.content?.toLongOrNull()
            } else {
                info["tokenAmount"]?.jsonObject
                    ?.get("amount")?.jsonPrimitive?.content?.toLongOrNull()
            }

            if (amount != null && amount in TIER_AMOUNTS) {
                return amount
            }
        }

        return null
    }

    /**
     * Detect tier amount from pre/post token balance changes on the ATA.
     */
    private fun detectBalanceChange(
        preBalances: kotlinx.serialization.json.JsonArray?,
        postBalances: kotlinx.serialization.json.JsonArray?,
        ataAddress: String
    ): Long? {
        if (preBalances == null || postBalances == null) return null

        // Find post balance for our ATA
        var postAmount: Long? = null
        for (bal in postBalances) {
            val balObj = bal.jsonObject
            val mint = balObj["mint"]?.jsonPrimitive?.content
            if (mint != AppConfig.Tokens.SKR_MINT) continue

            val owner = balObj["owner"]?.jsonPrimitive?.content
            // Match by owner since ATA addresses can differ in representation
            postAmount = balObj["uiTokenAmount"]?.jsonObject
                ?.get("amount")?.jsonPrimitive?.content?.toLongOrNull()
            if (postAmount != null) break
        }

        // Find pre balance (may not exist if account was just created)
        var preAmount: Long = 0
        for (bal in preBalances) {
            val balObj = bal.jsonObject
            val mint = balObj["mint"]?.jsonPrimitive?.content
            if (mint != AppConfig.Tokens.SKR_MINT) continue

            preAmount = balObj["uiTokenAmount"]?.jsonObject
                ?.get("amount")?.jsonPrimitive?.content?.toLongOrNull() ?: 0
            break
        }

        if (postAmount == null) return null

        val change = postAmount - preAmount
        return if (change > 0 && change in TIER_AMOUNTS) change else null
    }

    /**
     * Fallback: sum current SKR balance + staked + cooldown and find the best tier match.
     *
     * The user may have spent/sold some of their airdrop, so the current total
     * likely doesn't exactly match a tier amount. We use two strategies:
     * 1. Exact match (within 5% tolerance to account for staking rewards)
     * 2. Nearest tier that the total amount is >= to (minimum tier they could have claimed)
     *
     * This is a lower-bound estimate — the user may have been a higher tier but sold tokens.
     */
    private suspend fun tryBalanceFallback(
        walletAddress: String,
        rpcUrl: String
    ): Season1ClaimResult {
        var balance = 0L
        SkrRpcClient.getSkrBalance(walletAddress, rpcUrl).fold(
            onSuccess = { balance = it.rawAmount },
            onFailure = { }
        )

        var stakedAmount = 0L
        var cooldownAmount = 0L
        StakingRpcClient.getStakingInfo(walletAddress, rpcUrl).fold(
            onSuccess = { info ->
                stakedAmount = info.stakedAmount
                cooldownAmount = info.cooldownAmount
            },
            onFailure = { }
        )

        val total = balance + stakedAmount + cooldownAmount
        Log.d(TAG, "S1: Fallback totals — balance=$balance, staked=$stakedAmount, cooldown=$cooldownAmount, total=$total")

        // Strategy 1: Exact match with 5% tolerance (staking rewards change amounts slightly)
        val exactTier = tierFromAmountWithTolerance(total, 0.05)
        if (exactTier != null) {
            Log.d(TAG, "S1: Fallback exact match (±5%): ${exactTier.displayName}")
            return Season1ClaimResult(exactTier, null, null, total)
        }

        // Strategy 2: Find the highest tier amount that the total is >= to
        // This gives a minimum tier estimate (user may have been higher but sold tokens)
        val minimumTier = findMinimumTier(total)
        return if (minimumTier != null) {
            Log.d(TAG, "S1: Fallback minimum tier estimate: ${minimumTier.displayName} (total=$total)")
            Season1ClaimResult(minimumTier, null, null, total)
        } else {
            Log.d(TAG, "S1: No tier match (total=$total, ~${total / 1_000_000.0} SKR)")
            Season1ClaimResult(null, null, null, null)
        }
    }

    /**
     * Map a raw amount to an AirdropTier, returning null if no match.
     * Used for exact claim TX detection — amount must be in TIER_AMOUNTS set.
     */
    private fun tierFromAmount(amount: Long): AirdropTier? {
        return if (amount in TIER_AMOUNTS) {
            AirdropTier.entries.firstOrNull { it.skrAmount == amount }
        } else {
            null
        }
    }

    /**
     * Match amount to a tier with tolerance (e.g., 5% to account for staking rewards).
     * Returns the closest matching tier or null if no tier is within tolerance.
     */
    private fun tierFromAmountWithTolerance(amount: Long, tolerance: Double): AirdropTier? {
        for (tierAmount in TIER_AMOUNTS.sorted()) {
            val lower = (tierAmount * (1.0 - tolerance)).toLong()
            val upper = (tierAmount * (1.0 + tolerance)).toLong()
            if (amount in lower..upper) {
                return AirdropTier.entries.firstOrNull { it.skrAmount == tierAmount }
            }
        }
        return null
    }

    /**
     * Find the highest tier whose amount the total is >= to.
     * This is a lower-bound: if the user holds 1,362 SKR they at minimum claimed Scout (5,000 SKR)
     * but likely sold most tokens. We can't know the original tier without the claim TX.
     *
     * Only returns a result if the total is at least as much as the smallest tier (5,000 SKR).
     */
    private fun findMinimumTier(total: Long): AirdropTier? {
        val sortedTiers = TIER_AMOUNTS.sorted().reversed() // highest first
        for (tierAmount in sortedTiers) {
            if (total >= tierAmount) {
                return AirdropTier.entries.firstOrNull { it.skrAmount == tierAmount }
            }
        }
        return null
    }
}
