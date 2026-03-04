package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * Cached S2 prediction result for instant loading on screen open.
 * Refreshed on each successful prediction run.
 */
@Serializable
data class PredictionCache(
    val predictedTierName: String,
    val compositeScore: Double,
    val percentile: Double,
    val confidence: String,
    val breakdown: Map<String, Double>,
    // Projected (end-of-season) values
    val projectedTierName: String?,
    val projectedScore: Double?,
    val projectedPercentile: Double?,
    val projectedConfidence: String?,
    val projectedBreakdown: Map<String, Double>?,
    // Current (season-to-date) values
    val currentTierName: String?,
    val currentScore: Double?,
    val currentPercentile: Double?,
    val currentConfidence: String?,
    val currentBreakdown: Map<String, Double>?,
    val paceStatus: String?,
    val targetTierProgress: Double?,
    val cachedAt: Long
)

/**
 * Cached S1 analysis highlights for instant display.
 * Immutable once written (S1 data never changes).
 */
@Serializable
data class CachedHighlight(
    val label: String,
    val description: String,
    val isStrength: Boolean
)

@Serializable
data class Season1AnalysisCache(
    val detectedTierName: String?,
    val tierPercentile: Double,
    val overallActivityScore: Double,
    val highlights: List<CachedHighlight>,
    val cachedAt: Long
)
