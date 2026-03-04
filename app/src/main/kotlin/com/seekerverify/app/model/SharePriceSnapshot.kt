package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * A point-in-time snapshot of the SKR staking share price.
 * Saved once per day to track yield over time.
 */
@Serializable
data class SharePriceSnapshot(
    val timestamp: Long,    // epoch millis
    val sharePrice: Long    // raw share price (u128-as-Long, precision 1e9)
)
