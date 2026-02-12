package com.seekerverify.app.wallet

import android.net.Uri
import com.seekerverify.app.AppConfig
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import java.math.BigInteger

/**
 * Manages Solana Mobile Wallet Adapter connections for Seeker Verify.
 */
object WalletManager {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE58 = BigInteger.valueOf(58)

    data class WalletConnectResult(
        val publicKeyBase58: String,
        val walletName: String?
    )

    fun createAdapter(): MobileWalletAdapter {
        return MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse(AppConfig.Identity.URI),
                iconUri = Uri.parse(AppConfig.Identity.ICON_URI),
                identityName = AppConfig.Identity.NAME
            )
        )
    }

    suspend fun connect(
        adapter: MobileWalletAdapter,
        sender: ActivityResultSender
    ): Result<WalletConnectResult> {
        return try {
            val result = adapter.transact(sender) {
                authorize(
                    identityUri = Uri.parse(AppConfig.Identity.URI),
                    iconUri = Uri.parse(AppConfig.Identity.ICON_URI),
                    identityName = AppConfig.Identity.NAME,
                    chain = AppConfig.Wallet.CHAIN
                )
            }

            when (result) {
                is TransactionResult.Success -> {
                    val accounts = result.authResult.accounts
                    if (accounts.isEmpty()) {
                        Result.failure(Exception("No accounts returned from wallet"))
                    } else {
                        val publicKeyBytes = accounts.first().publicKey
                        if (publicKeyBytes.size != 32) {
                            Result.failure(Exception("Invalid public key length: ${publicKeyBytes.size}"))
                        } else {
                            val pubKeyBase58 = bytesToBase58(publicKeyBytes)
                            val walletName = result.authResult.walletUriBase?.toString()
                            Result.success(WalletConnectResult(pubKeyBase58, walletName))
                        }
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
