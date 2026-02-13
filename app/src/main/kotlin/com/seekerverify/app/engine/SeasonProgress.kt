package com.seekerverify.app.engine

import com.seekerverify.app.AppConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Calculates season progress based on assumed start/end dates.
 *
 * Season dates are assumptions — actual Season 2 dates have not been
 * announced. All projections derived from this progress are clearly
 * labeled as speculative in the UI.
 */
object SeasonProgress {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

    data class Progress(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val currentDate: LocalDate,
        val daysSinceStart: Long,
        val daysRemaining: Long,
        val totalDays: Long,
        val fractionComplete: Double,   // 0.0 to 1.0
        val percentComplete: Double,    // 0 to 100
        val phase: Phase
    )

    enum class Phase {
        PRE_SEASON,
        ACTIVE,
        POST_SEASON
    }

    /**
     * Calculate current season progress.
     */
    fun calculate(now: LocalDate = LocalDate.now()): Progress {
        val start = LocalDate.parse(AppConfig.Season2.ASSUMED_START, DATE_FORMAT)
        val end = LocalDate.parse(AppConfig.Season2.ASSUMED_END, DATE_FORMAT)
        val totalDays = ChronoUnit.DAYS.between(start, end)

        val daysSinceStart = ChronoUnit.DAYS.between(start, now).coerceAtLeast(0)
        val daysRemaining = ChronoUnit.DAYS.between(now, end).coerceAtLeast(0)

        val phase = when {
            now.isBefore(start) -> Phase.PRE_SEASON
            now.isAfter(end) -> Phase.POST_SEASON
            else -> Phase.ACTIVE
        }

        val fraction = when (phase) {
            Phase.PRE_SEASON -> 0.0
            Phase.POST_SEASON -> 1.0
            Phase.ACTIVE -> (daysSinceStart.toDouble() / totalDays.toDouble()).coerceIn(0.0, 1.0)
        }

        return Progress(
            startDate = start,
            endDate = end,
            currentDate = now,
            daysSinceStart = daysSinceStart,
            daysRemaining = daysRemaining,
            totalDays = totalDays,
            fractionComplete = fraction,
            percentComplete = fraction * 100.0,
            phase = phase
        )
    }
}
