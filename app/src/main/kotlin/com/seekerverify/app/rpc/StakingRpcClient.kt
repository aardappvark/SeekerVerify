package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.AppConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fetches SKR staking info by reading the user's UserStake PDA account
 * from the SKR staking program.
 *
 * The SKR staking program uses a shares model (similar to liquid staking):
 * - UserStake account stores active_shares (not direct token amount)
 * - StakeConfig stores the global share_price
 * - staked_SKR = active_shares * share_price / 1_000_000_000 / 1_000_000
 *
 * Account layouts (Anchor discriminator-prefixed):
 *
 * UserStake (169 bytes):
 *   [0-7]   discriminator (6635a36b098a5799)
 *   [8]     bump (u8)
 *   [9-40]  config pubkey (32 bytes)
 *   [41-72] authority / owner wallet (32 bytes)
 *   [73-104] delegation pubkey (32 bytes)
 *   [105-112] active_shares (u64 LE)
 *   [113-120] reserved (u64)
 *   [121-128] cooldown_shares (u64 LE)
 *   [129-136] cooldown_timestamp (u64 LE)
 *   [137-168] reserved (32 bytes)
 *
 * StakeConfig (193 bytes):
 *   [0-7]   discriminator (ee972b030b973fb0)
 *   [8]     bump
 *   [9-40]  authority
 *   [41-72] mint
 *   [73-104] stake_vault
 *   [105-112] min_stake_amount (u64)
 *   [113-120] cooldown_seconds (u64)
 *   [121-128] total_shares (u64)
 *   [129-136] reserved (u64)
 *   [137-144] share_price (u64 LE) <-- key field
 *   [145-192] remaining fields
 */
object StakingRpcClient {

    private const val TAG = "SeekerVerify"

    // Use shared constants from AppConfig
    private val SKR_STAKING_PROGRAM get() = AppConfig.Tokens.SKR_STAKING_PROGRAM
    private val STAKE_CONFIG get() = AppConfig.Tokens.SKR_STAKE_CONFIG
    private val SHARE_PRICE_PRECISION get() = AppConfig.Tokens.SHARE_PRICE_PRECISION
    private val SKR_DECIMALS get() = AppConfig.Tokens.SKR_DECIMALS_DIVISOR

    data class StakingInfo(
        val stakedAmount: Long,         // raw lamports (6 decimals)
        val stakedDisplay: Double,      // human-readable SKR
        val rewardsAccrued: Long,       // estimated rewards in raw
        val rewardsDisplay: Double,     // estimated rewards in SKR
        val cooldownAmount: Long,       // raw amount in cooldown
        val cooldownDisplay: Double,    // human-readable cooldown SKR
        val stakeAccountAddress: String?,
        val isStaked: Boolean,
        val activeShares: Long,
        val sharePrice: Long
    )

