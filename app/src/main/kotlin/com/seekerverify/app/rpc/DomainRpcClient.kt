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
import java.security.MessageDigest

/**
 * Resolves .skr domains from the AllDomains ANS protocol.
 *
 * AllDomains uses:
 * - ANS program: ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK (name registry)
 * - TLD House program: TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S
 *
 * Account layout for NameRecordHeader (200 bytes):
 * - 0-7: discriminator [68,72,88,44,15,167,103,243]
 * - 8-39: parentName (32 bytes, TLD parent pubkey)
 * - 40-71: owner (32 bytes, domain owner wallet)
 * - 72-103: nclass (32 bytes)
 * - 104-111: expiresAt (u64 LE, epoch seconds)
 * - 112-119: createdAt (u64 LE)
 * - 120: nonTransferable (u8)
 * - 121-199: padding
 * - 200+: variable data (reverse lookup stores domain string here)
 */
object DomainRpcClient {

    private const val TAG = "SeekerVerify"

    // NameRecordHeader discriminator for AllDomains
    private val NAME_RECORD_DISCRIMINATOR = byteArrayOf(68, 72, 88, 44, 15, -89, 103, -13)
    // (-89 = 167 as signed byte, -13 = 243 as signed byte)

    data class DomainInfo(
        val domainName: String,          // e.g. "varky"
        val fullDomain: String,          // e.g. "varky.skr"
        val nameAccountAddress: String,  // on-chain account pubkey
        val expiresAt: Long?,            // epoch seconds, null if unknown
        val isExpired: Boolean
    )

