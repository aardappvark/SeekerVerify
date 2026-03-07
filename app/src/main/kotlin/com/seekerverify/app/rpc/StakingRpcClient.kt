package com.seekerverify.app.rpc

import android.util.Log
import com.midmightbit.sgt.Base58
import com.seekerverify.app.AppConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Fetches SKR staking info by reading the user's UserStake PDA account
 * from the SKR staking program (Anchor).
 *
 * PDA seeds: ["user_stake", stake_config, user_wallet, guardian_pool]
 * Program: SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ
 *
 * The SKR staking program uses a shares model (similar to liquid staking):
 * - UserStake account stores shares (u128)
 * - StakeConfig stores the global share_price (u128)
 * - staked_SKR = shares * share_price / 1e9 / 1e6
 *
 * Account layouts (Anchor discriminator-prefixed):
 *
 * UserStake (169 bytes):
 *   [0-7]     discriminator
 *   [8]       bump (u8)
 *   [9-40]    stake_config (pubkey, 32 bytes)
 *   [41-72]   user (pubkey, 32 bytes)
 *   [73-104]  guardian_pool (pubkey, 32 bytes)
 *   [105-120] shares (u128 LE, 16 bytes)
 *   [121-136] cost_basis (u128 LE, 16 bytes)
 *   [137-152] cumulative_commission_before_staking (u128 LE, 16 bytes)
 *   [153-160] unstaking_amount (u64 LE, 8 bytes)
 *   [161-168] unstake_timestamp (i64 LE, 8 bytes)
 *
 * StakeConfig (193 bytes):
 *   [0-7]     discriminator
 *   [8]       bump (u8)
 *   [9-40]    authority (pubkey)
 *   [41-72]   mint (pubkey)
 *   [73-104]  stake_vault (pubkey)
 *   [105-112] min_stake_amount (u64)
 *   [113-120] cooldown_seconds (u64)
 *   [121-136] total_shares (u128)
 *   [137-152] share_price (u128 LE) <-- key field
 *   [153-168] commission_weight_sum (u128)
 *   [169-184] cumulative_commission_per_share (u128)
 *   [185-192] last_vault_amount (u64)
 */
object StakingRpcClient {

    private const val TAG = "SeekerVerify"

    private val SKR_STAKING_PROGRAM get() = AppConfig.Tokens.SKR_STAKING_PROGRAM
    private val STAKE_CONFIG get() = AppConfig.Tokens.SKR_STAKE_CONFIG
    private val GUARDIAN_POOL get() = AppConfig.Tokens.GUARDIAN_POOL_SOLANA_MOBILE
    private val SHARE_PRICE_PRECISION get() = AppConfig.Tokens.SHARE_PRICE_PRECISION
    private val SKR_DECIMALS get() = AppConfig.Tokens.SKR_DECIMALS_DIVISOR

    data class StakingInfo(
        val stakedAmount: Long,         // raw lamports (6 decimals) — current value of shares
        val stakedDisplay: Double,      // human-readable current value in SKR
        val originalDeposit: Long,      // raw original SKR deposited (shares * cost_basis / precision)
        val originalDepositDisplay: Double, // human-readable original deposit in SKR
        val rewardsAccrued: Long,       // calculated: stakedAmount - originalDeposit
        val rewardsDisplay: Double,     // calculated rewards in SKR
        val cooldownAmount: Long,       // raw amount in unstaking cooldown
        val cooldownDisplay: Double,    // human-readable cooldown SKR
        val stakeAccountAddress: String?,
        val isStaked: Boolean,
        val activeShares: Long,
        val sharePrice: Long,
        val costBasisPrice: Long        // share_price at time of staking (from on-chain cost_basis)
    )