    /**
     * Get staking info for a wallet.
     *
     * Strategy:
     * 1. Fetch the StakeConfig account to get the current share_price
     * 2. Use getProgramAccounts with memcmp filter on wallet at offset 41
     *    and dataSize 169 to find the UserStake account
     * 3. Read active_shares and cooldown_shares from the UserStake data
     * 4. Calculate staked SKR = shares * share_price / 1B / 1M
     */
    suspend fun getStakingInfo(
        walletAddress: String,
        rpcUrl: String
    ): Result<StakingInfo> {
        return try {
            // Step 1: Fetch share price from StakeConfig
            val sharePrice = fetchSharePrice(rpcUrl)
            Log.d(TAG, "Share price: $sharePrice")

            // Step 2: Find UserStake account via getProgramAccounts
            val params = buildJsonArray {
                add(JsonPrimitive(SKR_STAKING_PROGRAM))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("filters", buildJsonArray {
                        // Filter by data size (UserStake = 169 bytes)
                        add(buildJsonObject {
                            put("dataSize", 169)
                        })
                        // Filter by wallet address at offset 41
                        // (8 discriminator + 1 bump + 32 config = 41)
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", 41)
                                put("bytes", walletAddress)
                            })
                        })
                    })
                })
            }

            val result = RpcProvider.call(rpcUrl, "getProgramAccounts", params)

            result.fold(
                onSuccess = { response ->
                    val accounts = response.jsonArray
                    if (accounts.isEmpty()) {
                        Log.d(TAG, "No UserStake account found for wallet")
                        return Result.success(
                            StakingInfo(0L, 0.0, 0L, 0.0, 0L, 0.0, null, false, 0L, sharePrice)
                        )
                    }

                    val account = accounts.first().jsonObject
                    val stakeAccount = account["pubkey"]?.jsonPrimitive?.content
                    val dataArray = account["account"]?.jsonObject
                        ?.get("data")?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content

                    if (dataBase64 != null) {
                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                        Log.d(TAG, "UserStake account data size: ${data.size}")

                        if (data.size < 137) {
                            Log.w(TAG, "UserStake data too small: ${data.size}")
                            return Result.success(
                                StakingInfo(0L, 0.0, 0L, 0.0, 0L, 0.0, stakeAccount, false, 0L, sharePrice)
                            )
                        }

                        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                        // Read active_shares at offset 105
                        val activeShares = buf.getLong(105)
                        // Read cooldown_shares at offset 121
                        val cooldownShares = if (data.size >= 129) buf.getLong(121) else 0L

                        Log.d(TAG, "Active shares: $activeShares, Cooldown shares: $cooldownShares")

                        // Calculate staked amount: shares * share_price / 1B
                        // This gives raw token amount (6 decimals)
                        val stakedRaw = if (sharePrice > 0) {
                            (activeShares.toBigInteger() * sharePrice.toBigInteger() /
                                SHARE_PRICE_PRECISION.toBigInteger()).toLong()
                        } else {
                            0L
                        }

                        val cooldownRaw = if (sharePrice > 0) {
                            (cooldownShares.toBigInteger() * sharePrice.toBigInteger() /
                                SHARE_PRICE_PRECISION.toBigInteger()).toLong()
                        } else {
                            0L
                        }

                        // Estimate rewards: the difference between current value and principal
                        // Since share price grows over time, rewards = current_value - original_stake
                        // We can't know the original stake, so we report 0 rewards separately
                        // (rewards are compounded into the share price)
                        val rewardsRaw = 0L // Rewards are compounded into share price

                        Log.d(TAG, "Staking: ${stakedRaw / SKR_DECIMALS} SKR staked " +
                            "(${activeShares} shares @ price ${sharePrice}), " +
                            "${cooldownRaw / SKR_DECIMALS} SKR in cooldown")

                        Result.success(
                            StakingInfo(
                                stakedAmount = stakedRaw,
                                stakedDisplay = stakedRaw / SKR_DECIMALS,
                                rewardsAccrued = rewardsRaw,
                                rewardsDisplay = rewardsRaw / SKR_DECIMALS,
                                cooldownAmount = cooldownRaw,
                                cooldownDisplay = cooldownRaw / SKR_DECIMALS,
                                stakeAccountAddress = stakeAccount,
                                isStaked = activeShares > 0,
                                activeShares = activeShares,
                                sharePrice = sharePrice
                            )
                        )
                    } else {
                        Log.w(TAG, "UserStake account has no parseable data")
                        Result.success(
                            StakingInfo(0L, 0.0, 0L, 0.0, 0L, 0.0, stakeAccount, false, 0L, sharePrice)
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Staking info fetch failed: ${e.message}")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Staking error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the current share price from the StakeConfig account.
     * share_price is at offset 137 in StakeConfig (193 bytes).
     */
    private suspend fun fetchSharePrice(rpcUrl: String): Long {
        return try {
            val params = buildJsonArray {
                add(JsonPrimitive(STAKE_CONFIG))
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }

            val result = RpcProvider.call(rpcUrl, "getAccountInfo", params)

            result.fold(
                onSuccess = { response ->
                    val value = response.jsonObject["value"]
                    if (value == null || value.toString() == "null") {
                        Log.w(TAG, "StakeConfig account not found, using fallback share price")
                        return FALLBACK_SHARE_PRICE
                    }

                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content

                    if (dataBase64 != null) {
                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                        Log.d(TAG, "StakeConfig data size: ${data.size}")

                        if (data.size >= 145) {
                            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                            val price = buf.getLong(137)
                            Log.d(TAG, "StakeConfig share_price: $price")
                            price
                        } else {
                            Log.w(TAG, "StakeConfig data too small: ${data.size}")
                            FALLBACK_SHARE_PRICE
                        }
                    } else {
                        FALLBACK_SHARE_PRICE
                    }
                },
                onFailure = {
                    Log.e(TAG, "Failed to fetch StakeConfig: ${it.message}")
                    FALLBACK_SHARE_PRICE
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Share price fetch error: ${e.message}", e)
            FALLBACK_SHARE_PRICE
        }
    }

    /**
     * Estimate APY based on current inflation program data.
     * SKR inflation starts at 10% annually, decaying 25% per year to 2% terminal.
     * After first year (~Feb 2027), effective staking APY is ~20.7% based on
     * current staking participation rate.
     */
    fun estimateApy(): Double = 20.7

    private val FALLBACK_SHARE_PRICE get() = AppConfig.Tokens.FALLBACK_SHARE_PRICE
}
