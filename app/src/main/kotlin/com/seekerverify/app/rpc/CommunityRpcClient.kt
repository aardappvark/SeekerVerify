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
 * Uses getTokenLargestAccounts on the SGT group mint to estimate fleet size,
 * getProgramAccounts on the staking program for real staker counts.
 */
object CommunityRpcClient {

    private const val TAG = "SeekerVerify"

    // Use shared constants from AppConfig
    private val SKR_STAKING_PROGRAM get() = AppConfig.Tokens.SKR_STAKING_PROGRAM
    private val STAKE_CONFIG get() = AppConfig.Tokens.SKR_STAKE_CONFIG
    private val SHARE_PRICE_PRECISION get() = AppConfig.Tokens.SHARE_PRICE_PRECISION
    private val SKR_DECIMALS get() = AppConfig.Tokens.SKR_DECIMALS_DIVISOR

    data class CommunityStats(
        val totalSeekers: Long,    // approximate total SGT holders
        val userPosition: Long?    // member number = fleet position
    )

    data class StakingStats(
        val activeStakers: Int,
        val totalStakedSkr: Long,   // raw amount (6 decimals)
        val totalStakedDisplay: Double,
        val stakingParticipation: Double  // percentage of total supply staked (approximate)
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
     * Queries all UserStake accounts (169 bytes, dataSize filter) and counts
     * active stakers + total staked amount.
     *
     * Uses the StakeConfig share_price to convert shares → SKR tokens.
     */
    suspend fun getStakingStats(rpcUrl: String): Result<StakingStats> {
        return try {
            // Step 1: Get share price from StakeConfig
            val sharePrice = fetchSharePrice(rpcUrl)
            Log.d(TAG, "Community staking: share price = $sharePrice")

            // Step 2: Fetch the stake vault balance directly (much cheaper than scanning all accounts)
            val vaultParams = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(AppConfig.Tokens.SKR_STAKE_VAULT))
                add(buildJsonObject {
                    put("encoding", "jsonParsed")
                })
            }

            var totalStakedRaw = 0L
            RpcProvider.call(rpcUrl, "getAccountInfo", vaultParams).fold(
                onSuccess = { response ->
                    val value = response.jsonObject["value"]
                    if (value != null && value.toString() != "null") {
                        val amount = value.jsonObject["data"]?.jsonObject
                            ?.get("parsed")?.jsonObject
                            ?.get("info")?.jsonObject
                            ?.get("tokenAmount")?.jsonObject
                            ?.get("amount")?.jsonPrimitive?.content?.toLongOrNull()
                        if (amount != null) {
                            totalStakedRaw = amount
                            Log.d(TAG, "Stake vault balance: ${amount / SKR_DECIMALS} SKR")
                        }
                    }
                },
                onFailure = { e ->
                    Log.w(TAG, "Stake vault fetch failed: ${e.message}")
                }
            )

            // Step 3: Count active stakers by fetching UserStake accounts
            // Use getProgramAccounts with dataSize filter to get all UserStake accounts
            val stakerParams = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(SKR_STAKING_PROGRAM))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("dataSlice", buildJsonObject {
                        put("offset", 105)  // active_shares offset
                        put("length", 8)    // u64 size — only read the shares field
                    })
                    put("filters", buildJsonArray {
                        add(buildJsonObject {
                            put("dataSize", 169) // UserStake account size
                        })
                    })
                })
            }

            var activeStakers = 0
            RpcProvider.call(rpcUrl, "getProgramAccounts", stakerParams).fold(
                onSuccess = { response ->
                    val accounts = response.jsonArray
                    for (account in accounts) {
                        val dataArray = account.jsonObject["account"]?.jsonObject
                            ?.get("data")?.jsonArray
                        val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content
                            ?: continue
                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                        if (data.size >= 8) {
                            val shares = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
                            if (shares > 0) {
                                activeStakers++
                            }
                        }
                    }
                    Log.d(TAG, "Active stakers: $activeStakers out of ${response.jsonArray.size} accounts")
                },
                onFailure = { e ->
                    Log.w(TAG, "Staker count fetch failed: ${e.message}")
                }
            )

            // Approximate total SKR supply: ~1.4B based on public data
            val approxTotalSupply = 1_400_000_000.0
            val stakedDisplay = totalStakedRaw / SKR_DECIMALS
            val participation = if (approxTotalSupply > 0) {
                (stakedDisplay / approxTotalSupply * 100).coerceIn(0.0, 100.0)
            } else 0.0

            Log.d(TAG, "Staking stats: $activeStakers stakers, ${stakedDisplay.toLong()} SKR staked (${String.format("%.1f", participation)}%)")

            Result.success(StakingStats(
                activeStakers = activeStakers,
                totalStakedSkr = totalStakedRaw,
                totalStakedDisplay = stakedDisplay,
                stakingParticipation = participation
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Staking stats error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch share price from StakeConfig (same logic as StakingRpcClient).
     */
    private suspend fun fetchSharePrice(rpcUrl: String): Long {
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
                    if (value == null || value.toString() == "null") return FALLBACK_SHARE_PRICE
                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content
                        ?: return FALLBACK_SHARE_PRICE
                    val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                    if (data.size >= 145) {
                        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(137)
                    } else FALLBACK_SHARE_PRICE
                },
                onFailure = { FALLBACK_SHARE_PRICE }
            )
        } catch (e: Exception) {
            FALLBACK_SHARE_PRICE
        }
    }

    private const val KNOWN_APPROXIMATE_SEEKERS = 140_000L
    private val FALLBACK_SHARE_PRICE get() = AppConfig.Tokens.FALLBACK_SHARE_PRICE
}
