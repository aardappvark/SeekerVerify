package com.seekerverify.app.engine

import com.seekerverify.app.model.Achievement
import com.seekerverify.app.model.AirdropTier

/**
 * Evaluates which achievements have been unlocked based on current app state.
 * Call after check-in, portfolio load, prediction run, etc.
 */
object AchievementEngine {

    data class AchievementState(
        val totalCheckIns: Int = 0,
        val currentStreak: Int = 0,
        val isStaking: Boolean = false,
        val skrBalance: Double = 0.0,
        val hasDomain: Boolean = false,
        val hasPrediction: Boolean = false,
        val hasViewedCommunity: Boolean = false,
        val predictedTier: AirdropTier? = null,
        val hasSeason1Analysis: Boolean = false,
        val hasUsedSimulator: Boolean = false,
        val hasViewedHistory: Boolean = false,
        val hasTierUpgrade: Boolean = false
    )

    fun evaluate(state: AchievementState): Set<Achievement> {
        val unlocked = mutableSetOf<Achievement>()

        if (state.totalCheckIns >= 1) unlocked.add(Achievement.FIRST_CHECK_IN)
        if (state.currentStreak >= 7) unlocked.add(Achievement.STREAK_7)
        if (state.currentStreak >= 30) unlocked.add(Achievement.STREAK_30)
        if (state.currentStreak >= 90) unlocked.add(Achievement.STREAK_90)
        if (state.isStaking) unlocked.add(Achievement.STAKER)
        if (state.skrBalance >= 10_000) unlocked.add(Achievement.DIAMOND_HANDS)
        if (state.hasDomain) unlocked.add(Achievement.DOMAIN_OWNER)
        if (state.hasPrediction) unlocked.add(Achievement.PREDICTION_RUN)
        if (state.hasViewedCommunity) unlocked.add(Achievement.COMMUNITY_EXPLORER)
        if (state.hasSeason1Analysis) unlocked.add(Achievement.SEASON1_ANALYZED)
        if (state.hasUsedSimulator) unlocked.add(Achievement.SIMULATOR_USED)
        if (state.hasViewedHistory) unlocked.add(Achievement.HISTORY_CHECKED)
        if (state.hasTierUpgrade) unlocked.add(Achievement.TIER_UPGRADED)

        state.predictedTier?.let { tier ->
            if (tier == AirdropTier.VANGUARD || tier == AirdropTier.LUMINARY || tier == AirdropTier.SOVEREIGN) {
                unlocked.add(Achievement.VANGUARD_OR_ABOVE)
            }
        }

        return unlocked
    }
}
