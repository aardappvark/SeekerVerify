package com.seekerverify.app.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeoAnalyticsService — Fire-and-forget anonymous event tracking.
 *
 * PRIVACY ARCHITECTURE:
 * - App sends ONLY the event type (e.g., "app_open") to the Cloudflare Worker
 * - The Worker derives country/city from the request IP via Cloudflare's edge (request.cf)
 * - The IP address is NEVER stored — only aggregate counts by city/country/day
 * - Zero PII: no user ID, no device ID, no wallet address, no fingerprint
 * - Compliant with GDPR (Recital 26), CCPA, LGPD, APPI, PIPA without consent
 *
 * USAGE:
 *   GeoAnalyticsService.track("app_open")
 *   GeoAnalyticsService.track("wallet_connected")
 *   GeoAnalyticsService.track("sgt_verified")
 *
 * All calls are fire-and-forget on a background coroutine.
 * Failures are silently ignored — analytics must never crash the app.
 */
object GeoAnalyticsService {

    private const val TAG = "GeoAnalytics"

    private const val ENDPOINT = "https://aardappvark-analytics.aardappvark.workers.dev/track"

    // App-level API key — injected from local.properties via R.string.analytics_api_key
    private var apiKey: String = ""

    // Whether analytics are enabled
    private var enabled = true

    /**
     * Initialize with the API key from string resources.
     * Must be called before any tracking — typically in MainActivity.onCreate().
     */
    fun init(key: String) {
        apiKey = key
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Allowed event types — must match the ALLOWED_EVENTS list in the Worker
    object Events {
        const val APP_OPEN = "app_open"
        const val WALLET_CONNECTED = "wallet_connected"
        const val WALLET_DISCONNECTED = "wallet_disconnected"
        const val SGT_VERIFIED = "sgt_verified"
        const val SGT_NOT_FOUND = "sgt_not_found"
        const val CHECK_IN = "check_in"
        const val PREDICTION_RUN = "prediction_run"
        const val SEASON1_ANALYZED = "season1_analyzed"
        const val PORTFOLIO_VIEWED = "portfolio_viewed"
        const val COMMUNITY_VIEWED = "community_viewed"
        const val SETTINGS_OPENED = "settings_opened"
        const val GUEST_MODE_ENTERED = "guest_mode_entered"
        const val PREDICTION_TIER_RESULT = "prediction_tier_result"
        const val ONCHAIN_CHECKIN = "onchain_checkin"
        const val SIMULATOR_USED = "simulator_used"
        const val ONBOARDING_COMPLETED = "onboarding_completed"
    }

    /**
     * Track an event. Fire-and-forget — never blocks, never throws.
     * The Cloudflare Worker derives geo from the request IP server-side.
     */
    fun track(eventType: String) {
        if (!enabled) return
        if (apiKey.isEmpty()) return // Not initialized — key not injected yet

        scope.launch {
            try {
                val url = URL(ENDPOINT)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-API-Key", apiKey)
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true

                val body = """{"event_type":"$eventType"}"""

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode != 200) {
                    Log.d(TAG, "Analytics event '$eventType' failed: HTTP $responseCode")
                }
            } catch (e: Exception) {
                // Silently fail — analytics must never crash the app
                Log.d(TAG, "Analytics event '$eventType' failed: ${e.message}")
            }
        }
    }

    /**
     * Fetch aggregate analytics stats from the Worker.
     * Returns null if not configured or on error.
     */
    suspend fun fetchStats(periodDays: Int = 30): AnalyticsStats? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext null