    /**
     * Find all .skr domains owned by a wallet.
     *
     * Strategy:
     * 1. Derive the .skr TLD House PDA
     * 2. Read the TLD House to find the parentName for .skr
     * 3. Query getProgramAccounts on ANS with memcmp filter at offset 40 (owner = wallet)
     *    and offset 8 (parentName = .skr parent)
     * 4. Parse domain names from reverse lookup or the account data
     */
    suspend fun getSkrDomains(
        walletAddress: String,
        rpcUrl: String
    ): Result<List<DomainInfo>> {
        return try {
            // Step 1: Derive the TLD House PDA for "skr"
            val tldHousePda = deriveTldHousePda(AppConfig.Domains.SKR_TLD_NAME)
            if (tldHousePda == null) {
                Log.e(TAG, "Failed to derive TLD House PDA")
                return Result.success(emptyList())
            }
            Log.d(TAG, "TLD House PDA for .skr: $tldHousePda")

            // Step 2: Find the parent name account for .skr TLD
            // The parent name is derived by hashing the TLD name under the ANS program
            val parentName = deriveTldParentName(AppConfig.Domains.SKR_TLD_NAME)
            if (parentName == null) {
                Log.e(TAG, "Failed to derive TLD parent name")
                return Result.success(emptyList())
            }
            Log.d(TAG, "TLD parent name for .skr: $parentName")

            // Step 3: Query ANS program accounts with owner filter

            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(AppConfig.Domains.ANS_PROGRAM_ID))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("filters", buildJsonArray {
                        // Filter 1: owner at offset 40
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", 40)
                                put("bytes", walletAddress)
                            })
                        })
                        // Filter 2: parentName at offset 8
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", 8)
                                put("bytes", parentName)
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
                        Log.d(TAG, "No .skr domains found for wallet")
                        return Result.success(emptyList())
                    }

                    Log.d(TAG, "Found ${accounts.size} .skr domain account(s)")

                    val domains = mutableListOf<DomainInfo>()

                    for (account in accounts) {
                        val obj = account.jsonObject
                        val nameAccountAddr = obj["pubkey"]?.jsonPrimitive?.content ?: continue
                        val dataArray = obj["account"]?.jsonObject?.get("data")?.jsonArray
                        val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content ?: continue

                        val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)

                        if (data.size < AppConfig.Domains.NAME_RECORD_HEADER_SIZE) continue

                        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                        // Parse expiresAt at offset 104 (u64 LE)
                        val expiresAt = buf.getLong(104)
                        val now = System.currentTimeMillis() / 1000
                        val isExpired = expiresAt > 0 && expiresAt < now

                        // We have the name account; now we need the domain string
                        // Try reverse lookup
                        val domainName = reverseLookup(nameAccountAddr, rpcUrl)

                        if (domainName != null) {
                            domains.add(DomainInfo(
                                domainName = domainName,
                                fullDomain = "$domainName${AppConfig.Domains.SKR_TLD}",
                                nameAccountAddress = nameAccountAddr,
                                expiresAt = if (expiresAt > 0) expiresAt else null,
                                isExpired = isExpired
                            ))
                        } else {
                            // Fallback: use truncated account address as placeholder
                            domains.add(DomainInfo(
                                domainName = "unknown",
                                fullDomain = "unknown${AppConfig.Domains.SKR_TLD}",
                                nameAccountAddress = nameAccountAddr,
                                expiresAt = if (expiresAt > 0) expiresAt else null,
                                isExpired = isExpired
                            ))
                        }
                    }

                    Result.success(domains)
                },
                onFailure = { e ->
                    Log.e(TAG, "Domain lookup failed: ${e.message}")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Domain resolver error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get the main/primary .skr domain for a wallet.
     * Uses the MainDomain PDA: seeds ["main_domain", user.toBuffer()] under TLD_HOUSE_PROGRAM_ID.
     */
    suspend fun getMainDomain(
        walletAddress: String,
        rpcUrl: String
    ): Result<DomainInfo?> {
        return try {
            val walletBytes = Base58.decode(walletAddress)
            val mainDomainPda = findProgramAddress(
                seeds = listOf("main_domain".toByteArray(), walletBytes),
                programId = Base58.decode(AppConfig.Domains.TLD_HOUSE_PROGRAM_ID)
            )

            if (mainDomainPda == null) {
                return Result.success(null)
            }

            val mainDomainAddr = Base58.encode(mainDomainPda)
            Log.d(TAG, "Main domain PDA: $mainDomainAddr")

            // Fetch the main domain account
            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(mainDomainAddr))
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }

            val result = RpcProvider.call(rpcUrl, "getAccountInfo", params)

            result.fold(
                onSuccess = { response ->
                    val value = response.jsonObject["value"]
                    if (value == null || value.toString() == "null") {
                        Log.d(TAG, "No main domain set for wallet")
                        return Result.success(null)
                    }

                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content
                        ?: return Result.success(null)

                    val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)

                    // MainDomain layout:
                    // 8 bytes discriminator
                    // 32 bytes nameAccount pubkey
                    // 4 bytes tld string length (borsh)
                    // N bytes tld string
                    // 4 bytes domain string length (borsh)
                    // N bytes domain string

                    if (data.size < 44) {
                        return Result.success(null)
                    }

                    val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                    // Skip discriminator (8) + nameAccount (32) = offset 40
                    val tldLen = buf.getInt(40)
                    if (tldLen <= 0 || tldLen > 32 || 44 + tldLen > data.size) {
                        return Result.success(null)
                    }
                    val tld = String(data, 44, tldLen)

                    val domainLenOffset = 44 + tldLen
                    if (domainLenOffset + 4 > data.size) {
                        return Result.success(null)
                    }
                    val domainLen = buf.getInt(domainLenOffset)
                    val domainOffset = domainLenOffset + 4
                    if (domainLen <= 0 || domainLen > 64 || domainOffset + domainLen > data.size) {
                        return Result.success(null)
                    }
                    val domain = String(data, domainOffset, domainLen)

                    val nameAccountBytes = data.copyOfRange(8, 40)
                    val nameAccountAddr = Base58.encode(nameAccountBytes)

                    Log.d(TAG, "Main domain: $domain.$tld (nameAccount=$nameAccountAddr)")

                    Result.success(DomainInfo(
                        domainName = domain,
                        fullDomain = "$domain.$tld",
                        nameAccountAddress = nameAccountAddr,
                        expiresAt = null,
                        isExpired = false
                    ))
                },
                onFailure = { e ->
                    Log.e(TAG, "Main domain fetch failed: ${e.message}")
                    Result.success(null)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Main domain error: ${e.message}", e)
            Result.success(null)
        }
    }

    /**
     * Reverse lookup: given a name account, find the human-readable domain string.
     */
    private suspend fun reverseLookup(
        nameAccountAddress: String,
        rpcUrl: String
    ): String? {
        return try {
            // Hash the name account address to find the reverse lookup PDA
            val hashedName = getHashedName(nameAccountAddress)
            val nameClass = ByteArray(32) // zeroed
            val parentName = ByteArray(32) // zeroed for reverse lookups

            val reversePda = findProgramAddress(
                seeds = listOf(hashedName, nameClass, parentName),
                programId = Base58.decode(AppConfig.Domains.ANS_PROGRAM_ID)
            ) ?: return null

            val reversePdaAddr = Base58.encode(reversePda)

            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(reversePdaAddr))
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }

            val result = RpcProvider.call(rpcUrl, "getAccountInfo", params)

            result.fold(
                onSuccess = { response ->
                    val value = response.jsonObject["value"]
                    if (value == null || value.toString() == "null") return@fold null

                    val dataArray = value.jsonObject["data"]?.jsonArray
                    val dataBase64 = dataArray?.firstOrNull()?.jsonPrimitive?.content ?: return@fold null

                    val data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)

                    // Domain string is at offset 200+ in the reverse lookup account
                    if (data.size <= AppConfig.Domains.NAME_RECORD_HEADER_SIZE) return@fold null

                    val domainBytes = data.copyOfRange(
                        AppConfig.Domains.NAME_RECORD_HEADER_SIZE,
                        data.size
                    )

                    // Remove null bytes
                    val domainStr = String(domainBytes).trim('\u0000').trim()
                    if (domainStr.isNotEmpty()) domainStr else null
                },
                onFailure = { null }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Reverse lookup failed for $nameAccountAddress: ${e.message}")
            null
        }
    }

    /**
     * Derive the TLD House PDA.
     * Seeds: ["tld_house", tldName.lowercase()] under TLD_HOUSE_PROGRAM_ID
     */
    private fun deriveTldHousePda(tldName: String): String? {
        val pda = findProgramAddress(
            seeds = listOf("tld_house".toByteArray(), tldName.lowercase().toByteArray()),
            programId = Base58.decode(AppConfig.Domains.TLD_HOUSE_PROGRAM_ID)
        ) ?: return null
        return Base58.encode(pda)
    }

    /**
     * Derive the TLD parent name account PDA.
     * This is the name account for the TLD itself under ANS.
     * Seeds: [SHA256("ALT Name Service" + tldName), zeros(32), zeros(32)] under ANS_PROGRAM_ID
     */
    private fun deriveTldParentName(tldName: String): String? {
        val hashedName = getHashedName(tldName)
        val pda = findProgramAddress(
            seeds = listOf(hashedName, ByteArray(32), ByteArray(32)),
            programId = Base58.decode(AppConfig.Domains.ANS_PROGRAM_ID)
        ) ?: return null
        return Base58.encode(pda)
    }

    /**
     * Hash a name using SHA-256 with the AllDomains prefix.
     * Input: "ALT Name Service" + name
     * Output: SHA-256 hash (32 bytes)
     */
    private fun getHashedName(name: String): ByteArray {
        val input = "${AppConfig.Domains.HASH_PREFIX}$name"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
    }

    /**
     * Find a program-derived address (PDA).
     * Solana PDA: find nonce (255 down to 0) where SHA256(seeds + [nonce] + programId + "ProgramDerivedAddress")
     * falls off the ed25519 curve.
     *
     * Returns the PDA bytes (32) or null if not found.
     */
    private fun findProgramAddress(
        seeds: List<ByteArray>,
        programId: ByteArray
    ): ByteArray? {
        val programDerivedSuffix = "ProgramDerivedAddress".toByteArray()

        for (nonce in 255 downTo 0) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                for (seed in seeds) {
                    digest.update(seed)
                }
                digest.update(byteArrayOf(nonce.toByte()))
                digest.update(programId)
                digest.update(programDerivedSuffix)

                val hash = digest.digest()

                // Check if the point is off the ed25519 curve
                // For simplicity, we check that it's not a valid ed25519 point.
                // A valid ed25519 point satisfies: y^2 = x^3 + 486662*x^2 + x (mod p)
                // The simplest heuristic: if the high bit of byte 31 would make it
                // fail point decompression, it's off-curve.
                // In practice, ~50% of hashes are off-curve, so nonce=255 usually works.

                // Use the TweetNaCl-style check: attempt to decompress the point
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
     * Uses the ed25519 curve equation to verify.
     *
     * This is a simplified check — in production, use a proper ed25519 library.
     * For PDA derivation, we just need to reject points that ARE on the curve.
     */
    private fun isOnCurve(point: ByteArray): Boolean {
        if (point.size != 32) return false

        // Ed25519 curve parameters
        // p = 2^255 - 19
        val p = java.math.BigInteger.TWO.pow(255).subtract(java.math.BigInteger.valueOf(19))
        val d = java.math.BigInteger("-7053407859506552187299420025600839209977975968590236691996009792624681218700")

        // Decode y coordinate (little-endian, clear high bit)
        val yBytes = point.copyOf()
        @Suppress("UNUSED_VARIABLE")
        val xSign = (yBytes[31].toInt() shr 7) and 1 // stored for potential future use
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()

        // Convert to BigInteger (little-endian)
        val reversedY = yBytes.reversedArray()
        val y = java.math.BigInteger(1, reversedY)

        if (y >= p) return false

        // Compute y^2 mod p
        val y2 = y.modPow(java.math.BigInteger.TWO, p)

        // Compute u = y^2 - 1
        val u = y2.subtract(java.math.BigInteger.ONE).mod(p)

        // Compute v = d * y^2 + 1
        val v = d.multiply(y2).add(java.math.BigInteger.ONE).mod(p)

        // Compute x^2 = u * v^-1 mod p
        val vInv = v.modPow(p.subtract(java.math.BigInteger.TWO), p)
        val x2 = u.multiply(vInv).mod(p)

        // Check if x^2 has a square root mod p
        // x = x2^((p+3)/8) mod p
        val exp = p.add(java.math.BigInteger.valueOf(3)).divide(java.math.BigInteger.valueOf(8))
        val x = x2.modPow(exp, p)

        // Verify: x^2 == x2 mod p
        val check = x.modPow(java.math.BigInteger.TWO, p)

        if (check == x2) return true

        // Also check with sqrt(-1) factor
        val sqrtM1 = java.math.BigInteger.TWO.modPow(
            p.subtract(java.math.BigInteger.ONE).divide(java.math.BigInteger.valueOf(4)),
            p
        )
        val x2Neg = x2.multiply(sqrtM1).mod(p)
        val xAlt = x2Neg.modPow(exp, p)
        val checkAlt = xAlt.modPow(java.math.BigInteger.TWO, p)

        return checkAlt == x2 || checkAlt == x2Neg
    }
}
