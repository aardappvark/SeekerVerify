package com.seekerverify.app.engine

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Projects current on-chain activity metrics to end-of-season values.
 *
 * Time-dependent metrics (transactions, dApp interactions, staking duration,
 * wallet age) are linearly extrapolated. Static metrics (booleans, snapshots)
 * are held constant.
 *
 * Safety: projection scale factor is capped at 10x. Below 10% season
 * progress, projections are flagged as unreliable.
 */
object ProjectionEngine {

    private const val MAX_SCALE_FACTOR = 10.0
    private const val MIN_RELIABLE_FRACTION = 0.10  // 10%

    data class ProjectionResult(
        val currentMetrics: PredictorEngine.ActivityMetrics,
        val projectedMetrics: PredictorEngine.ActivityMetrics,
        val projectionFactor: Double,
        val isReliable: Boolean,
        val assumptions: List<String>
    )

    /**
     * Project current metrics to end-of-season values.
     */
    fun project(
        metrics: PredictorEngine.ActivityMetrics,
        progress: SeasonProgress.Progress
    ): ProjectionResult {
        val fraction = progress.fractionComplete
        val isReliable = fraction >= MIN_RELIABLE_FRACTION &&
            progress.phase == SeasonProgress.Phase.ACTIVE

        // Scale factor: 1/fraction, capped at MAX_SCALE_FACTOR
        val scaleFactor = if (fraction > 0.0) {
            min(1.0 / fraction, MAX_SCALE_FACTOR)
        } else {
            MAX_SCALE_FACTOR
        }

        // For post-season, no projection needed — current IS final
        val effectiveScale = when (progress.phase) {
            SeasonProgress.Phase.POST_SEASON -> 1.0
            else -> scaleFactor
        }

        // Project time-dependent metrics
        val projectedTx = (metrics.totalTransactions * effectiveScale).toInt()
        val projectedDapp = (metrics.dappInteractions * effectiveScale).toInt()
        val projectedStakeDays = when (progress.phase) {
            SeasonProgress.Phase.POST_SEASON -> metrics.stakingDurationDays
            else -> metrics.stakingDurationDays + progress.daysRemaining.toInt()
        }
        val projectedWalletAge = when (progress.phase) {
            SeasonProgress.Phase.POST_SEASON -> metrics.walletAgeDays
            else -> metrics.walletAgeDays + progress.daysRemaining.toInt()
        }

        // Programs estimate scales with sqrt of projected TX count
        val projectedPrograms = sqrt(projectedTx.toDouble()).toInt().coerceIn(0, 50)

        val projectedMetrics = metrics.copy(
            totalTransactions = projectedTx,
            uniquePrograms = projectedPrograms,
            dappInteractions = projectedDapp,
            stakingDurationDays = projectedStakeDays,
            walletAgeDays = projectedWalletAge
            // Static metrics unchanged: tokenDiversity, skrStaked, hasSkrDomain,
            // nftCount, season1Tier
        )

        val assumptions = buildList {
            add("Season start: ${progress.startDate} (assumed)")
            add("Season end: ${progress.endDate} (assumed)")
            add("Season progress: ${String.format("%.0f", progress.percentComplete)}%")
            add("Projection method: Linear extrapolation of time-dependent metrics")
            add("Projection factor: ${String.format("%.1f", effectiveScale)}x")
            add("Projected metrics: Transactions, dApp interactions, staking duration, wallet age")
            add("Static metrics (unchanged): Token diversity, NFTs, SKR staking status, .skr domain, Season 1 tier")
            if (effectiveScale >= MAX_SCALE_FACTOR) {
                add("Scale factor capped at ${MAX_SCALE_FACTOR.toInt()}x for early-season safety")
            }
            if (!isReliable) {
                add("Warning: Less than 10% of season complete — projections may be unreliable")
            }
        }

        return ProjectionResult(
            currentMetrics = metrics,
            projectedMetrics = projectedMetrics,
            projectionFactor = effectiveScale,
            isReliable = isReliable,
            assumptions = assumptions
        )
    }
}
