package com.seekerverify.app.model

import kotlinx.serialization.Serializable

@Serializable
data class CheckInStreak(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCheckInDate: String? = null,  // ISO date "2026-02-12"
    val totalCheckIns: Int = 0
)