    /**
     * Get staking info for a wallet.
     *
     * Strategy:
     * 1. Fetch the StakeConfig account to get the current share_price
     * 2. Derive UserStake PDA and use getAccountInfo (single call, reliable)
     * 3. Fallback: getProgramAccounts with memcmp filter (if PDA derivation fails)
     */
    suspend fun getStakingInfo(
        walletAddress: String,
        rpcUrl: String
    ): Result<StakingInfo> {
        return try {
            val sharePrice = fetchSharePrice(rpcUrl)
            Log.d(TAG, "SKR Staking: share_price=$sharePrice (${sharePrice.toDouble() / SHARE_PRICE_PRECISION} ratio)")

            val pdaResult = tryPdaApproach(walletAddress, rpcUrl, sharePrice)
            if (pdaResult != null) {
                Log.w(TAG, "SKR Staking: staked=${pdaResult.stakedDisplay} SKR, " +
                    "shares=${pdaResult.activeShares}, unstaking=${pdaResult.cooldownDisplay} SKR")
                return Result.success(pdaResult)
            }

            Log.w(TAG, "SKR Staking: PDA approach returned null, falling back to getProgramAccounts")
            val fallbackResult = tryGetProgramAccounts(walletAddress, rpcUrl, sharePrice)
            fallbackResult.onFailure { e ->
                Log.e(TAG, "SKR Staking: BOTH approaches failed. fallback=${e.message}")
            }
            fallbackResult
        } catch (e: Exception) {
            Log.e(TAG, "SKR Staking: Fatal error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Primary approach: Derive UserStake PDA and fetch via getAccountInfo.
     * seeds = ["user_stake", stake_config, user_wallet, guardian_pool]
     * program = SKR_STAKING_PROGRAM
     */
    private suspend fun tryPdaApproach(
        walletAddress: String,
        rpcUrl: String,
        sharePrice: Long
    ): StakingInfo? {
        return try {
            val configBytes = Base58.decode(STAKE_CONFIG)
            val walletBytes = Base58.decode(walletAddress)
            val guardianPoolBytes = Base58.decode(GUARDIAN_POOL)
            val programBytes = Base58.decode(SKR_STAKING_PROGRAM)

            val seeds = listOf(
                "user_stake".toByteArray(),
                configBytes,
                walletBytes,
                guardianPoolBytes
            )

            val pda = findProgramAddress(seeds, programBytes) ?: run {
                Log.w(TAG, "SKR Staking: PDA derivation failed — no valid bump found")
                return null
            }

            val pdaAddress = Base58.encode(pda)
            Log.d(TAG, "SKR Staking: PDA=$pdaAddress wallet=${walletAddress.take(8)} guardian_pool=${GUARDIAN_POOL.take(8)}")

            val params = buildJsonArray {
                add(JsonPrimitive(pdaAddress))
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }

            val result = RpcProvider.call(rpcUrl, "getAccountInfo", params)

            result.fold(
                onSuccess = { response ->
                    val value = response.jsonObject["value"]
                    if (value == null || value.toString() == "null") {
                        Log.d(TAG, "SKR Staking: UserStake PDA does not exist (no stake with Solana Mobile guardian)")
                        return StakingInfo(0L, 0.0, 0L, 0.0, 0L, 0.0, 0L, 0.0, null, false, 0L, sharePrice, 0L)
                    }

                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content
                        ?: return null

                    val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                    Log.d(TAG, "SKR Staking: PDA account data received, size=${data.size} bytes")
                    parseUserStakeData(data, pdaAddress, sharePrice)
                },
                onFailure = { e ->
                    Log.e(TAG, "PDA getAccountInfo failed: ${e.message}")
                    null
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "PDA approach error: ${e.message}", e)
            null
        }
    }

    /**
     * Fallback: Find UserStake account via getProgramAccounts with memcmp filter.
     * Filters by wallet pubkey at offset 41 (user field in UserStake).
     */
    private suspend fun tryGetProgramAccounts(
        walletAddress: String,
        rpcUrl: String,
        sharePrice: Long
    ): Result<StakingInfo> {
        val params = buildJsonArray {
            add(JsonPrimitive(SKR_STAKING_PROGRAM))
            add(buildJsonObject {
                put("encoding", "base64")
                put("filters", buildJsonArray {
                    add(buildJsonObject {
                        put("dataSize", 169)
                    })
                    // user pubkey at offset 41 (8 disc + 1 bump + 32 config = 41)
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

        return result.fold(
            onSuccess = { response ->
                val accounts = response.jsonArray
                if (accounts.isEmpty()) {
                    return Result.success(
                        StakingInfo(0L, 0.0, 0L, 0.0, 0L, 0.0, 0L, 0.0, null, false, 0L, sharePrice, 0L)
                    )
                }

                // Sum across all guardian pools (future: multiple guardians)
                var totalStaked = 0L
                var totalOrigDeposit = 0L
                var totalUnstaking = 0L
                var totalShares = 0L
                var firstAccount: String? = null
                var firstCostBasisPrice = 0L

                for (acct in accounts) {
                    val obj = acct.jsonObject
                    val pubkey = obj["pubkey"]?.jsonPrimitive?.content
                    if (firstAccount == null) firstAccount = pubkey
                    val dataBase64 = obj["account"]?.jsonObject
                        ?.get("data")?.jsonArray
                        ?.firstOrNull()?.jsonPrimitive?.content ?: continue

                    val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                    val info = parseUserStakeData(data, pubkey, sharePrice) ?: continue
                    totalStaked += info.stakedAmount
                    totalOrigDeposit += info.originalDeposit
                    totalUnstaking += info.cooldownAmount
                    totalShares += info.activeShares
                    if (firstCostBasisPrice == 0L) firstCostBasisPrice = info.costBasisPrice
                }

                val totalRewards = if (totalStaked > totalOrigDeposit && totalOrigDeposit > 0) {
                    totalStaked - totalOrigDeposit
                } else 0L

                Result.success(StakingInfo(
                    stakedAmount = totalStaked,
                    stakedDisplay = totalStaked / SKR_DECIMALS,
                    originalDeposit = totalOrigDeposit,
                    originalDepositDisplay = totalOrigDeposit / SKR_DECIMALS,
                    rewardsAccrued = totalRewards,
                    rewardsDisplay = totalRewards / SKR_DECIMALS,
                    cooldownAmount = totalUnstaking,
                    cooldownDisplay = totalUnstaking / SKR_DECIMALS,
                    stakeAccountAddress = firstAccount,
                    isStaked = totalShares > 0,
                    activeShares = totalShares,
                    sharePrice = sharePrice,
                    costBasisPrice = firstCostBasisPrice
                ))
            },
            onFailure = { e ->
                Log.e(TAG, "Staking getProgramAccounts failed: ${e.message}")
                Result.failure(e)
            }
        )
    }

    /**
     * Parse a 169-byte UserStake account data blob into StakingInfo.
     */
    private fun parseUserStakeData(
        data: ByteArray,
        stakeAccount: String?,
        sharePrice: Long
    ): StakingInfo? {
        if (data.size < 169) {
            Log.w(TAG, "UserStake data too small: ${data.size}")
            return null
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // shares at offset 105 (u128 LE, 16 bytes)
        val shares = readU128AsLong(data, 105)
        // cost_basis at offset 121 (u128 LE, 16 bytes) — share_price at time of staking
        val costBasisPrice = readU128AsLong(data, 121)
        // unstaking_amount at offset 153 (u64 LE, 8 bytes)
        val unstakingAmount = buf.getLong(153)

        Log.w(TAG, "SKR Staking: shares=$shares, cost_basis=$costBasisPrice, unstaking=$unstakingAmount")

        // Current value: shares * current_share_price / SHARE_PRICE_PRECISION
        val stakedRaw = if (sharePrice > 0) {
            (shares.toBigInteger() * sharePrice.toBigInteger() /
                SHARE_PRICE_PRECISION.toBigInteger()).toLong()
        } else {
            0L
        }

        // Original deposit: shares * cost_basis_price / SHARE_PRICE_PRECISION
        // cost_basis stores the share_price when the user staked
        val originalDepositRaw = if (costBasisPrice > 0) {
            (shares.toBigInteger() * costBasisPrice.toBigInteger() /
                SHARE_PRICE_PRECISION.toBigInteger()).toLong()
        } else {
            0L
        }

        val stakedDisplay = stakedRaw / SKR_DECIMALS
        val originalDepositDisplay = originalDepositRaw / SKR_DECIMALS
        val unstakingDisplay = unstakingAmount / SKR_DECIMALS.toLong()

        // Rewards = current value - original deposit
        val rewardsRaw = if (stakedRaw > originalDepositRaw && originalDepositRaw > 0) {
            stakedRaw - originalDepositRaw
        } else {
            0L
        }
        val rewardsDisplay = rewardsRaw / SKR_DECIMALS

        Log.w(TAG, "SKR Staking: current=$stakedDisplay SKR, original=$originalDepositDisplay SKR, " +
            "rewards=$rewardsDisplay SKR, unstaking=$unstakingDisplay SKR " +
            "(shares=$shares × price=$sharePrice / cbPrice=$costBasisPrice)")

        return StakingInfo(
            stakedAmount = stakedRaw,
            stakedDisplay = stakedDisplay,
            originalDeposit = originalDepositRaw,
            originalDepositDisplay = originalDepositDisplay,
            rewardsAccrued = rewardsRaw,
            rewardsDisplay = rewardsDisplay,
            cooldownAmount = unstakingAmount,
            cooldownDisplay = unstakingDisplay.toDouble(),
            stakeAccountAddress = stakeAccount,
            isStaked = shares > 0,
            activeShares = shares,
            sharePrice = sharePrice,
            costBasisPrice = costBasisPrice
        )
    }

    /**
     * Read a u128 little-endian value from a byte array, returning as Long.
     * Safe for values that fit in Long (which all current staking values do).
     */
    private fun readU128AsLong(data: ByteArray, offset: Int): Long {
        // u128 LE: low 8 bytes at offset, high 8 bytes at offset+8
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val low = buf.getLong(offset)
        val high = buf.getLong(offset + 8)
        if (high != 0L) {
            Log.w(TAG, "SKR Staking: u128 value at offset $offset exceeds Long range (high=$high)")
        }
        return low
    }

    /**
     * Fetch the current share price from the StakeConfig account.
     * share_price is a u128 at offset 137 in StakeConfig (193 bytes).
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
                        Log.w(TAG, "StakeConfig not found, using fallback share price")
                        return FALLBACK_SHARE_PRICE
                    }

                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content

                    if (dataBase64 != null) {
                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
                        if (data.size >= 153) {
                            // share_price is u128 at offset 137
                            val price = readU128AsLong(data, 137)
                            Log.d(TAG, "SKR Staking: share_price=$price (live)")
                            price
                        } else {
                            Log.w(TAG, "StakeConfig data too small (${data.size}), using fallback")
                            FALLBACK_SHARE_PRICE
                        }
                    } else {
                        Log.w(TAG, "StakeConfig has no data, using fallback")
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
     * Estimate APY dynamically from share price history.
     * Falls back to static estimate if insufficient data.
     *
     * @param priceHistory list of (timestamp, sharePrice) snapshots sorted chronologically
     */
    fun estimateApy(priceHistory: List<Pair<Long, Long>> = emptyList()): Double {
        if (priceHistory.size < 2) return FALLBACK_APY

        val oldest = priceHistory.first()
        val newest = priceHistory.last()
        val daysBetween = (newest.first - oldest.first) / 86_400_000.0
        if (daysBetween < 1) return FALLBACK_APY

        if (oldest.second <= 0 || newest.second <= 0) return FALLBACK_APY
        val priceRatio = newest.second.toDouble() / oldest.second.toDouble()
        if (priceRatio <= 0) return FALLBACK_APY

        return ((Math.pow(priceRatio, 365.0 / daysBetween) - 1) * 100)
            .coerceIn(0.0, 200.0)
    }

    private const val FALLBACK_APY = 20.7

    private val FALLBACK_SHARE_PRICE get() = AppConfig.Tokens.FALLBACK_SHARE_PRICE

    // ==================== PDA Derivation Helpers ====================

    /**
     * Find a program-derived address (PDA).
     * SHA256(seeds + [nonce] + programId + "ProgramDerivedAddress")
     * finds first nonce (255→0) where result is off the ed25519 curve.
     */
    private fun findProgramAddress(
        seeds: List<ByteArray>,
        programId: ByteArray
    ): ByteArray? {
        val suffix = "ProgramDerivedAddress".toByteArray()

        for (nonce in 255 downTo 0) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                for (seed in seeds) {
                    digest.update(seed)
                }
                digest.update(byteArrayOf(nonce.toByte()))
                digest.update(programId)
                digest.update(suffix)

                val hash = digest.digest()

                if (!isOnCurve(hash)) {
                    return hash
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Check if a 32-byte hash is a valid ed25519 public key (on-curve).
     * Uses Euler criterion (Legendre symbol) to check if x² is a quadratic residue.
     */
    private fun isOnCurve(point: ByteArray): Boolean {
        if (point.size != 32) return false

        val p = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))
        val d = BigInteger("-7053407859506552187299420025600839209977975968590236691996009792624681218700")

        // Extract y from compressed ed25519 point (LE, clear sign bit)
        val yBytes = point.copyOf()
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
        val y = BigInteger(1, yBytes.reversedArray())

        if (y >= p) return false

        // ed25519 curve: -x² + y² = 1 + d·x²·y²
        // Solve for x²: x² = (y² - 1) / (d·y² + 1)
        val y2 = y.modPow(BigInteger.TWO, p)
        val u = y2.subtract(BigInteger.ONE).mod(p)
        val v = d.multiply(y2).add(BigInteger.ONE).mod(p)

        if (v == BigInteger.ZERO) return false

        val vInv = v.modPow(p.subtract(BigInteger.TWO), p)
        val x2 = u.multiply(vInv).mod(p)

        // Euler criterion: x²^((p-1)/2) mod p
        // = 1 → quadratic residue (on curve)
        // = 0 → x²=0 (on curve, x=0)
        // = p-1 → non-residue (NOT on curve)
        val euler = x2.modPow(p.subtract(BigInteger.ONE).divide(BigInteger.TWO), p)

        return euler == BigInteger.ONE || euler == BigInteger.ZERO
    }
}
