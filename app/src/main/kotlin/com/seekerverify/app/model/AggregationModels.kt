package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * Anonymous prediction payload for future opt-in aggregation.
 * Privacy-preserving: no wallet address, only bucketed values.
 */
@Serializable
data class AnonymousPredictionPayload(
    val schemaVersion: Int = 1,
    val season1TierOrdinal: Int?,
    val season2TierOrdinal: Int?,
    val compositeScoreBucket: Int,
    val isStaked: Boolean,
    val hasSkrDomain: Boolean,
    val walletAgeBucket: Int,
    val txCountBucket: Int,
    val submittedAt: Long
)

/**
 * Aggregate stats from future backend.
 */
@Serializable
data class AggregateStats(
    val totalSubmissions: Int,
    val tierDistribution: Map<String, Int>,
    val medianCompositeScore: Double,
    val averageCompositeScore: Double,
    val stakedPercentage: Double,
    val averageWalletAgeBucket: Int,
    val updatedAt: Long
)

/**
 * Bucketing utilities for privacy-safe aggregation.
 */
object AggregationBuckets {
    fun bucketScore(score: Double): Int = ((score / 5.0).toInt() * 5).coerceIn(0, 100)

    fun bucketWalletAge(days: Int): Int = when {
        days <= 30 -> 0
        days <= 90 -> 1
        days <= 180 -> 2
        days <= 365 -> 3
        else -> 4
    }

    fun bucketTxCount(count: Int): Int = when {
        count <= 10 -> 0
        count <= 50 -> 1
        count <= 200 -> 2
        count <= 1000 -> 3
        else -> 4
    }
}
