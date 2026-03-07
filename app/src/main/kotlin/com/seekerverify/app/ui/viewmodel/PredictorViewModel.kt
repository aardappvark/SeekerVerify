package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.data.CheckInBackupManager
import com.seekerverify.app.model.CachedHighlight
import com.seekerverify.app.model.PredictionCache
import com.seekerverify.app.model.PredictionHistoryEntry
import com.seekerverify.app.model.Season1AnalysisCache
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.engine.PredictorEngine
import com.seekerverify.app.engine.ProjectionEngine
import com.seekerverify.app.engine.Season1Engine
import com.seekerverify.app.engine.SeasonProgress
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.model.Season1Result
import com.seekerverify.app.model.SeasonComparison
import com.seekerverify.app.model.Trend
import com.seekerverify.app.rpc.ActivityRpcClient
import com.seekerverify.app.rpc.DomainRpcClient
import com.seekerverify.app.rpc.Season1RpcClient
import com.seekerverify.app.rpc.StakingRpcClient
import com.seekerverify.app.widget.SeekerWidgetProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PredictorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    // Season 2 state
    private val _result = MutableStateFlow<PredictorEngine.PredictorResult?>(null)
    val result: StateFlow<PredictorEngine.PredictorResult?> = _result.asStateFlow()

    private val _metrics = MutableStateFlow<PredictorEngine.ActivityMetrics?>(null)
    val metrics: StateFlow<PredictorEngine.ActivityMetrics?> = _metrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings.asStateFlow()

    // Season 1 state
    private val _season1Claim = MutableStateFlow<Season1RpcClient.Season1ClaimResult?>(null)
    val season1Claim: StateFlow<Season1RpcClient.Season1ClaimResult?> = _season1Claim.asStateFlow()

    private val _season1Analysis = MutableStateFlow<Season1Engine.Season1Analysis?>(null)
    val season1Analysis: StateFlow<Season1Engine.Season1Analysis?> = _season1Analysis.asStateFlow()

    private val _season1Loading = MutableStateFlow(false)
    val season1Loading: StateFlow<Boolean> = _season1Loading.asStateFlow()

    // Comparison
    private val _comparison = MutableStateFlow<SeasonComparison?>(null)
    val comparison: StateFlow<SeasonComparison?> = _comparison.asStateFlow()

    // Season progress + projection
    private val _seasonProgress = MutableStateFlow<SeasonProgress.Progress?>(null)
    val seasonProgress: StateFlow<SeasonProgress.Progress?> = _seasonProgress.asStateFlow()

    private val _projectedResult = MutableStateFlow<PredictorEngine.ProjectedPredictorResult?>(null)
    val projectedResult: StateFlow<PredictorEngine.ProjectedPredictorResult?> = _projectedResult.asStateFlow()

    private val _projectionAssumptions = MutableStateFlow<List<String>>(emptyList())
    val projectionAssumptions: StateFlow<List<String>> = _projectionAssumptions.asStateFlow()

    // Prediction history (must be before init block — Kotlin initializes in declaration order)
    private val _predictionHistory = MutableStateFlow<List<PredictionHistoryEntry>>(emptyList())
    val predictionHistory: StateFlow<List<PredictionHistoryEntry>> = _predictionHistory.asStateFlow()

    private val _isFreshPrediction = MutableStateFlow(false)
    val isFreshPrediction: StateFlow<Boolean> = _isFreshPrediction.asStateFlow()

    // Community ranking from real leaderboard data
    data class CommunityRank(
        val totalPredictors: Long,
        val topPercentile: Double, // e.g., 15.2 means "top 15.2%"
        val sameOrHigherCount: Long
    )
    private val _communityRank = MutableStateFlow<CommunityRank?>(null)
    val communityRank: StateFlow<CommunityRank?> = _communityRank.asStateFlow()

    init {
        // Calculate season progress immediately (no RPC needed)
        _seasonProgress.value = SeasonProgress.calculate()

        // Load cached Season 1 result
        prefs.getSeason1Result()?.let { cached ->
            val tier = cached.detectedTier?.let { AirdropTier.fromName(it) }
            _season1Claim.value = Season1RpcClient.Season1ClaimResult(
                tier = tier,
                claimSignature = cached.claimSignature,
                claimTimestamp = cached.claimTimestamp,
                rawAmount = cached.rawClaimAmount
            )
        }

        // Load cached Season 1 analysis (immutable — S1 data never changes)
        prefs.getSeason1AnalysisCache()?.let { cached ->
            val tier = cached.detectedTierName?.let { AirdropTier.fromName(it) }
            _season1Analysis.value = Season1Engine.Season1Analysis(
                detectedTier = tier,
                tierPercentile = cached.tierPercentile,
                overallActivityScore = cached.overallActivityScore,
                activityHighlights = cached.highlights.map {
                    Season1Engine.ActivityHighlight(it.label, it.description, it.isStrength)
                }
            )
        }

        // Load cached S2 prediction for instant display
        // If no cached data, try restoring from device backup (survives app uninstall)
        if (prefs.getPredictionCache() == null) {
            val walletAddr = prefs.getWalletAddress()
            if (walletAddr != null) {
                try {
                    val restored = CheckInBackupManager.restorePredictionData(
                        application, walletAddr
                    )
                    if (restored) {
                        Log.d(TAG, "Prediction data restored from device backup")
                    }
                } catch (_: Exception) { }
            }
        }
        loadCachedPrediction()

        // Load prediction history for trajectory chart
        loadPredictionHistory()
    }

    /**
     * Detect Season 1 tier and run activity analysis.
     */
    fun runSeason1Analysis(walletAddress: String, rpcUrl: String) {
        viewModelScope.launch {
            _season1Loading.value = true
            _error.value = null

            Log.d(TAG, "Running Season 1 analysis for ${walletAddress.take(8)}...")

            try {
                val partialWarnings = mutableListOf<String>()

                // Run claim detection, staking, and domain ALL in parallel for speed
                val claimDeferred = async {
                    Season1RpcClient.detectSeason1Tier(walletAddress, rpcUrl)
                }
                val stakingDeferred = async {
                    StakingRpcClient.getStakingInfo(walletAddress, rpcUrl)
                }
                val domainDeferred = async {
                    DomainRpcClient.getSkrDomains(walletAddress, rpcUrl)
                }

                // Resolve staking + domain (fast)
                var isStaked = false
                stakingDeferred.await().fold(
                    onSuccess = { info -> isStaked = info.isStaked },
                    onFailure = { partialWarnings.add("Staking data unavailable") }
                )

                var hasSkrDomain = false
                domainDeferred.await().fold(
                    onSuccess = { domains -> hasSkrDomain = domains.any { !it.isExpired } },
                    onFailure = { partialWarnings.add("Domain data unavailable") }
                )
                // SGT holders always have at least one .skr domain (assigned during Seeker setup)
                if (!hasSkrDomain && prefs.hasSgt()) {
                    hasSkrDomain = true
                }

                // Lite activity scan (3 batches = 3K sigs — S1 era only, before S2 launch)
                // periodEndEpoch = May 15 2025 (approx S2/SKR token launch) → 1747353600
                val activityDeferred = async {
                    ActivityRpcClient.getActivityMetrics(
                        walletAddress = walletAddress,
                        rpcUrl = rpcUrl,
                        isStaked = isStaked,
                        hasSkrDomain = hasSkrDomain,
                        maxBatches = 3,
                        periodEndEpoch = 1747353600L // S2 start (May 15, 2025 00:00 UTC)
                    )
                }

                // Wait for claim result
                val claimResult = claimDeferred.await()
                claimResult.fold(
                    onSuccess = { claim ->
                        _season1Claim.value = claim

                        if (partialWarnings.isNotEmpty()) {
                            _warnings.value = partialWarnings
                        }

                        val activityResult = activityDeferred.await()
                        val activityMetrics = activityResult.metrics

                        // Analyze in S1 context
                        val analysis = Season1Engine.analyze(activityMetrics, claim.tier)
                        _season1Analysis.value = analysis

                        // Cache full analysis (immutable)
                        saveSeason1AnalysisCache(analysis)

                        // Save device backup with S1 data
                        try {
                            CheckInBackupManager.savePredictionBackup(
                                getApplication(), walletAddress
                            )
                        } catch (_: Exception) { }

                        // Cache result
                        prefs.saveSeason1Result(Season1Result(
                            detectedTier = claim.tier?.displayName,
                            claimSignature = claim.claimSignature,
                            claimTimestamp = claim.claimTimestamp,
                            rawClaimAmount = claim.rawAmount,
                            activityScore = analysis.overallActivityScore,
                            highlights = analysis.activityHighlights.map { it.label },
                            detectedAt = System.currentTimeMillis()
                        ))

                        // Update comparison if S2 exists
                        updateComparison()

                        // Auto-refresh S2 prediction with newly detected S1 tier
                        if (_result.value != null && claim.tier != null) {
                            Log.d(TAG, "S1 tier detected, auto-refreshing S2 with ${claim.tier.displayName}")
                            refreshS2WithNewS1Tier()
                        }

                        Log.d(TAG, "Season 1 analysis complete: ${claim.tier?.displayName ?: "Unknown"}")
                    },
                    onFailure = { e ->
                        _error.value = "Season 1 detection failed: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Season 1 analysis failed: ${e.message}", e)
                _error.value = "Season 1 analysis failed: ${e.message}"
            }

            _season1Loading.value = false
        }
    }

    /**
     * Re-score S2 prediction with the newly detected S1 tier.
     * Uses existing metrics — no RPC re-scan needed.
     */
    private fun refreshS2WithNewS1Tier() {
        val existingMetrics = _metrics.value ?: return
        val s1Tier = _season1Claim.value?.tier ?: return

        val enrichedMetrics = existingMetrics.copy(season1Tier = s1Tier)
        _metrics.value = enrichedMetrics

        val progress = SeasonProgress.calculate()
        _seasonProgress.value = progress

        val projection = ProjectionEngine.project(enrichedMetrics, progress)
        _projectionAssumptions.value = projection.assumptions

        val projectedPrediction = PredictorEngine.predictWithProjection(
            currentMetrics = enrichedMetrics,
            projectedMetrics = projection.projectedMetrics,
            seasonFractionComplete = progress.fractionComplete,
            isSeasonReliable = projection.isReliable
        )
        _projectedResult.value = projectedPrediction
        _result.value = projectedPrediction.projected

        updateComparison()

        Log.d(TAG, "S2 auto-refreshed with S1 tier ${s1Tier.displayName} → " +
            "projected ${projectedPrediction.projected.predictedTier.displayName}")
    }

    /**
     * Run Season 2 prediction. Now injects cached Season 1 tier for full scoring.
     */
    fun runPrediction(walletAddress: String, rpcUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "Running prediction for ${walletAddress.take(8)}...")

            try {
                val partialWarnings = mutableListOf<String>()

                // Fetch staking and domain data in parallel
                val stakingDeferred = async {
                    StakingRpcClient.getStakingInfo(walletAddress, rpcUrl)
                }
                val domainDeferred = async {
                    DomainRpcClient.getSkrDomains(walletAddress, rpcUrl)
                }

                var isStaked = false
                var stakeDurationDays = 0
                stakingDeferred.await().fold(
                    onSuccess = { info ->
                        isStaked = info.isStaked
                        if (info.isStaked && info.sharePrice > 1_000_000_000L) {
                            val priceGrowth = info.sharePrice.toDouble() / 1_000_000_000.0
                            val dailyRate = kotlin.math.ln(1.0 + 0.207 / 365.0)
                            stakeDurationDays = (kotlin.math.ln(priceGrowth) / dailyRate).toInt()
                                .coerceIn(1, 365)
                        } else if (info.isStaked) {
                            stakeDurationDays = 30
                        }
                    },
                    onFailure = { partialWarnings.add("Staking data unavailable") }
                )

                var hasSkrDomain = false
                domainDeferred.await().fold(
                    onSuccess = { domains -> hasSkrDomain = domains.any { !it.isExpired } },
                    onFailure = { partialWarnings.add("Domain data unavailable") }
                )
                // SGT holders always have at least one .skr domain (assigned during Seeker setup)
                if (!hasSkrDomain && prefs.hasSgt()) {
                    hasSkrDomain = true
                }

                if (partialWarnings.isNotEmpty()) {
                    _warnings.value = partialWarnings
                }

                val activityResult = ActivityRpcClient.getActivityMetrics(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isStaked = isStaked,
                    hasSkrDomain = hasSkrDomain
                )
                val activityMetrics = activityResult.metrics

                // Save parsed transaction records for Portfolio "Recent Activity" card
                if (activityResult.recentTransactions.isNotEmpty()) {
                    try { prefs.saveTransactionHistory(activityResult.recentTransactions) } catch (_: Exception) { }
                }

                // Inject cached Season 1 tier and real staking duration
                val s1Tier = _season1Claim.value?.tier
                    ?: prefs.getSeason1Result()?.detectedTier?.let { AirdropTier.fromName(it) }
                val enrichedMetrics = activityMetrics.copy(
                    season1Tier = s1Tier,
                    stakingDurationDays = stakeDurationDays
                )

                _metrics.value = enrichedMetrics

                // Calculate season progress and projection
                val progress = SeasonProgress.calculate()
                _seasonProgress.value = progress

                val projection = ProjectionEngine.project(enrichedMetrics, progress)
                _projectionAssumptions.value = projection.assumptions

                // Run dual prediction (current + projected)
                val projectedPrediction = PredictorEngine.predictWithProjection(
                    currentMetrics = enrichedMetrics,
                    projectedMetrics = projection.projectedMetrics,
                    seasonFractionComplete = progress.fractionComplete,
                    isSeasonReliable = projection.isReliable
                )
                _projectedResult.value = projectedPrediction

                // Keep backward compat: _result holds the projected result (end-of-season prediction)
                _result.value = projectedPrediction.projected
                _isFreshPrediction.value = true

                // Update widget with predicted tier
                try {
                    val widgetPrefs = getApplication<Application>()
                        .getSharedPreferences("widget_data", android.content.Context.MODE_PRIVATE)
                    val existingSol = widgetPrefs.getString("widget_sol", "--") ?: "--"
                    val existingSkr = widgetPrefs.getString("widget_skr", "--") ?: "--"
                    SeekerWidgetProvider.writeWidgetData(
                        getApplication(),
                        tier = projectedPrediction.projected.predictedTier.displayName,
                        solBalance = existingSol,
                        skrBalance = existingSkr
                    )
                } catch (_: Exception) { }

                // Mark prediction activity flag
                prefs.setHasPrediction(true)

                // Track prediction tier result
                GeoAnalyticsService.track(GeoAnalyticsService.Events.PREDICTION_TIER_RESULT)

                // Submit to leaderboard if opted in
                if (prefs.isLeaderboardOptedIn()) {
                    GeoAnalyticsService.submitLeaderboard(
                        compositeScore = projectedPrediction.projected.compositeScore,
                        tierName = projectedPrediction.projected.predictedTier.displayName
                    )
                }

                // Fetch community ranking from real leaderboard data
                fetchCommunityRank(projectedPrediction.projected.predictedTier)

                updateComparison()

                // Save prediction cache for instant reload
                savePredictionCache()

                // Save device backup (survives app uninstall)
                try {
                    CheckInBackupManager.savePredictionBackup(
                        getApplication(), walletAddress
                    )
                } catch (_: Exception) { }

                // Save prediction history entry for trajectory chart
                savePredictionHistoryEntry(projectedPrediction.projected)

                // Track tier upgrade achievement
                trackTierUpgrade(projectedPrediction.projected.predictedTier)

                Log.d(TAG, "Prediction complete: current=${projectedPrediction.current.predictedTier.displayName}, " +
                    "projected=${projectedPrediction.projected.predictedTier.displayName}, " +
                    "pace=${projectedPrediction.paceStatus}")
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed: ${e.message}", e)
                _error.value = "Prediction failed: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    private fun loadCachedPrediction() {
        val cache = prefs.getPredictionCache() ?: return

        val predictedTier = AirdropTier.fromName(cache.predictedTierName) ?: return
        val mainResult = PredictorEngine.PredictorResult(
            compositeScore = cache.compositeScore,
            percentile = cache.percentile,
            predictedTier = predictedTier,
            confidence = cache.confidence,
            breakdown = cache.breakdown
        )
        _result.value = mainResult

        // Restore projected result if available
        if (cache.currentTierName != null && cache.projectedTierName != null) {
            val currentTier = AirdropTier.fromName(cache.currentTierName)
            val projectedTier = AirdropTier.fromName(cache.projectedTierName)
            if (currentTier != null && projectedTier != null) {
                val currentResult = PredictorEngine.PredictorResult(
                    compositeScore = cache.currentScore ?: cache.compositeScore,
                    percentile = cache.currentPercentile ?: cache.percentile,
                    predictedTier = currentTier,
                    confidence = cache.currentConfidence ?: cache.confidence,
                    breakdown = cache.currentBreakdown ?: cache.breakdown
                )
                val projResult = PredictorEngine.PredictorResult(
                    compositeScore = cache.projectedScore ?: cache.compositeScore,
                    percentile = cache.projectedPercentile ?: cache.percentile,
                    predictedTier = projectedTier,
                    confidence = cache.projectedConfidence ?: cache.confidence,
                    breakdown = cache.projectedBreakdown ?: cache.breakdown
                )
                val paceStatus = cache.paceStatus?.let {
                    try { PredictorEngine.PaceStatus.valueOf(it) } catch (_: Exception) { null }
                } ?: PredictorEngine.PaceStatus.TOO_EARLY

                _projectedResult.value = PredictorEngine.ProjectedPredictorResult(
                    current = currentResult,
                    projected = projResult,
                    paceStatus = paceStatus,
                    targetTierProgress = cache.targetTierProgress ?: 0.0
                )
            }
        }

        updateComparison()

        // Write cached tier to widget immediately so it's populated on app launch
        try {
            val tierName = (_projectedResult.value?.projected?.predictedTier
                ?: predictedTier).displayName
            val widgetPrefs = getApplication<Application>()
                .getSharedPreferences("widget_data", android.content.Context.MODE_PRIVATE)
            val existingSol = widgetPrefs.getString("widget_sol", "--") ?: "--"
            val existingSkr = widgetPrefs.getString("widget_skr", "--") ?: "--"
            SeekerWidgetProvider.writeWidgetData(
                getApplication(),
                tier = tierName,
                solBalance = existingSol,
                skrBalance = existingSkr
            )
        } catch (_: Exception) { }

        Log.w(TAG, "Prediction loaded from cache (age ${(System.currentTimeMillis() - cache.cachedAt) / 1000}s)")
    }

    private fun savePredictionCache() {
        try {
            val mainResult = _result.value ?: return
            val projected = _projectedResult.value
            val cache = PredictionCache(
                predictedTierName = mainResult.predictedTier.displayName,
                compositeScore = mainResult.compositeScore,
                percentile = mainResult.percentile,
                confidence = mainResult.confidence,
                breakdown = mainResult.breakdown,
                projectedTierName = projected?.projected?.predictedTier?.displayName,
                projectedScore = projected?.projected?.compositeScore,
                projectedPercentile = projected?.projected?.percentile,
                projectedConfidence = projected?.projected?.confidence,
                projectedBreakdown = projected?.projected?.breakdown,
                currentTierName = projected?.current?.predictedTier?.displayName,
                currentScore = projected?.current?.compositeScore,
                currentPercentile = projected?.current?.percentile,
                currentConfidence = projected?.current?.confidence,
                currentBreakdown = projected?.current?.breakdown,
                paceStatus = projected?.paceStatus?.name,
                targetTierProgress = projected?.targetTierProgress,
                cachedAt = System.currentTimeMillis()
            )
            prefs.savePredictionCache(cache)
        } catch (_: Exception) { }
    }

    private fun saveSeason1AnalysisCache(analysis: Season1Engine.Season1Analysis) {
        try {
            val cache = Season1AnalysisCache(
                detectedTierName = analysis.detectedTier?.displayName,
                tierPercentile = analysis.tierPercentile,
                overallActivityScore = analysis.overallActivityScore,
                highlights = analysis.activityHighlights.map {
                    CachedHighlight(it.label, it.description, it.isStrength)
                },
                cachedAt = System.currentTimeMillis()
            )
            prefs.saveSeason1AnalysisCache(cache)
        } catch (_: Exception) { }
    }

    fun clearFreshPrediction() {
        _isFreshPrediction.value = false
    }

    fun loadPredictionHistory() {
        _predictionHistory.value = prefs.getPredictionHistory()
    }

    private fun savePredictionHistoryEntry(result: PredictorEngine.PredictorResult) {
        try {
            val entry = PredictionHistoryEntry(
                timestamp = System.currentTimeMillis(),
                compositeScore = result.compositeScore,
                percentile = result.percentile,
                tierName = result.predictedTier.displayName
            )
            prefs.savePredictionHistoryEntry(entry)
            _predictionHistory.value = prefs.getPredictionHistory()
        } catch (_: Exception) { }
    }

    private fun trackTierUpgrade(newTier: AirdropTier) {
        val previousTierName = prefs.getPreviousTierName()
        prefs.setPreviousTierName(newTier.displayName)
        if (previousTierName != null && previousTierName != newTier.displayName) {
            val previousTier = AirdropTier.fromName(previousTierName) ?: return
            val prevIdx = DISPLAY_TIERS.indexOf(previousTier)
            val newIdx = DISPLAY_TIERS.indexOf(newTier)
            if (newIdx > prevIdx) {
                prefs.setHasTierUpgrade(true)
            }
        }
    }

    private fun fetchCommunityRank(userTier: AirdropTier) {
        viewModelScope.launch {
            try {
                val lb = GeoAnalyticsService.fetchLeaderboard(7) ?: return@launch
                if (lb.totalParticipants == 0L) return@launch

                val tierOrder = listOf("Sovereign", "Luminary", "Vanguard", "Prospector", "Scout")
                val userTierIdx = tierOrder.indexOf(userTier.displayName)
                if (userTierIdx < 0) return@launch

                // Count users in the same tier or higher (lower index = higher tier)
                val sameOrHigher = lb.tierDistribution
                    .filter { tierOrder.indexOf(it.tier) <= userTierIdx }
                    .sumOf { it.count }

                val topPct = (sameOrHigher.toDouble() / lb.totalParticipants) * 100.0

                _communityRank.value = CommunityRank(
                    totalPredictors = lb.totalParticipants,
                    topPercentile = topPct.coerceIn(0.1, 100.0),
                    sameOrHigherCount = sameOrHigher
                )
            } catch (_: Exception) { }
        }
    }

    private fun updateComparison() {
        val s1Tier = _season1Claim.value?.tier
        val s2Result = _result.value
        _comparison.value = if (s1Tier != null && s2Result != null) {
            computeComparison(s1Tier, s2Result)
        } else null
    }

    companion object {
        private const val TAG = "SeekerVerify"

        private val DISPLAY_TIERS = listOf(
            AirdropTier.SCOUT,
            AirdropTier.PROSPECTOR,
            AirdropTier.VANGUARD,
            AirdropTier.LUMINARY,
            AirdropTier.SOVEREIGN
        )

        // Season 1 percentile midpoints per tier
        private val TIER_PERCENTILE_MIDPOINTS = mapOf(
            AirdropTier.SCOUT to 9.75,
            AirdropTier.PROSPECTOR to 51.6,
            AirdropTier.VANGUARD to 89.65,
            AirdropTier.LUMINARY to 97.6,
            AirdropTier.SOVEREIGN to 99.8
        )

        fun computeComparison(
            season1Tier: AirdropTier,
            season2Result: PredictorEngine.PredictorResult
        ): SeasonComparison {
            val s1Idx = DISPLAY_TIERS.indexOf(season1Tier)
            val s2Idx = DISPLAY_TIERS.indexOf(season2Result.predictedTier)
            val tierShift = s2Idx - s1Idx

            val s1Percentile = TIER_PERCENTILE_MIDPOINTS[season1Tier] ?: 50.0
            val s2Percentile = season2Result.percentile

            val trend = when {
                tierShift > 0 -> Trend.UP
                tierShift < 0 -> Trend.DOWN
                else -> Trend.STABLE
            }

            val percentileShift = s2Percentile - s1Percentile

            val summary = when (trend) {
                Trend.UP -> "Trending up: ${season1Tier.displayName} \u2192 ${season2Result.predictedTier.displayName}"
                Trend.DOWN -> "Trending down: ${season1Tier.displayName} \u2192 ${season2Result.predictedTier.displayName}"
                Trend.STABLE -> "Holding steady at ${season1Tier.displayName}"
                Trend.UNKNOWN -> "Comparison unavailable"
            }

            return SeasonComparison(
                season1Tier = season1Tier,
                season2Tier = season2Result.predictedTier,
                season1Percentile = s1Percentile,
                season2Percentile = s2Percentile,
                trend = trend,
                percentileShift = percentileShift,
                tierShift = tierShift,
                summary = summary
            )
        }
    }
}
