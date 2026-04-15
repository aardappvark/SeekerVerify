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
 *
 * Uses `jsonParsed` encoding so we can read `delegation.stake` (the actually-
 * delegated lamports) rather than `account.lamports` (which includes rent
 * reserves and undelegated funds and would over-report). Each account is then
 * classified by its activation/deactivation epochs against the current epoch:
 *   - active        : fully delegated and earning
 *   - warming       : delegated this epoch, will earn next epoch
 *   - deactivating  : cooling down this epoch, still staked
 *   - inactive      : fully cooled down / never delegated — EXCLUDED from staked total
 *
 * The reported staked total is the sum of `delegation.stake` for the first
 * three categories, matching the Seeker native wallet's "Staked SOL" display.
 */
object SolRpcClient {

    private const val TAG = "SeekerVerify"
    private const val LAMPORTS_PER_SOL = 1_000_000_000.0

    // Solana native Stake program ID
    private const val STAKE_PROGRAM_ID = "Stake11111111111111111111111111111111111111"

    /**
     * A single stake account as returned by queryStakeAccounts.
     * delegation.stake is 0 and epochs are 0 for uninitialized accounts.
     */
    private data class StakeEntry(
        val pubkey: String,
        val delegationStake: Long,  // lamports actually delegated (0 if uninitialized)
        val activationEpoch: Long,
        val deactivationEpoch: Long,
        val isUninitialized: Boolean
    )

    private enum class StakeState { ACTIVE, WARMING, DEACTIVATING, INACTIVE }

