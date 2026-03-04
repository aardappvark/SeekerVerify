package com.seekerverify.app.rpc

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 *
 * Queries both staker (offset 12) and withdrawer (offset 44) authorities
 * to catch all stake accounts owned by the wallet, then deduplicates by pubkey.
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

            // Step 2: Get staked SOL via dual-query (staker + withdrawer authorities)
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
     * Fetch staked SOL by querying getProgramAccounts on the native Stake program.
     *
     * Runs two parallel queries:
     *   1. memcmp at offset 12 — matches accounts where wallet is the staker
     *   2. memcmp at offset 44 — matches accounts where wallet is the withdrawer
     *
     * Many wallets set the same address for both, but some (e.g. multisig, custodial)
     * differ. Deduplicates by account pubkey to avoid double-counting.
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
            coroutineScope {
                // Query 1: staker authority at offset 12
                val stakerDeferred = async {
                    queryStakeAccounts(walletAddress, rpcUrl, offset = 12, label = "staker")
                }

                // Query 2: withdrawer authority at offset 44
                val withdrawerDeferred = async {
                    queryStakeAccounts(walletAddress, rpcUrl, offset = 44, label = "withdrawer")
                }

                val stakerAccounts = stakerDeferred.await()
                val withdrawerAccounts = withdrawerDeferred.await()

                // Merge and deduplicate by account pubkey
                val merged = mutableMapOf<String, Long>()
                for ((pubkey, lamports) in stakerAccounts) {
                    merged[pubkey] = lamports
                }
                for ((pubkey, lamports) in withdrawerAccounts) {
                    merged.putIfAbsent(pubkey, lamports)
                }

                val totalLamports = merged.values.sum()
                val uniqueCount = merged.size

                Log.d(TAG, "SOL staking: ${stakerAccounts.size} staker, " +
                    "${withdrawerAccounts.size} withdrawer, $uniqueCount unique accounts")

                Pair(totalLamports, uniqueCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Staked SOL error: ${e.message}", e)
            Pair(0L, 0)
        }
    }

    /**
     * Query getProgramAccounts for stake accounts matching wallet at the given offset.
     * Returns a list of (pubkey, lamports) pairs.
     */
    private suspend fun queryStakeAccounts(
        walletAddress: String,
        rpcUrl: String,
        offset: Int,
        label: String
    ): List<Pair<String, Long>> {
        return try {
            val params = buildJsonArray {
                add(JsonPrimitive(STAKE_PROGRAM_ID))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("filters", buildJsonArray {
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", offset)
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
                    accounts.map { entry ->
                        val obj = entry.jsonObject
                        val pubkey = obj["pubkey"]?.jsonPrimitive?.content ?: ""
                        val lamports = obj["account"]?.jsonObject
                            ?.get("lamports")?.jsonPrimitive?.long ?: 0L
                        Pair(pubkey, lamports)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Stake query ($label, offset $offset) failed: ${e.message}")
                    emptyList()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stake query ($label) error: ${e.message}", e)
            emptyList()
        }
    }
}
