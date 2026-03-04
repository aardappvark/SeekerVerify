package com.seekerverify.app.engine

import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.model.Achievement

/**
 * Computes a 0-100 "Seeker Health Score" that measures app engagement.
 * All data comes from AppPreferences — no RPC calls needed.
 */
object HealthScoreEngine {

    data class HealthResult(
        val totalScore: Int,
        val components: List<HealthComponent>
    )

    data class HealthComponent(
        val label: String,
        val points: Int,
        val maxPoints: Int,
        val completed: Boolean
    )

    fun compute(prefs: AppPreferences, hasDomain: Boolean = false): HealthResult {
        val streak = prefs.getCheckInStreak()
        val achievements = prefs.getUnlockedAchievements()
        val totalAchievements = Achievement.entries.size

        val components = mutableListOf<HealthComponent>()

        // Check-in streak (0-30 pts)
        val streakPts = ((streak.currentStreak.coerceAtMost(30) / 30.0) * 30).toInt()
        components.add(HealthComponent("Check-in Streak", streakPts, 30, streak.currentStreak >= 30))

        // SGT verified (15 pts)
        val sgtPts = if (prefs.hasSgt()) 15 else 0
        components.add(HealthComponent("SGT Verified", sgtPts, 15, prefs.hasSgt()))

        // .skr domain (10 pts)
        val domainPts = if (hasDomain) 10 else 0
        components.add(HealthComponent(".skr Domain", domainPts, 10, hasDomain))

        // Has prediction (10 pts)
        val predPts = if (prefs.hasPrediction()) 10 else 0
        components.add(HealthComponent("Prediction Run", predPts, 10, prefs.hasPrediction()))

        // S1 analysis (10 pts)
        val s1Pts = if (prefs.hasSeason1Analysis()) 10 else 0
        components.add(HealthComponent("S1 Analysis", s1Pts, 10, prefs.hasSeason1Analysis()))

        // Achievement progress (0-15 pts)
        val achPts = if (totalAchievements > 0) ((achievements.size.toDouble() / totalAchievements) * 15).toInt() else 0
        components.add(HealthComponent("Achievements", achPts, 15, achievements.size == totalAchievements))

        // Simulator used (5 pts)
        val simPts = if (prefs.hasUsedSimulator()) 5 else 0
        components.add(HealthComponent("Simulator Used", simPts, 5, prefs.hasUsedSimulator()))

        // History viewed (5 pts)
        val histPts = if (prefs.hasViewedHistory()) 5 else 0
        components.add(HealthComponent("History Viewed", histPts, 5, prefs.hasViewedHistory()))

        val total = (streakPts + sgtPts + domainPts + predPts + s1Pts + achPts + simPts + histPts).coerceAtMost(100)

        return HealthResult(total, components)
    }
}
