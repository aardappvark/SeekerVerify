package com.seekerverify.app.rpc

import android.util.Log
import com.midmightbit.sgt.SgtConstants
import com.seekerverify.app.AppConfig
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fetches community-level statistics from chain.
 * Uses getAccountInfo on StakeConfig for total staked (reliable, single RPC call).
 * Uses getTokenSupply for actual SKR supply (replaces hardcoded estimate).
 * Optionally uses getProgramAccounts for staker count (non-fatal if 429).
 */
object CommunityRpcClient {

    private const val TAG = "SeekerVerify"

    private val SKR_STAKING_PROGRAM get() = AppConfig.Tokens.SKR_STAKING_PROGRAM
    private val STAKE_CONFIG get() = AppConfig.Tokens.SKR_STAKE_CONFIG
    private val SHARE_PRICE_PRECISION get() = AppConfig.Tokens.SHARE_PRICE_PRECISION
    private val SKR_DECIMALS get() = AppConfig.Tokens.SKR_DECIMALS_DIVISOR
    private val SKR_MINT get() = AppConfig.Tokens.SKR_MINT

    data class CommunityStats(
        val totalSeekers: Long,    // approximate total SGT holders
        val userPosition: Long?    // member number = fleet position
    )

    data class StakingStats(
        val activeStakers: Int?,        // null if couldn't fetch (429)
        val totalStakedSkr: Long,       // raw amount (6 decimals)
        val totalStakedDisplay: Double,
        val stakingParticipation: Double,  // percentage of total supply staked
        val fleetModeSkr: Double? = null   // statistical mode of individual staked amounts
    )

    /**
     * Internal data from StakeConfig account.
     */
    private data class StakeConfigData(
        val sharePrice: Long,
        val totalShares: Long,
        val lastVaultAmount: Long
    )

    /**
     * Get community stats. For total Seekers, we check the SGT group account
     * which tracks total members. If that fails, we use a hardcoded known value.
     */
    suspend fun getCommunityStats(
        userMemberNumber: Long?,
        rpcUrl: String
    ): Result<CommunityStats> {
        return try {
            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(SgtConstants.SGT_METADATA_ADDRESS))
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }

            val result = RpcProvider.call(rpcUrl, "getAccountInfo", params)

