package com.seekerverify.app.rpc

import android.util.Log
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Fetches SOL balance and native stake info for a wallet.
 */
object SolRpcClient {

    private const val TAG = "SeekerVerify"
    private const val LAMPORTS_PER_SOL = 1_000_000_000.0

    // Solana native Stake program ID
    private const val STAKE_PROGRAM_ID = "Stake11111111111111111111111111111111111111"

    data class SolBalanceInfo(
        val solBalance: Double,         // SOL balance (human readable)
        val solLamports: Long,          // Raw lamports
        val stakedSol: Double,          // Total staked SOL
        val stakedLamports: Long,       // Raw staked lamports
        val stakeAccounts: Int          // Number of stake accounts
    )

    /**
     * Get SOL balance and native staking info for a wallet.
     */
    suspend fun getSolBalance(
        walletAddress: String,
        rpcUrl: String
    ): Result<SolBalanceInfo> {
        return try {
            // Step 1: Get SOL balance via getBalance
            val balanceParams = buildJsonArray {
                add(JsonPrimitive(walletAddress))
            }

            val balanceResult = RpcProvider.call(rpcUrl, "getBalance", balanceParams)
            val solLamports = balanceResult.fold(
                onSuccess = { response ->
                    response.jsonObject["value"]?.jsonPrimitive?.long ?: 0L
                },
                onFailure = { e ->
                    Log.e(TAG, "SOL balance fetch failed: ${e.message}")
                    0L
                }
            )

            // Step 2: Get staked SOL via getStakeAccountsByOwner (if available)
            // Fall back to getProgramAccounts with stake program
            val stakedLamports = fetchStakedSol(walletAddress, rpcUrl)

            Log.d(TAG, "SOL: ${solLamports / LAMPORTS_PER_SOL} liquid, " +
                "${stakedLamports.first / LAMPORTS_PER_SOL} staked (${stakedLamports.second} accounts)")

            Result.success(
                SolBalanceInfo(
                    solBalance = solLamports / LAMPORTS_PER_SOL,
                    solLamports = solLamports,
                    stakedSol = stakedLamports.first / LAMPORTS_PER_SOL,
                    stakedLamports = stakedLamports.first,
                    stakeAccounts = stakedLamports.second
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "SOL balance error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch staked SOL by querying getProgramAccounts on the native Stake program
     * filtered by the authorized staker (offset 12 for staker pubkey in Stake account).
     *
     * Stake account layout:
     * [0-3]   state (u32 LE)
     * [4-11]  meta.rent_exempt_reserve (u64 LE)
     * [12-43] meta.authorized.staker (Pubkey, 32 bytes)
     * [44-75] meta.authorized.withdrawer (Pubkey, 32 bytes)
     * ...
     *
     * The total staked amount is the account's lamports (balance).
     */
    private suspend fun fetchStakedSol(
        walletAddress: String,
        rpcUrl: String
    ): Pair<Long, Int> {
        return try {
            val params = buildJsonArray {
                add(JsonPrimitive(STAKE_PROGRAM_ID))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("filters", buildJsonArray {
                        // Filter by staker authority at offset 12
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", 12)
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
                    var totalLamports = 0L

                    for (accountEntry in accounts) {
                        val accountObj = accountEntry.jsonObject
                        val lamports = accountObj["account"]?.jsonObject
                            ?.get("lamports")?.jsonPrimitive?.long ?: 0L
                        totalLamports += lamports
                    }

                    Pair(totalLamports, accounts.size)
                },
                onFailure = { e ->
                    Log.e(TAG, "Staked SOL fetch failed: ${e.message}")
                    Pair(0L, 0)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Staked SOL error: ${e.message}", e)
            Pair(0L, 0)
        }
    }
}
