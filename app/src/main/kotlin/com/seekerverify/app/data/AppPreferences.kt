package com.seekerverify.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.seekerverify.app.AppConfig
import com.seekerverify.app.model.Achievement
import com.seekerverify.app.model.CheckInStreak
import com.seekerverify.app.model.CommunityCache
import com.seekerverify.app.model.PortfolioCache
import com.seekerverify.app.model.PredictionCache
import com.seekerverify.app.model.PredictionHistoryEntry
import com.seekerverify.app.model.Season1AnalysisCache
import com.seekerverify.app.model.Season1Result
import com.seekerverify.app.model.SharePriceSnapshot
import com.seekerverify.app.model.TransactionRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypted local storage for Seeker Verify.
 * Uses AES-256 encryption via EncryptedSharedPreferences.
 * Falls back to standard SharedPreferences if Tink crypto is unavailable.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w("AppPreferences", "EncryptedSharedPreferences failed, using standard fallback: ${e.message}")
        context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

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
            .remove(KEY_CHECK_IN_STREAK)
            .remove(KEY_SEASON1_RESULT)
            .remove(KEY_SEASON1_CHECKED_AT)
            .remove(KEY_COMMUNITY_CACHE)
            .remove(KEY_PREDICTION_CACHE)
            .remove(KEY_SEASON1_ANALYSIS_CACHE)
            .apply()
    }

    /** Erase ALL stored data. Used by "Delete All Data" in Settings. */
    fun deleteAllData() {
        prefs.edit().clear().apply()
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

    // --- Season 1 ---

    fun getSeason1Result(): Season1Result? {
        val resultJson = prefs.getString(KEY_SEASON1_RESULT, null) ?: return null
        return try {
            json.decodeFromString<Season1Result>(resultJson)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSeason1Result(result: Season1Result) {
        prefs.edit()
            .putString(KEY_SEASON1_RESULT, json.encodeToString(result))
            .putLong(KEY_SEASON1_CHECKED_AT, System.currentTimeMillis())
            .apply()
    }

    fun shouldRecheckSeason1(): Boolean {
        val lastCheck = prefs.getLong(KEY_SEASON1_CHECKED_AT, 0)
        val elapsed = System.currentTimeMillis() - lastCheck
        return elapsed > AppConfig.Cache.SEASON1_CACHE_HOURS * 3600 * 1000
    }

    // --- Settings ---

    // Default to Helius for new installs. Users who have explicitly set "public" in
    // Settings will keep their choice (KEY_RPC_PROVIDER will already exist). This
    // avoids the public RPC's aggressive rate limit on getProgramAccounts, which was
    // causing stake queries to fail with HTTP 429 on fresh installs.
    fun getRpcProvider(): String = prefs.getString(KEY_RPC_PROVIDER, "helius") ?: "helius"

    fun setRpcProvider(provider: String) {
        prefs.edit().putString(KEY_RPC_PROVIDER, provider).apply()
    }

    fun isOptedIn(): Boolean = prefs.getBoolean(KEY_OPTED_IN, false)

    fun setOptedIn(optedIn: Boolean) {
        prefs.edit().putBoolean(KEY_OPTED_IN, optedIn).apply()
    }

    // --- Achievements ---

    fun getUnlockedAchievements(): Set<Achievement> {
        val names = prefs.getStringSet(KEY_ACHIEVEMENTS, emptySet()) ?: emptySet()
        return names.mapNotNull {
            try { Achievement.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }

    fun saveUnlockedAchievements(achievements: Set<Achievement>) {
        prefs.edit()
            .putStringSet(KEY_ACHIEVEMENTS, achievements.map { it.name }.toSet())
            .apply()
    }

    // --- Theme ---

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "dark") ?: "dark"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    // --- Haptics ---

    fun isHapticsEnabled(): Boolean = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    // --- Analytics ---

    fun isAnalyticsEnabled(): Boolean = prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)

    fun setAnalyticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, enabled).apply()
    }

    // --- Notifications ---

    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getLastNotifiedTier(): String? = prefs.getString(KEY_LAST_NOTIFIED_TIER, null)

    fun setLastNotifiedTier(tier: String) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_TIER, tier).apply()
    }

    // --- Leaderboard ---

    fun isLeaderboardOptedIn(): Boolean = prefs.getBoolean(KEY_LEADERBOARD_OPTED_IN, true)

    fun setLeaderboardOptedIn(optedIn: Boolean) {
        prefs.edit().putBoolean(KEY_LEADERBOARD_OPTED_IN, optedIn).apply()
    }

    // --- Staking History ---

    fun getSharePriceHistory(): List<SharePriceSnapshot> {
        val historyJson = prefs.getString(KEY_SHARE_PRICE_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SharePriceSnapshot>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save a share price snapshot. Deduplicates by day (keeps one per day).
     * Retains last 180 days of history.
     */
    fun saveSharePriceSnapshot(timestamp: Long, sharePrice: Long) {
        if (sharePrice <= 0) return
        val history = getSharePriceHistory().toMutableList()

        // Deduplicate: only one snapshot per calendar day
        val dayMs = 86_400_000L
        val todayStart = (timestamp / dayMs) * dayMs
        val alreadyHasToday = history.any { (it.timestamp / dayMs) * dayMs == todayStart }
        if (alreadyHasToday) return

        history.add(SharePriceSnapshot(timestamp, sharePrice))

        // Keep last 180 days
        val cutoff = timestamp - (180 * dayMs)
        val trimmed = history.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }

        prefs.edit()
            .putString(KEY_SHARE_PRICE_HISTORY, json.encodeToString(trimmed))
            .apply()
    }

    fun getYieldPeriod(): String = prefs.getString(KEY_YIELD_PERIOD, "ytd") ?: "ytd"

    fun setYieldPeriod(period: String) {
        prefs.edit().putString(KEY_YIELD_PERIOD, period).apply()
    }

    // --- Activity Flags ---

    fun setHasViewedCommunity(viewed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_VIEWED_COMMUNITY, viewed).apply()
    }

    fun hasViewedCommunity(): Boolean = prefs.getBoolean(KEY_HAS_VIEWED_COMMUNITY, false)

    fun setHasPrediction(has: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_PREDICTION, has).apply()
    }

    fun hasPrediction(): Boolean = prefs.getBoolean(KEY_HAS_PREDICTION, false)

    fun setHasSeason1Analysis(has: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEASON1_ANALYSIS, has).apply()
    }

    fun hasSeason1Analysis(): Boolean = prefs.getBoolean(KEY_HAS_SEASON1_ANALYSIS, false)

    // --- Portfolio Cache ---
    //
    // Cache is keyed per wallet address so switching wallets does not display
    // another wallet's balances. The legacy unkeyed entry (KEY_PORTFOLIO_CACHE)
    // is ignored on read and cleared on first per-wallet save, so upgraded
    // installs don't leak the previous session's numbers.

    private fun portfolioCacheKey(walletAddress: String) =
        "$KEY_PORTFOLIO_CACHE:$walletAddress"

    fun getPortfolioCache(walletAddress: String): PortfolioCache? {
        if (walletAddress.isEmpty()) return null
        val cacheJson = prefs.getString(portfolioCacheKey(walletAddress), null) ?: return null
        return try {
            json.decodeFromString<PortfolioCache>(cacheJson)
        } catch (e: Exception) {
            null
        }
    }

    fun savePortfolioCache(walletAddress: String, cache: PortfolioCache) {
        if (walletAddress.isEmpty()) return
        prefs.edit()
            .putString(portfolioCacheKey(walletAddress), json.encodeToString(cache))
            // One-shot cleanup of the legacy unkeyed cache from pre-fix installs.
            .remove(KEY_PORTFOLIO_CACHE)
            .apply()
    }

    // --- Community Cache ---

    fun getCommunityCache(): CommunityCache? {
        val cacheJson = prefs.getString(KEY_COMMUNITY_CACHE, null) ?: return null
        return try {
            json.decodeFromString<CommunityCache>(cacheJson)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCommunityCache(cache: CommunityCache) {
        prefs.edit()
            .putString(KEY_COMMUNITY_CACHE, json.encodeToString(cache))
            .apply()
    }

    // --- Prediction Cache (S2) ---

    fun getPredictionCache(): PredictionCache? {
        val cacheJson = prefs.getString(KEY_PREDICTION_CACHE, null) ?: return null
        return try {
            json.decodeFromString<PredictionCache>(cacheJson)
        } catch (e: Exception) {
            null
        }
    }

    fun savePredictionCache(cache: PredictionCache) {
        prefs.edit()
            .putString(KEY_PREDICTION_CACHE, json.encodeToString(cache))
            .apply()
    }

    // --- Season 1 Analysis Cache ---

    fun getSeason1AnalysisCache(): Season1AnalysisCache? {
        val cacheJson = prefs.getString(KEY_SEASON1_ANALYSIS_CACHE, null) ?: return null
        return try {
            json.decodeFromString<Season1AnalysisCache>(cacheJson)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSeason1AnalysisCache(cache: Season1AnalysisCache) {
        prefs.edit()
            .putString(KEY_SEASON1_ANALYSIS_CACHE, json.encodeToString(cache))
            .apply()
    }

    // --- Transaction History Cache ---

    /**
     * Save parsed transaction records (capped at 100). Used to populate
     * the "Recent Activity" card in Portfolio and as the source of truth
     * for the S1/S2 per-metric fleet comparison.
     */
    fun saveTransactionHistory(txs: List<TransactionRecord>) {
        if (txs.isEmpty()) return
        val capped = txs.take(100)
        prefs.edit()
            .putString(KEY_TX_HISTORY, json.encodeToString(capped))
            .apply()
    }

    fun getTransactionHistory(): List<TransactionRecord> {
        val historyJson = prefs.getString(KEY_TX_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<TransactionRecord>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Staking Tracking ---

    /** Timestamp when we first observed the user had an active stake. */
    fun getFirstStakedAt(): Long? {
        val ts = prefs.getLong(KEY_FIRST_STAKED_AT, 0L)
        return if (ts > 0) ts else null
    }

    fun setFirstStakedAt(timestamp: Long) {
        if (!prefs.contains(KEY_FIRST_STAKED_AT) || prefs.getLong(KEY_FIRST_STAKED_AT, 0L) == 0L) {
            prefs.edit().putLong(KEY_FIRST_STAKED_AT, timestamp).apply()
        }
    }

    // --- Prediction History ---

    fun getPredictionHistory(): List<PredictionHistoryEntry> {
        val historyJson = prefs.getString(KEY_PREDICTION_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PredictionHistoryEntry>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save a prediction history entry. Deduplicates by day (one per day).
     * Retains last 90 days of history.
     */
    fun savePredictionHistoryEntry(entry: PredictionHistoryEntry) {
        val history = getPredictionHistory().toMutableList()
        val dayMs = 86_400_000L
        val todayStart = (entry.timestamp / dayMs) * dayMs
        // Remove existing entry for today (replace with latest)
        history.removeAll { (it.timestamp / dayMs) * dayMs == todayStart }
        history.add(entry)
        // Keep last 90 days
        val cutoff = entry.timestamp - (90 * dayMs)
        val trimmed = history.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }
        prefs.edit()
            .putString(KEY_PREDICTION_HISTORY, json.encodeToString(trimmed))
            .apply()
    }

    // --- New Achievement Flags ---

    fun hasUsedSimulator(): Boolean = prefs.getBoolean(KEY_HAS_USED_SIMULATOR, false)
    fun setHasUsedSimulator(used: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_USED_SIMULATOR, used).apply()
    }

    fun hasViewedHistory(): Boolean = prefs.getBoolean(KEY_HAS_VIEWED_HISTORY, false)
    fun setHasViewedHistory(viewed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_VIEWED_HISTORY, viewed).apply()
    }

    fun hasTierUpgrade(): Boolean = prefs.getBoolean(KEY_HAS_TIER_UPGRADE, false)
    fun setHasTierUpgrade(upgraded: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_TIER_UPGRADE, upgraded).apply()
    }

    fun getPreviousTierName(): String? = prefs.getString(KEY_PREVIOUS_TIER, null)
    fun setPreviousTierName(tier: String) {
        prefs.edit().putString(KEY_PREVIOUS_TIER, tier).apply()
    }

    // --- Onboarding ---

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)

    // --- Raw JSON export/import (for device backup) ---

    fun getRawSeason1Result(): String? = prefs.getString(KEY_SEASON1_RESULT, null)
    fun getRawPredictionCache(): String? = prefs.getString(KEY_PREDICTION_CACHE, null)
    fun getRawSeason1AnalysisCache(): String? = prefs.getString(KEY_SEASON1_ANALYSIS_CACHE, null)
    fun getRawPredictionHistory(): String? = prefs.getString(KEY_PREDICTION_HISTORY, null)
    fun getRawTransactionHistory(): String? = prefs.getString(KEY_TX_HISTORY, null)

    /**
     * Restore prediction data from device backup.
     * Only writes non-null fields; preserves existing data for null fields.
     */
    fun restorePredictionData(
        season1ResultJson: String?,
        predictionCacheJson: String?,
        season1AnalysisCacheJson: String?,
        predictionHistoryJson: String?,
        transactionHistoryJson: String?
    ) {
        prefs.edit().apply {
            season1ResultJson?.let {
                putString(KEY_SEASON1_RESULT, it)
                putLong(KEY_SEASON1_CHECKED_AT, System.currentTimeMillis())
                putBoolean(KEY_HAS_SEASON1_ANALYSIS, true)
            }
            predictionCacheJson?.let {
                putString(KEY_PREDICTION_CACHE, it)
                putBoolean(KEY_HAS_PREDICTION, true)
            }
            season1AnalysisCacheJson?.let { putString(KEY_SEASON1_ANALYSIS_CACHE, it) }
            predictionHistoryJson?.let { putString(KEY_PREDICTION_HISTORY, it) }
            transactionHistoryJson?.let { putString(KEY_TX_HISTORY, it) }
        }.apply()
    }

    // --- On-Chain Check-In ---
    fun getLastOnChainCheckInSignature(): String? = prefs.getString(KEY_LAST_ONCHAIN_CHECKIN_SIG, null)
    fun getLastOnChainCheckInDate(): String? = prefs.getString(KEY_LAST_ONCHAIN_CHECKIN_DATE, null)
    fun setLastOnChainCheckIn(signature: String, date: String) {
        prefs.edit()
            .putString(KEY_LAST_ONCHAIN_CHECKIN_SIG, signature)
            .putString(KEY_LAST_ONCHAIN_CHECKIN_DATE, date)
            .apply()
    }
    fun setHasCompletedOnboarding(completed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
    }

    companion object {
        private const val PREFS_NAME = "seeker_verify_prefs"
        private const val PREFS_NAME_FALLBACK = "seeker_verify_prefs_plain"
        private const val KEY_WALLET_ADDRESS = "wallet_address"
        private const val KEY_WALLET_NAME = "wallet_name"
        private const val KEY_WALLET_CONNECTED_AT = "wallet_connected_at"
        private const val KEY_HAS_SGT = "has_sgt"
        private const val KEY_SGT_CHECKED_AT = "sgt_checked_at"
        private const val KEY_SGT_MEMBER_NUMBER = "sgt_member_number"
        private const val KEY_SGT_MINT_ADDRESS = "sgt_mint_address"
        private const val KEY_CHECK_IN_STREAK = "check_in_streak"
        private const val KEY_SEASON1_RESULT = "season1_result"
        private const val KEY_SEASON1_CHECKED_AT = "season1_checked_at"
        private const val KEY_RPC_PROVIDER = "rpc_provider"
        private const val KEY_OPTED_IN = "opted_in"
        private const val KEY_ACHIEVEMENTS = "achievements"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_LEADERBOARD_OPTED_IN = "leaderboard_opted_in"
        private const val KEY_HAS_VIEWED_COMMUNITY = "has_viewed_community"
        private const val KEY_HAS_PREDICTION = "has_prediction"
        private const val KEY_HAS_SEASON1_ANALYSIS = "has_season1_analysis"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_SHARE_PRICE_HISTORY = "share_price_history"
        private const val KEY_YIELD_PERIOD = "yield_period"
        private const val KEY_PORTFOLIO_CACHE = "portfolio_cache"
        private const val KEY_COMMUNITY_CACHE = "community_cache"
        private const val KEY_PREDICTION_CACHE = "prediction_cache"
        private const val KEY_SEASON1_ANALYSIS_CACHE = "season1_analysis_cache"
        private const val KEY_FIRST_STAKED_AT = "first_staked_at"
        private const val KEY_LAST_NOTIFIED_TIER = "last_notified_tier"
        private const val KEY_TX_HISTORY = "tx_history"
        private const val KEY_PREDICTION_HISTORY = "prediction_history"
        private const val KEY_HAS_USED_SIMULATOR = "has_used_simulator"
        private const val KEY_HAS_VIEWED_HISTORY = "has_viewed_history"
        private const val KEY_HAS_TIER_UPGRADE = "has_tier_upgrade"
        private const val KEY_PREVIOUS_TIER = "previous_tier"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_LAST_ONCHAIN_CHECKIN_SIG = "last_onchain_checkin_sig"
        private const val KEY_LAST_ONCHAIN_CHECKIN_DATE = "last_onchain_checkin_date"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
    }
}
