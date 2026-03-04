package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * Cached snapshot of community stats for instant loading on screen open.
 * Refreshed on each successful community data fetch.
 */
@Serializable
data class CommunityCache(
    val totalSeekers: Long,
    val activeStakers: Int?,
    val totalStakedDisplay: Double?,
    val stakingParticipation: Double?,
    val cachedAt: Long       // epoch millis
)
