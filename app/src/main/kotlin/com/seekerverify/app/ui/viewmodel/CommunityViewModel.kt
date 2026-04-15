package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.model.CommunityCache
import com.seekerverify.app.rpc.CommunityRpcClient
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.service.LeaderboardData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private val _totalSeekers = MutableStateFlow(0L)
    val totalSeekers: StateFlow<Long> = _totalSeekers.asStateFlow()

    private val _userPosition = MutableStateFlow<Long?>(null)
    val userPosition: StateFlow<Long?> = _userPosition.asStateFlow()

    private val _percentile = MutableStateFlow<Double?>(null)
    val percentile: StateFlow<Double?> = _percentile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Staking stats
    private val _activeStakers = MutableStateFlow<Int?>(null)
    val activeStakers: StateFlow<Int?> = _activeStakers.asStateFlow()

    private val _totalStakedDisplay = MutableStateFlow<Double?>(null)
    val totalStakedDisplay: StateFlow<Double?> = _totalStakedDisplay.asStateFlow()

    private val _stakingParticipation = MutableStateFlow<Double?>(null)
    val stakingParticipation: StateFlow<Double?> = _stakingParticipation.asStateFlow()

    // Leaderboard
    private val _leaderboard = MutableStateFlow<LeaderboardData?>(null)
    val leaderboard: StateFlow<LeaderboardData?> = _leaderboard.asStateFlow()

    // Fleet Mode — statistical mode (most common staked SKR amount)
    private val _fleetModeSkr = MutableStateFlow<Double?>(null)
    val fleetModeSkr: StateFlow<Double?> = _fleetModeSkr.asStateFlow()

    // User's own staked SKR (sourced from cached portfolio for comparison)
    private val _userStakedSkr = MutableStateFlow(0.0)
    val userStakedSkr: StateFlow<Double> = _userStakedSkr.asStateFlow()

    /**
     * Load cached community data instantly from device storage.
     * Called before the network fetch to show data immediately.
     */
    fun loadCachedCommunity() {
        val cache = prefs.getCommunityCache() ?: return

        _totalSeekers.value = cache.totalSeekers
        _activeStakers.value = cache.activeStakers
        _totalStakedDisplay.value = cache.totalStakedDisplay
        _stakingParticipation.value = cache.stakingParticipation
        _userStakedSkr.value = (prefs.getWalletAddress()?.let { prefs.getPortfolioCache(it) }?.stakedSkr ?: 0.0)

        // Recalculate percentile from cached data
        val memberNumber = prefs.getMemberNumber()
        _userPosition.value = memberNumber
        memberNumber?.let { num ->
            if (cache.totalSeekers > 0) {
                val pct = (1.0 - (num.toDouble() / cache.totalSeekers)) * 100
                _percentile.value = pct.coerceIn(0.0, 100.0)
            }
        }

        Log.w(TAG, "Community loaded from cache (age ${(System.currentTimeMillis() - cache.cachedAt) / 1000}s)")
    }

    fun loadCommunity(walletAddress: String, rpcUrl: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true

            val memberNumber = prefs.getMemberNumber()
            _userPosition.value = memberNumber

            CommunityRpcClient.getCommunityStats(memberNumber, rpcUrl).fold(
                onSuccess = { stats ->
                    _totalSeekers.value = stats.totalSeekers

                    // Calculate percentile (lower member number = earlier adopter)
                    memberNumber?.let { num ->
                        if (stats.totalSeekers > 0) {
                            val pct = (1.0 - (num.toDouble() / stats.totalSeekers)) * 100
                            _percentile.value = pct.coerceIn(0.0, 100.0)
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Community load failed: ${e.message}")
                }
            )

            // Fetch real staking stats from chain (includes fleet mode computation)
            CommunityRpcClient.getStakingStats(rpcUrl).fold(
                onSuccess = { stats ->
                    _activeStakers.value = stats.activeStakers
                    _totalStakedDisplay.value = stats.totalStakedDisplay
                    _stakingParticipation.value = stats.stakingParticipation
                    _fleetModeSkr.value = stats.fleetModeSkr
                },
                onFailure = { e ->
                    Log.e(TAG, "Staking stats load failed: ${e.message}")
                }
            )

            // Fetch leaderboard data
            try {
                val lb = GeoAnalyticsService.fetchLeaderboard(7)
                _leaderboard.value = lb
            } catch (e: Exception) {
                Log.e(TAG, "Leaderboard fetch failed: ${e.message}")
            }

            // Note: previously also called GeoAnalyticsService.fetchStats(30)
            // to populate an in-app "dApp Activity" card, but that card was
            // removed on 2026-04-15 to keep the Community screen focused on
            // fleet position and to preserve the app's privacy-first messaging.
            // Tracking itself still fires everywhere — GeoAnalyticsService.track()
            // calls are unchanged — so the /public-stats endpoint continues to
            // serve fresh numbers to the Builder Grant deck.

            _userStakedSkr.value = (prefs.getWalletAddress()?.let { prefs.getPortfolioCache(it) }?.stakedSkr ?: 0.0)

            _isLoading.value = false

            // Save community cache for instant reload
            saveCommunityCache()
        }
    }

    private fun saveCommunityCache() {
        try {
            val cache = CommunityCache(
                totalSeekers = _totalSeekers.value,
                activeStakers = _activeStakers.value,
                totalStakedDisplay = _totalStakedDisplay.value,
                stakingParticipation = _stakingParticipation.value,
                cachedAt = System.currentTimeMillis()
            )
            prefs.saveCommunityCache(cache)
        } catch (_: Exception) { }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