    data class SolBalanceInfo(
        val solBalance: Double,         // SOL balance (human readable)
        val solLamports: Long,          // Raw lamports
        val stakedSol: Double,          // Active + warming + deactivating staked SOL
        val stakedLamports: Long,       // Raw active+warming+deactivating delegation.stake sum
        val stakeAccounts: Int          // Number of stake accounts contributing to stakedSol
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

            // Step 2: Get the current epoch — needed to classify stake accounts as
            // active / warming / deactivating / inactive. We query in parallel with
            // the stake account lookups to minimize total wall-clock time.
            val (stakedLamports, stakeCount) = fetchStakedSol(walletAddress, rpcUrl)

            Log.d(TAG, "SOL: ${solLamports / LAMPORTS_PER_SOL} liquid, " +
                "${stakedLamports / LAMPORTS_PER_SOL} staked ($stakeCount accounts contributing)")

            Result.success(
                SolBalanceInfo(
                    solBalance = solLamports / LAMPORTS_PER_SOL,
                    solLamports = solLamports,
                    stakedSol = stakedLamports / LAMPORTS_PER_SOL,
                    stakedLamports = stakedLamports,
                    stakeAccounts = stakeCount
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
     * Runs three queries in parallel:
     *   1. getEpochInfo — needed to classify stake accounts by their activation/
     *      deactivation epochs
     *   2. memcmp at offset 12 — matches accounts where wallet is the staker
     *   3. memcmp at offset 44 — matches accounts where wallet is the withdrawer
     *
     * Many wallets set the same address for both authorities, but some
     * (multisig, custodial) differ. Deduplicates by account pubkey.
     *
     * Returns (lamports, accountCount) where:
     *   - lamports   = sum of delegation.stake for active + warming + deactivating
     *                  (matches what the Seeker native wallet displays)
     *   - accountCount = number of unique accounts contributing to that sum
     *                    (inactive / uninitialized accounts are NOT counted)
     */
    private suspend fun fetchStakedSol(
        walletAddress: String,
        rpcUrl: String
    ): Pair<Long, Int> {
        return try {
            coroutineScope {
                val epochDeferred = async { fetchCurrentEpoch(rpcUrl) }
                val stakerDeferred = async {
                    queryStakeAccounts(walletAddress, rpcUrl, offset = 12, label = "staker")
                }
                val withdrawerDeferred = async {
                    queryStakeAccounts(walletAddress, rpcUrl, offset = 44, label = "withdrawer")
                }

                val currentEpoch = epochDeferred.await()
                val stakerAccounts = stakerDeferred.await()
                val withdrawerAccounts = withdrawerDeferred.await()

                // Merge and deduplicate by account pubkey. A single stake account
                // frequently appears in both lists (wallet is both staker and
                // withdrawer), so we keep one entry per pubkey.
                val merged = mutableMapOf<String, StakeEntry>()
                for (entry in stakerAccounts) merged[entry.pubkey] = entry
                for (entry in withdrawerAccounts) merged.putIfAbsent(entry.pubkey, entry)

                // Classify each account by state and sum only the active-like ones.
                var activeLamports = 0L
                var contributingCount = 0
                var activeAccts = 0
                var warmingAccts = 0
                var deactivatingAccts = 0
                var inactiveAccts = 0
                var uninitializedAccts = 0

                for (entry in merged.values) {
                    if (entry.isUninitialized) {
                        uninitializedAccts++
                        continue
                    }
                    val state = classifyStakeState(
                        activationEpoch = entry.activationEpoch,
                        deactivationEpoch = entry.deactivationEpoch,
                        currentEpoch = currentEpoch
                    )
                    when (state) {
                        StakeState.ACTIVE -> {
                            activeLamports += entry.delegationStake
                            activeAccts++
                            contributingCount++
                        }
                        StakeState.WARMING -> {
                            activeLamports += entry.delegationStake
                            warmingAccts++
                            contributingCount++
                        }
                        StakeState.DEACTIVATING -> {
                            activeLamports += entry.delegationStake
                            deactivatingAccts++
                            contributingCount++
                        }
                        StakeState.INACTIVE -> {
                            inactiveAccts++
                        }
                    }
                }

                Log.d(TAG, "SOL staking @ epoch $currentEpoch: " +
                    "$activeAccts active, $warmingAccts warming, " +
                    "$deactivatingAccts deactivating, $inactiveAccts inactive, " +
                    "$uninitializedAccts uninit → " +
                    "${activeLamports / LAMPORTS_PER_SOL} SOL across $contributingCount accts")

                Pair(activeLamports, contributingCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Staked SOL error: ${e.message}", e)
            Pair(0L, 0)
        }
    }

    /**
     * Fetch the current Solana epoch from the RPC. Returns 0 on failure, which
     * causes classifyStakeState to default all delegated-but-not-sentinel
     * accounts to "active" — a reasonable fallback so we don't silently hide
     * staked SOL when getEpochInfo temporarily fails.
     */
    private suspend fun fetchCurrentEpoch(rpcUrl: String): Long {
        return try {
            val result = RpcProvider.call(rpcUrl, "getEpochInfo", buildJsonArray { })
            result.fold(
                onSuccess = { response ->
                    response.jsonObject["epoch"]?.jsonPrimitive?.long ?: 0L
                },
                onFailure = { e ->
                    Log.w(TAG, "getEpochInfo failed, defaulting to epoch 0: ${e.message}")
                    0L
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "getEpochInfo error, defaulting to epoch 0: ${e.message}")
            0L
        }
    }

    /**
     * Classify a stake account by its activation/deactivation epochs relative
     * to the current epoch.
     *
     * Solana native stake semantics:
     *   - deactivationEpoch == U64_MAX  → never deactivated (currently active)
     *   - currentEpoch < deactivationEpoch → active
     *   - currentEpoch == deactivationEpoch → deactivating this epoch (still counted)
     *   - currentEpoch > deactivationEpoch → fully inactive (withdrawable)
     *
     * Additionally, if activationEpoch == currentEpoch and never deactivated,
     * the account is "warming up" — still credited as staked but hasn't yet
     * started earning.
     *
     * We special-case currentEpoch == 0 (getEpochInfo failed) by treating all
     * non-sentinel delegations as active — a safe fallback that preserves the
     * staked display rather than silently zeroing it.
     */
    private fun classifyStakeState(
        activationEpoch: Long,
        deactivationEpoch: Long,
        currentEpoch: Long
    ): StakeState {
        // u64 max as a signed Long is -1 (Long.parseLong can't hold u64::MAX
        // but we read through jsonPrimitive.long which will throw; see parser
        // below which returns U64_SENTINEL for unparseable huge values).
        val isNeverDeactivated = deactivationEpoch == U64_SENTINEL
        if (isNeverDeactivated) {
            // Warming if activated this epoch AND epoch is known; otherwise active.
            return if (currentEpoch > 0 && activationEpoch == currentEpoch) {
                StakeState.WARMING
            } else {
                StakeState.ACTIVE
            }
        }
        if (currentEpoch == 0L) {
            // Epoch unknown — err on the side of showing the stake rather than
            // hiding it. Anything that isn't fully-cooled-at-epoch-0 counts.
            return StakeState.ACTIVE
        }
        return when {
            currentEpoch < deactivationEpoch -> StakeState.ACTIVE
            currentEpoch == deactivationEpoch -> StakeState.DEACTIVATING
            else -> StakeState.INACTIVE
        }
    }

    // Sentinel we use when we can't parse a full u64 deactivation epoch
    // (which the RPC returns as the string "18446744073709551615" for
    // "never deactivated"). Any value >= U64_SENTINEL is treated as never.
    private const val U64_SENTINEL = Long.MAX_VALUE

    /**
     * Query getProgramAccounts for stake accounts matching wallet at the given offset.
     *
     * Uses encoding: "jsonParsed" so the response contains parsed stake state
     * including delegation.stake and activation/deactivation epochs. This is
     * the only way to correctly compute staked SOL — reading account.lamports
     * over-reports by rent reserves and includes inactive/deactivated funds.
     *
     * The parser is lenient: any account whose data can't be parsed as a
     * delegated stake (e.g. uninitialized state, malformed data) is included
     * as a StakeEntry with delegationStake=0 and isUninitialized=true. These
     * get filtered out downstream.
     */
    private suspend fun queryStakeAccounts(
        walletAddress: String,
        rpcUrl: String,
        offset: Int,
        label: String
    ): List<StakeEntry> {
        return try {
            val params = buildJsonArray {
                add(JsonPrimitive(STAKE_PROGRAM_ID))
                add(buildJsonObject {
                    put("encoding", "jsonParsed")
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
                    accounts.mapNotNull { entry ->
                        parseStakeEntry(entry)
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

    /**
     * Parse a single getProgramAccounts entry into a StakeEntry. Tolerates
     * uninitialized accounts, missing delegation, and unparseable epoch
     * strings (including the u64::MAX sentinel).
     *
     * Returns null only if the entry structure is missing essentials (no
     * pubkey). An uninitialized account returns a StakeEntry with
     * isUninitialized=true so the caller can count it for diagnostics.
     */
    private fun parseStakeEntry(entry: kotlinx.serialization.json.JsonElement): StakeEntry? {
        return try {
            val obj = entry.jsonObject
            val pubkey = obj["pubkey"]?.jsonPrimitive?.content ?: return null
            val account = obj["account"]?.jsonObject ?: return null

            // Navigate: account.data.parsed.info.stake.delegation
            val data = account["data"]?.jsonObject ?: return StakeEntry(
                pubkey = pubkey, delegationStake = 0L,
                activationEpoch = 0L, deactivationEpoch = 0L,
                isUninitialized = true
            )
            val parsed = data["parsed"]?.jsonObject ?: return StakeEntry(
                pubkey = pubkey, delegationStake = 0L,
                activationEpoch = 0L, deactivationEpoch = 0L,
                isUninitialized = true
            )

            // type = "delegated" (full stake) / "initialized" (no delegation yet) / "uninitialized"
            val type = parsed["type"]?.jsonPrimitive?.content

            if (type != "delegated") {
                return StakeEntry(
                    pubkey = pubkey, delegationStake = 0L,
                    activationEpoch = 0L, deactivationEpoch = 0L,
                    isUninitialized = true
                )
            }

            val info = parsed["info"]?.jsonObject ?: return StakeEntry(
                pubkey = pubkey, delegationStake = 0L,
                activationEpoch = 0L, deactivationEpoch = 0L,
                isUninitialized = true
            )
            val stake = info["stake"]?.jsonObject ?: return StakeEntry(
                pubkey = pubkey, delegationStake = 0L,
                activationEpoch = 0L, deactivationEpoch = 0L,
                isUninitialized = true
            )
            val delegation = stake["delegation"]?.jsonObject ?: return StakeEntry(
                pubkey = pubkey, delegationStake = 0L,
                activationEpoch = 0L, deactivationEpoch = 0L,
                isUninitialized = true
            )

            // RPC returns these as JSON strings (since they can exceed signed-long
            // range for u64::MAX). Parse defensively.
            val delegationStake = delegation["stake"]?.jsonPrimitive?.content
                ?.toLongOrNull() ?: 0L
            val activationEpoch = delegation["activationEpoch"]?.jsonPrimitive?.content
                ?.toLongOrNull() ?: 0L

            // deactivationEpoch == u64::MAX ("18446744073709551615") means
            // "never deactivated". toLongOrNull() returns null for that (out of
            // signed-long range), so we map null → sentinel.
            val rawDeact = delegation["deactivationEpoch"]?.jsonPrimitive?.content
            val deactivationEpoch = rawDeact?.toLongOrNull() ?: U64_SENTINEL

            StakeEntry(
                pubkey = pubkey,
                delegationStake = delegationStake,
                activationEpoch = activationEpoch,
                deactivationEpoch = deactivationEpoch,
                isUninitialized = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Stake entry parse error: ${e.message}")
            null
        }
    }
}
