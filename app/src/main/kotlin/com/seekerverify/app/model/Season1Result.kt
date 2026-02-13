package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * Cached Season 1 detection + analysis result.
 */
@Serializable
data class Season1Result(
    val detectedTier: String?,
    val claimSignature: String?,
    val claimTimestamp: Long?,
    val rawClaimAmount: Long?,
    val activityScore: Double,
    val highlights: List<String>,
    val detectedAt: Long
)

/**
 * Season 1 vs Season 2 comparison.
 */
data class SeasonComparison(
    val season1Tier: AirdropTier?,
    val season2Tier: AirdropTier?,
    val season1Percentile: Double,
    val season2Percentile: Double,
    val trend: Trend,
    val percentileShift: Double,
    val tierShift: Int,
    val summary: String
)

enum class Trend { UP, DOWN, STABLE, UNKNOWN }
