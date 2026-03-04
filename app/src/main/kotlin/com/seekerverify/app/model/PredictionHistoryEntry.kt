package com.seekerverify.app.model

import kotlinx.serialization.Serializable

@Serializable
data class PredictionHistoryEntry(
    val timestamp: Long,
    val compositeScore: Double,
    val percentile: Double,
    val tierName: String
)
