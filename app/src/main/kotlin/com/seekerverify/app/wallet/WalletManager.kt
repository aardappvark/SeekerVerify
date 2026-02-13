package com.seekerverify.app.wallet

import android.net.Uri
import com.seekerverify.app.AppConfig
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
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
     */
    suspend fun signIn(sender: ActivityResultSender): Result<WalletConnectResult> {
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
        }
    }

    private fun bytesToBase58(bytes: ByteArray): String {
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
}