        try {
            val baseUrl = ENDPOINT.removeSuffix("/track").removeSuffix("/")
            val url = URL("$baseUrl/stats?period=$periodDays")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-API-Key", apiKey)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.d(TAG, "Stats fetch failed: HTTP $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            parseStats(response)
        } catch (e: Exception) {
            Log.d(TAG, "Stats fetch failed: ${e.message}")
            null
        }
    }

    private fun parseStats(json: String): AnalyticsStats {
        val obj = JSONObject(json)

        val eventTotals = mutableMapOf<String, Long>()
        val eventArray = obj.optJSONArray("event_totals")
        if (eventArray != null) {
            for (i in 0 until eventArray.length()) {
                val item = eventArray.getJSONObject(i)
                eventTotals[item.getString("event_type")] = item.getLong("total")
            }
        }

        val countries = mutableListOf<CountryStats>()
        val countryArray = obj.optJSONArray("countries")
        if (countryArray != null) {
            for (i in 0 until countryArray.length()) {
                val item = countryArray.getJSONObject(i)
                countries.add(CountryStats(
                    country = item.getString("country"),
                    total = item.getLong("total")
                ))
            }
        }

        val topCities = mutableListOf<CityStats>()
        val cityArray = obj.optJSONArray("top_cities")
        if (cityArray != null) {
            for (i in 0 until cityArray.length()) {
                val item = cityArray.getJSONObject(i)
                topCities.add(CityStats(
                    country = item.getString("country"),
                    city = item.getString("city"),
                    total = item.getLong("total")
                ))
            }
        }

        return AnalyticsStats(
            periodDays = obj.optInt("period_days", 30),
            eventTotals = eventTotals,
            countries = countries,
            topCities = topCities
        )
    }

    /**
     * Submit anonymous leaderboard entry (bucketed score + tier only).
     */
    fun submitLeaderboard(compositeScore: Double, tierName: String) {
        if (!enabled) return
        if (apiKey.isEmpty()) return

        val bucket = when {
            compositeScore >= 80 -> "80-100"
            compositeScore >= 60 -> "60-79"
            compositeScore >= 40 -> "40-59"
            compositeScore >= 20 -> "20-39"
            else -> "0-19"
        }

        scope.launch {
            try {
                val baseUrl = ENDPOINT.removeSuffix("/track").removeSuffix("/")
                val url = URL("$baseUrl/leaderboard/submit")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-API-Key", apiKey)
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true

                val body = """{"score_bucket":"$bucket","tier":"$tierName"}"""
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode != 200) {
                    Log.d(TAG, "Leaderboard submit failed: HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Leaderboard submit failed: ${e.message}")
            }
        }
    }

    /**
     * Fetch leaderboard data.
     */
    suspend fun fetchLeaderboard(periodDays: Int = 7): LeaderboardData? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext null

        try {
            val baseUrl = ENDPOINT.removeSuffix("/track").removeSuffix("/")
            val url = URL("$baseUrl/leaderboard?period=$periodDays")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode != 200) return@withContext null

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            parseLeaderboard(response)
        } catch (e: Exception) {
            Log.d(TAG, "Leaderboard fetch failed: ${e.message}")
            null
        }
    }

    private fun parseLeaderboard(json: String): LeaderboardData {
        val obj = JSONObject(json)

        val tiers = mutableListOf<LeaderboardTierEntry>()
        val tierArray = obj.optJSONArray("tier_distribution")
        if (tierArray != null) {
            for (i in 0 until tierArray.length()) {
                val item = tierArray.getJSONObject(i)
                tiers.add(LeaderboardTierEntry(
                    tier = item.getString("tier"),
                    count = item.getLong("total")
                ))
            }
        }

        val scores = mutableListOf<LeaderboardScoreEntry>()
        val scoreArray = obj.optJSONArray("score_distribution")
        if (scoreArray != null) {
            for (i in 0 until scoreArray.length()) {
                val item = scoreArray.getJSONObject(i)
                scores.add(LeaderboardScoreEntry(
                    scoreBucket = item.getString("score_bucket"),
                    tier = item.getString("tier"),
                    count = item.getLong("total")
                ))
            }
        }

        val countries = mutableListOf<LeaderboardCountryEntry>()
        val countryArray = obj.optJSONArray("country_leaders")
        if (countryArray != null) {
            for (i in 0 until countryArray.length()) {
                val item = countryArray.getJSONObject(i)
                countries.add(LeaderboardCountryEntry(
                    country = item.getString("country"),
                    tier = item.getString("tier"),
                    count = item.getLong("total")
                ))
            }
        }

        return LeaderboardData(
            tierDistribution = tiers,
            scoreDistribution = scores,
            countryLeaders = countries
        )
    }

    /**
     * Disable analytics (e.g., for testing or if user opts out)
     */
    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }
}

data class AnalyticsStats(
    val periodDays: Int,
    val eventTotals: Map<String, Long>,
    val countries: List<CountryStats>,
    val topCities: List<CityStats>
) {
    val totalEvents: Long get() = eventTotals.values.sum()
    val totalCountries: Int get() = countries.size
    val totalAppOpens: Long get() = eventTotals["app_open"] ?: 0
    val totalWalletConnects: Long get() = eventTotals["wallet_connected"] ?: 0
    val totalSgtVerified: Long get() = eventTotals["sgt_verified"] ?: 0
}

data class CountryStats(
    val country: String,
    val total: Long
)

data class CityStats(
    val country: String,
    val city: String,
    val total: Long
)

data class LeaderboardData(
    val tierDistribution: List<LeaderboardTierEntry>,
    val scoreDistribution: List<LeaderboardScoreEntry>,
    val countryLeaders: List<LeaderboardCountryEntry>
) {
    val totalParticipants: Long get() = tierDistribution.sumOf { it.count }
}

data class LeaderboardTierEntry(
    val tier: String,
    val count: Long
)

data class LeaderboardScoreEntry(
    val scoreBucket: String,
    val tier: String,
    val count: Long
)

data class LeaderboardCountryEntry(
    val country: String,
    val tier: String,
    val count: Long
)
