package com.seekerverify.app.wallet

import android.net.Uri
import android.util.Log
import com.seekerverify.app.AppConfig
import com.seekerverify.app.rpc.RpcProvider
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger

/**
 * Manages Solana Mobile Wallet Adapter connections for Seeker Verify.
 *
 * Uses Sign In With Solana (SIWS) for cryptographic proof of wallet ownership.
 * The user must actively sign a message on their Seeker device to connect.
 */
object WalletManager {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE58 = BigInteger.valueOf(58)

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    data class WalletConnectResult(
        val publicKeyBase58: String,
        val walletName: String?
    )

    private val mobileWalletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse(AppConfig.Identity.URI),
            iconUri = Uri.parse(AppConfig.Identity.ICON_URI),
            identityName = AppConfig.Identity.NAME
        )
    ).apply {
        blockchain = Solana.Mainnet
    }

    /**
     * Sign In With Solana (SIWS) — cryptographic wallet authentication.
     *
     * This presents a sign-in request to the wallet app. The user must
     * review the domain + statement and actively confirm by tapping the
     * Seeker side button. The wallet returns a signed payload proving
     * ownership of the public key.
     *
     * Guarded against concurrent calls — double-tapping Sign In during
     * the MWA handshake will not crash the app.
     */
    suspend fun signIn(sender: ActivityResultSender): Result<WalletConnectResult> {
        if (_isConnecting.value) return Result.failure(Exception("Already connecting"))
        _isConnecting.value = true
        return try {
            val signInPayload = SignInWithSolana.Payload(
                Uri.parse(AppConfig.Identity.URI).host,
                "Sign in to Seeker Verify with your Solana wallet"
            )

            val result = mobileWalletAdapter.signIn(sender, signInPayload)

            when (result) {
                is TransactionResult.Success -> {
                    val signInResult = result.payload
                    val pubKeyBytes = signInResult.publicKey

                    if (pubKeyBytes.size != 32) {
                        Result.failure(Exception("Invalid public key length: ${pubKeyBytes.size}"))
                    } else {
                        val pubKeyBase58 = bytesToBase58(pubKeyBytes)
                        val walletName = result.authResult.walletUriBase?.host ?: "Seeker"
                        Result.success(WalletConnectResult(pubKeyBase58, walletName))
                    }
                }
                is TransactionResult.NoWalletFound -> {
                    Result.failure(Exception("No Solana wallet found on this device"))
                }
                is TransactionResult.Failure -> {
                    Result.failure(result.e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isConnecting.value = false
        }
    }

    /**
     * Sign and send a memo transaction via Seed Vault.
     *
     * Flow: authorize → get blockhash → build memo tx → signAndSendTransactions
     *
     * @param sender ActivityResultSender for MWA communication
     * @param rpcUrl Solana RPC endpoint for blockhash fetch
     * @param memo Memo string to write on-chain (e.g. "SV:CI:2026-03-04:5")
     * @return Transaction signature as base58 string
     */
    suspend fun signAndSendMemo(
        sender: ActivityResultSender,
        rpcUrl: String,
        memo: String
    ): Result<String> {
        // Get recent blockhash first (before MWA session, to avoid timeout)
        val blockhashStr = try {
            val params = buildJsonArray {
                add(buildJsonObject { put("commitment", "finalized") })
            }
            val rpcResult = RpcProvider.call(rpcUrl, "getLatestBlockhash", params)
            rpcResult.getOrElse { return Result.failure(Exception("Failed to get blockhash: ${it.message}")) }
                .jsonObject["value"]?.jsonObject?.get("blockhash")?.jsonPrimitive?.content
                ?: return Result.failure(Exception("No blockhash in response"))
        } catch (e: Exception) {
            return Result.failure(Exception("Blockhash fetch failed: ${e.message}"))
        }

        val blockhashBytes = SolanaTransactionBuilder.decodeBase58(blockhashStr)
        if (blockhashBytes.size != 32) {
            return Result.failure(Exception("Invalid blockhash size: ${blockhashBytes.size}"))
        }

        return try {
            val result = mobileWalletAdapter.transact(sender) {
                val authResult = authorize(
                    identityUri = Uri.parse(AppConfig.Identity.URI),
                    iconUri = Uri.parse(AppConfig.Identity.ICON_URI),
                    identityName = AppConfig.Identity.NAME,
                    chain = AppConfig.Wallet.CHAIN
                )

                val userPubkey = authResult.accounts.firstOrNull()?.publicKey
                    ?: throw Exception("No account returned from wallet")

                Log.d(TAG, "Memo tx: user=${bytesToBase58(userPubkey).take(8)}... memo=$memo")

                val txBytes = SolanaTransactionBuilder.buildMemoTransaction(
                    feePayer = userPubkey,
                    recentBlockhash = blockhashBytes,
                    memo = memo
                )

                signAndSendTransactions(transactions = arrayOf(txBytes))
            }

            when (result) {
                is TransactionResult.Success -> {
                    val signatures = result.payload.signatures
                    val signature = if (signatures.isNotEmpty()) {
                        bytesToBase58(signatures[0])
                    } else {
                        return Result.failure(Exception("No signature returned"))
                    }
                    Log.d(TAG, "Memo tx confirmed: $signature")
                    Result.success(signature)
                }
                is TransactionResult.NoWalletFound -> {
                    Result.failure(Exception("No Solana wallet found on this device"))
                }
                is TransactionResult.Failure -> {
                    Result.failure(result.e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Memo tx failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun bytesToBase58(bytes: ByteArray): String {
        var num = BigInteger(1, bytes)
        val sb = StringBuilder()

        while (num > BigInteger.ZERO) {
            val divRem = num.divideAndRemainder(BASE58)
            sb.append(ALPHABET[divRem[1].toInt()])
            num = divRem[0]
        }

        for (byte in bytes) {
            if (byte.toInt() == 0) sb.append(ALPHABET[0])
            else break
        }

        return sb.reverse().toString()
    }

    private const val TAG = "SeekerVerify"
}