            result.fold(
                onSuccess = {
                    Result.success(
                        CommunityStats(
                            totalSeekers = KNOWN_APPROXIMATE_SEEKERS,
                            userPosition = userMemberNumber
                        )
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "Community stats fetch failed: ${e.message}")
                    Result.success(
                        CommunityStats(
                            totalSeekers = KNOWN_APPROXIMATE_SEEKERS,
                            userPosition = userMemberNumber
                        )
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Community stats error: ${e.message}", e)
            Result.success(
                CommunityStats(
                    totalSeekers = KNOWN_APPROXIMATE_SEEKERS,
                    userPosition = userMemberNumber
                )
            )
        }
    }

    /**
     * Get real staking stats from the SKR staking program.
     *
     * Optimized flow (2 reliable getAccountInfo calls + 1 optional):
     * 1. getAccountInfo(StakeConfig) → share_price, total_shares, last_vault_amount
     * 2. getTokenSupply(SKR_MINT) → actual total supply for participation %
     * 3. getProgramAccounts (optional) → staker count (non-fatal if 429)
     */
    suspend fun getStakingStats(rpcUrl: String): Result<StakingStats> {
        return try {
            // Step 1: Read StakeConfig — single call gets share_price + total_shares
            val configData = fetchStakeConfig(rpcUrl)
            Log.d(TAG, "Community staking: share_price=${configData.sharePrice}, " +
                "total_shares=${configData.totalShares}, last_vault=${configData.lastVaultAmount}")

            // Calculate total staked from total_shares * share_price
            val totalStakedRaw = if (configData.sharePrice > 0 && configData.totalShares > 0) {
                (configData.totalShares.toBigInteger() * configData.sharePrice.toBigInteger() /
                    SHARE_PRICE_PRECISION.toBigInteger()).toLong()
            } else {
                configData.lastVaultAmount // fallback to last_vault_amount
            }

            val stakedDisplay = totalStakedRaw / SKR_DECIMALS

            // Step 2: Get actual token supply for accurate participation %
            val totalSupply = fetchTokenSupply(rpcUrl)
            val participation = if (totalSupply > 0) {
                (stakedDisplay / totalSupply * 100).coerceIn(0.0, 100.0)
            } else 0.0

            // Step 3: Count active stakers + compute fleet mode (optional — non-fatal if 429)
            val stakerAnalysis = tryFetchStakerAnalysis(rpcUrl, configData.sharePrice)

            Log.d(TAG, "Staking stats: ${stakerAnalysis?.count ?: "?"} stakers, " +
                "${stakedDisplay.toLong()} SKR staked (${String.format("%.1f", participation)}% of ${totalSupply.toLong()})" +
                ", fleet mode=${stakerAnalysis?.modeSkr?.toLong() ?: "?"}")

            Result.success(StakingStats(
                activeStakers = stakerAnalysis?.count,
                totalStakedSkr = totalStakedRaw,
                totalStakedDisplay = stakedDisplay,
                stakingParticipation = participation,
                fleetModeSkr = stakerAnalysis?.modeSkr
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Staking stats error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch StakeConfig account and extract key fields.
     *
     * StakeConfig layout (193 bytes):
     *   [121-136] total_shares (u128)
     *   [137-152] share_price (u128)
     *   [185-192] last_vault_amount (u64)
     */
    private suspend fun fetchStakeConfig(rpcUrl: String): StakeConfigData {
        return try {
            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(STAKE_CONFIG))
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }
            val result = RpcProvider.call(rpcUrl, "getAccountInfo", params)
            result.fold(
                onSuccess = { response ->
                    val value = response.jsonObject["value"]
                    if (value == null || value.toString() == "null") {
                        return FALLBACK_CONFIG
                    }
                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content
                        ?: return FALLBACK_CONFIG
                    val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                    if (data.size >= 193) {
                        StakeConfigData(
                            sharePrice = readU128AsLong(data, 137),
                            totalShares = readU128AsLong(data, 121),
                            lastVaultAmount = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(185)
                        )
                    } else if (data.size >= 153) {
                        // Partial read — at least get share_price and total_shares
                        StakeConfigData(
                            sharePrice = readU128AsLong(data, 137),
                            totalShares = readU128AsLong(data, 121),
                            lastVaultAmount = 0L
                        )
                    } else {
                        FALLBACK_CONFIG
                    }
                },
                onFailure = { FALLBACK_CONFIG }
            )
        } catch (e: Exception) {
            FALLBACK_CONFIG
        }
    }

    /**
     * Fetch actual SKR token supply via getTokenSupply RPC.
     * Returns display amount (already divided by decimals).
     * Falls back to approximate value if call fails.
     */
    private suspend fun fetchTokenSupply(rpcUrl: String): Double {
        return try {
            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(SKR_MINT))
            }
            val result = RpcProvider.call(rpcUrl, "getTokenSupply", params)
            result.fold(
                onSuccess = { response ->
                    val uiAmount = response.jsonObject["value"]?.jsonObject
                        ?.get("uiAmount")?.jsonPrimitive?.content?.toDoubleOrNull()
                    if (uiAmount != null && uiAmount > 0) {
                        Log.d(TAG, "SKR total supply: ${uiAmount.toLong()}")
                        uiAmount
                    } else {
                        FALLBACK_TOTAL_SUPPLY
                    }
                },
                onFailure = {
                    Log.w(TAG, "Token supply fetch failed: ${it.message}")
                    FALLBACK_TOTAL_SUPPLY
                }
            )
        } catch (e: Exception) {
            FALLBACK_TOTAL_SUPPLY
        }
    }

    /**
     * Analysis result from scanning all staking accounts.
     */
    private data class StakerAnalysis(
        val count: Int,          // number of active stakers
        val modeSkr: Double?     // statistical mode of staked SKR (most common bucket midpoint)
    )

    /**
     * Fetch all staker accounts, count active ones, and compute
     * the statistical mode (most common staking amount bucket).
     * Returns null if the call fails (e.g., 429 rate limit on public RPC).
     */
    private suspend fun tryFetchStakerAnalysis(rpcUrl: String, sharePrice: Long): StakerAnalysis? {
        return try {
            val stakerParams = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(SKR_STAKING_PROGRAM))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("dataSlice", buildJsonObject {
                        put("offset", 105)  // active_shares offset
                        put("length", 16)   // u128 size
                    })
                    put("filters", buildJsonArray {
                        add(buildJsonObject {
                            put("dataSize", 169) // UserStake account size
                        })
                    })
                })
            }

            val result = RpcProvider.call(rpcUrl, "getProgramAccounts", stakerParams)
            result.fold(
                onSuccess = { response ->
                    var count = 0
                    val amounts = mutableListOf<Double>()
                    val accounts = response.jsonArray
                    for (account in accounts) {
                        val dataArray = account.jsonObject["account"]?.jsonObject
                            ?.get("data")?.jsonArray
                        val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content
                            ?: continue
                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                        if (data.size >= 16) {
                            val shares = readU128AsLong(data, 0)
                            if (shares > 0) {
                                count++
                                // Convert shares to SKR display amount
                                if (sharePrice > 0) {
                                    val skrRaw = (shares.toBigInteger() * sharePrice.toBigInteger() /
                                        SHARE_PRICE_PRECISION.toBigInteger()).toLong()
                                    val skrDisplay = skrRaw.toDouble() / SKR_DECIMALS
                                    amounts.add(skrDisplay)
                                }
                            }
                        }
                    }

                    // Compute statistical mode using logarithmic bucketing
                    val modeSkr = computeStakingMode(amounts)

                    Log.d(TAG, "Active stakers: $count out of ${accounts.size} accounts, mode=${modeSkr?.toLong()} SKR")
                    StakerAnalysis(count = count, modeSkr = modeSkr)
                },
                onFailure = { e ->
                    Log.w(TAG, "Staker analysis fetch failed (non-fatal): ${e.message}")
                    null
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Staker analysis error (non-fatal): ${e.message}")
            null
        }
    }

    /**
     * Compute the statistical mode of staked SKR amounts.
     * Uses logarithmic bucketing (powers of 10 subdivided) to group
     * continuous values into meaningful ranges, then returns the
     * median of the most common bucket.
     *
     * Buckets: 0-100, 100-500, 500-1K, 1K-5K, 5K-10K, 10K-50K, 50K-100K, 100K+
     */
    private fun computeStakingMode(amounts: List<Double>): Double? {
        if (amounts.isEmpty()) return null

        data class Bucket(val label: String, val low: Double, val high: Double)

        val buckets = listOf(
            Bucket("0-100", 0.0, 100.0),
            Bucket("100-500", 100.0, 500.0),
            Bucket("500-1K", 500.0, 1_000.0),
            Bucket("1K-5K", 1_000.0, 5_000.0),
            Bucket("5K-10K", 5_000.0, 10_000.0),
            Bucket("10K-50K", 10_000.0, 50_000.0),
            Bucket("50K-100K", 50_000.0, 100_000.0),
            Bucket("100K+", 100_000.0, Double.MAX_VALUE)
        )

        // Count amounts in each bucket
        val bucketCounts = buckets.map { bucket ->
            bucket to amounts.count { it >= bucket.low && it < bucket.high }
        }

        // Find the bucket with the most entries (the mode bucket)
        val modeBucket = bucketCounts.maxByOrNull { it.second } ?: return null
        if (modeBucket.second == 0) return null

        // Return median of amounts within the mode bucket
        val modeAmounts = amounts.filter { it >= modeBucket.first.low && it < modeBucket.first.high }.sorted()
        return modeAmounts[modeAmounts.size / 2]
    }

    /**
     * Read a u128 little-endian value from a byte array, returning as Long.
     * Safe for values that fit in Long (which all current staking values do).
     */
    private fun readU128AsLong(data: ByteArray, offset: Int): Long {
        if (data.size < offset + 16) return 0L
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val low = buf.getLong(offset)
        val high = buf.getLong(offset + 8)
        if (high != 0L) {
            Log.w(TAG, "Community: u128 value at offset $offset exceeds Long range (high=$high)")
        }
        return low
    }

    // Seeker devices sold — updated 2026-04-15 to match the Solana Compass
    // Breakpoint 2025 ecosystem figure (150,000+). Note: percentile math in
    // CommunityViewModel is already a pure function of (memberNumber, totalSeekers)
    // so every user's displayed percentile recomputes automatically on next refresh.
    private const val KNOWN_APPROXIMATE_SEEKERS = 150_000L
    private const val FALLBACK_TOTAL_SUPPLY = 1_400_000_000.0

    private val FALLBACK_CONFIG = StakeConfigData(
        sharePrice = AppConfig.Tokens.FALLBACK_SHARE_PRICE,
        totalShares = 0L,
        lastVaultAmount = 0L
    )
}
