package com.seekerverify.app.rpc

import android.util.Log
import com.midmightbit.sgt.Base58
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
 * Fetches SKR staking info by reading the user's stake account
 * from the SKR staking program.
 *
 * The staking PDA is derived from the user's wallet address + SKR staking program.
 * We use getProgramAccounts with a memcmp filter on the wallet address bytes.
 */
object StakingRpcClient {

    private const val TAG = "SeekerVerify"

    data class StakingInfo(
        val stakedAmount: Long,         // raw lamports (6 decimals)
        val stakedDisplay: Double,      // human-readable
        val rewardsAccrued: Long,       // estimated rewards
        val rewardsDisplay: Double,
        val stakeAccountAddress: String?,
        val isStaked: Boolean
    )

    /**
     * Get staking info for a wallet.
     * Uses getProgramAccounts with a memcmp filter on the user's pubkey.
     *
     * Account layout (estimated from on-chain data):
     * - Bytes 0-7: discriminator
     * - Bytes 8-39: owner (wallet) pubkey
     * - Bytes 40-47: staked_amount (u64 LE)
     * - Bytes 48-55: rewards_accrued (u64 LE)
     */
    suspend fun getStakingInfo(
        walletAddress: String,
        rpcUrl: String
    ): Result<StakingInfo> {
        return try {
            // Query program accounts filtering by owner wallet at offset 8
            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(AppConfig.Tokens.SKR_STAKING_PROGRAM))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("filters", buildJsonArray {
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", 8)
                                put("bytes", walletAddress)
                            })
                        })
                        add(buildJsonObject {
                            put("dataSize", 120)  // expected account size
                        })
                    })
                })
            }

            val result = RpcProvider.call(rpcUrl, "getProgramAccounts", params)

            result.fold(
                onSuccess = { response ->
                    val accounts = response.jsonArray
                    if (accounts.isEmpty()) {
                        Log.d(TAG, "No staking account found")
                        return Result.success(
                            StakingInfo(0L, 0.0, 0L, 0.0, null, false)
                        )
                    }

                    val account = accounts.first().jsonObject
                    val stakeAccount = account["pubkey"]?.jsonPrimitive?.content
                    val dataArray = account["account"]?.jsonObject
                        ?.get("data")?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content

                    if (dataBase64 != null) {
                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                        val stakedAmount = if (data.size >= 48) buf.getLong(40) else 0L
                        val rewardsAccrued = if (data.size >= 56) buf.getLong(48) else 0L

                        Log.d(TAG, "Staking: ${stakedAmount / 1_000_000.0} SKR staked, " +
                            "${rewardsAccrued / 1_000_000.0} rewards")

                        Result.success(
                            StakingInfo(
                                stakedAmount = stakedAmount,
                                stakedDisplay = stakedAmount / 1_000_000.0,
                                rewardsAccrued = rewardsAccrued,
                                rewardsDisplay = rewardsAccrued / 1_000_000.0,
                                stakeAccountAddress = stakeAccount,
                                isStaked = stakedAmount > 0
                            )
                        )
                    } else {
                        Log.w(TAG, "Staking account has no parseable data")
                        Result.success(
                            StakingInfo(0L, 0.0, 0L, 0.0, stakeAccount, false)
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
     * Estimate APY based on current inflation program data.
     * For now we use a static value from on-chain research.
     */
    fun estimateApy(): Double = 20.7 // ~20.7% from Solscan observation
}
