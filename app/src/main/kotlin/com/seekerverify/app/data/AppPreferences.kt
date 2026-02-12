package com.seekerverify.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.seekerverify.app.AppConfig
import com.seekerverify.app.model.CheckInStreak
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypted local storage for Seeker Verify.
 * Uses AES-256 encryption via EncryptedSharedPreferences.
 */
class AppPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    // --- Wallet ---

    fun isWalletConnected(): Boolean = prefs.contains(KEY_WALLET_ADDRESS)

    fun getWalletAddress(): String? = prefs.getString(KEY_WALLET_ADDRESS, null)

    fun saveWalletConnection(publicKey: String, walletName: String?) {
        prefs.edit()
            .putString(KEY_WALLET_ADDRESS, publicKey)
            .putString(KEY_WALLET_NAME, walletName)
            .putLong(KEY_WALLET_CONNECTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun disconnectWallet() {
        prefs.edit()
            .remove(KEY_WALLET_ADDRESS)
            .remove(KEY_WALLET_NAME)
            .remove(KEY_WALLET_CONNECTED_AT)
            .remove(KEY_HAS_SGT)
            .remove(KEY_SGT_CHECKED_AT)
            .remove(KEY_SGT_MEMBER_NUMBER)
            .remove(KEY_SGT_MINT_ADDRESS)
            .apply()
    }

    fun getShortWalletAddress(): String {
        val addr = getWalletAddress() ?: return ""
        return if (addr.length > 8) "${addr.take(4)}...${addr.takeLast(4)}" else addr
    }

    // --- SGT ---

    fun hasSgt(): Boolean = prefs.getBoolean(KEY_HAS_SGT, false)

    fun setSgtStatus(hasSgt: Boolean, memberNumber: Long? = null, mintAddress: String? = null) {
        prefs.edit()
            .putBoolean(KEY_HAS_SGT, hasSgt)
            .putLong(KEY_SGT_CHECKED_AT, System.currentTimeMillis())
            .apply {
                memberNumber?.let { putLong(KEY_SGT_MEMBER_NUMBER, it) }
                mintAddress?.let { putString(KEY_SGT_MINT_ADDRESS, it) }
            }
            .apply()
    }

    fun getMemberNumber(): Long? {
        return if (prefs.contains(KEY_SGT_MEMBER_NUMBER)) {
            prefs.getLong(KEY_SGT_MEMBER_NUMBER, 0)
        } else null
    }

    fun getSgtMintAddress(): String? = prefs.getString(KEY_SGT_MINT_ADDRESS, null)

    fun shouldRecheckSgt(): Boolean {
        val lastCheck = prefs.getLong(KEY_SGT_CHECKED_AT, 0)
        val elapsed = System.currentTimeMillis() - lastCheck
        return elapsed > AppConfig.Cache.SGT_CACHE_HOURS * 3600 * 1000
    }

    // --- Check-in Streak ---

    fun getCheckInStreak(): CheckInStreak {
        val streakJson = prefs.getString(KEY_CHECK_IN_STREAK, null) ?: return CheckInStreak()
        return try {
            json.decodeFromString<CheckInStreak>(streakJson)
        } catch (e: Exception) {
            CheckInStreak()
        }
    }

    fun saveCheckInStreak(streak: CheckInStreak) {
        prefs.edit()
            .putString(KEY_CHECK_IN_STREAK, json.encodeToString(streak))
            .apply()
    }

    // --- Settings ---

    fun getRpcProvider(): String = prefs.getString(KEY_RPC_PROVIDER, "public") ?: "public"

    fun setRpcProvider(provider: String) {
        prefs.edit().putString(KEY_RPC_PROVIDER, provider).apply()
    }

    fun isOptedIn(): Boolean = prefs.getBoolean(KEY_OPTED_IN, false)

    fun setOptedIn(optedIn: Boolean) {
        prefs.edit().putBoolean(KEY_OPTED_IN, optedIn).apply()
    }

    companion object {
        private const val PREFS_NAME = "seeker_verify_prefs"
        private const val KEY_WALLET_ADDRESS = "wallet_address"
        private const val KEY_WALLET_NAME = "wallet_name"
        private const val KEY_WALLET_CONNECTED_AT = "wallet_connected_at"
        private const val KEY_HAS_SGT = "has_sgt"
        private const val KEY_SGT_CHECKED_AT = "sgt_checked_at"
        private const val KEY_SGT_MEMBER_NUMBER = "sgt_member_number"
        private const val KEY_SGT_MINT_ADDRESS = "sgt_mint_address"
        private const val KEY_CHECK_IN_STREAK = "check_in_streak"
        private const val KEY_RPC_PROVIDER = "rpc_provider"
        private const val KEY_OPTED_IN = "opted_in"
    }
}
