package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.AppConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Fetches SKR token balance for a wallet using getTokenAccountsByOwner.
 */
object SkrRpcClient {

    private const val TAG = "SeekerVerify"
    private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    data class SkrBalanceResult(
        val rawAmount: Long,      // raw lamports (6 decimals)
        val displayAmount: Double, // human-readable amount
        val tokenAccount: String?  // the ATA address
    )

    /**
     * Get SKR balance for a wallet.
     * Uses getTokenAccountsByOwner with mint filter.
     */
    suspend fun getSkrBalance(
        walletAddress: String,
        rpcUrl: String
    ): Result<SkrBalanceResult> {
        return try {
            val params = buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(walletAddress))
                add(buildJsonObject {
                    put("mint", AppConfig.Tokens.SKR_MINT)
                })
                add(buildJsonObject {
                    put("encoding", "jsonParsed")
                })
            }

            val result = RpcProvider.call(rpcUrl, "getTokenAccountsByOwner", params)

            result.fold(
                onSuccess = { response ->
                    val accounts = response.jsonObject["value"]?.jsonArray
                    if (accounts == null || accounts.isEmpty()) {
                        Log.d(TAG, "No SKR token account found")
                        return Result.success(SkrBalanceResult(0L, 0.0, null))
                    }

                    val account = accounts.first().jsonObject
                    val tokenAccount = account["pubkey"]?.jsonPrimitive?.content
                    val parsed = account["account"]?.jsonObject
                        ?.get("data")?.jsonObject
                        ?.get("parsed")?.jsonObject
                        ?.get("info")?.jsonObject

                    val tokenAmount = parsed?.get("tokenAmount")?.jsonObject
                    val rawAmount = tokenAmount?.get("amount")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    val displayAmount = rawAmount / 1_000_000.0

                    Log.d(TAG, "SKR balance: $displayAmount ($rawAmount raw)")
                    Result.success(SkrBalanceResult(rawAmount, displayAmount, tokenAccount))
                },
                onFailure = { e ->
                    Log.e(TAG, "SKR balance fetch failed: ${e.message}")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "SKR balance error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
