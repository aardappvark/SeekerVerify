package com.seekerverify.app.engine

import android.util.Log
import com.seekerverify.app.model.AirdropTier
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * On-device Season 2 airdrop predictor.
 * Uses weighted on-chain activity metrics to compute a composite score,
 * maps it to a percentile, and predicts the airdrop tier.
 *
 * No ML, no server — pure deterministic scoring.
 */
object PredictorEngine {

    private const val TAG = "SeekerVerify"

    /**
     * Raw activity metrics gathered from on-chain data.
     */
    data class ActivityMetrics(
        val totalTransactions: Int = 0,
        val uniquePrograms: Int = 0,
        val tokenDiversity: Int = 0,       // unique tokens held/traded
        val stakingDurationDays: Int = 0,
        val skrStaked: Boolean = false,
        val hasSkrDomain: Boolean = false,
        val nftCount: Int = 0,
        val walletAgeDays: Int = 0,
        val dappInteractions: Int = 0,
        val season1Tier: AirdropTier? = null
    )

    /**
     * Prediction result.
     */
    data class PredictorResult(
        val compositeScore: Double,     // 0-100
        val percentile: Double,         // 0-100 (where in the fleet you rank)
        val predictedTier: AirdropTier,
        val confidence: String,         // "Low", "Medium", "High"
        val breakdown: Map<String, Double>  // score per metric
    )

    /**
     * Projected prediction result — wraps current + projected predictions
     * with pace tracking relative to season progress.
     */
    data class ProjectedPredictorResult(
        val current: PredictorResult,
        val projected: PredictorResult,
        val paceStatus: PaceStatus,
        val targetTierProgress: Double    // 0-100% progress toward projected tier threshold
    )

    enum class PaceStatus {
        AHEAD,      // current score > expected at this point
        ON_TRACK,   // current score within 10% of expected
        BEHIND,     // current score < expected
        TOO_EARLY   // < 10% of season complete
    }

    // Metric weights (sum to 1.0)
    // .skr domain reduced from 5% to 2% — all Seeker Genesis NFTs include one,
    // so it's not a differentiator. Redistributed to TX, staking, dApp.
    private object Weights {
        const val TRANSACTIONS = 0.16
        const val PROGRAMS = 0.12
        const val TOKEN_DIVERSITY = 0.08
        const val STAKING = 0.21
        const val DOMAIN = 0.02
        const val NFTS = 0.05
        const val WALLET_AGE = 0.10
        const val DAPP_INTERACTIONS = 0.11
        const val SEASON1 = 0.15
    }

    // Normalization thresholds (from observing Seeker ecosystem data)
    private object Thresholds {
        const val MAX_TX = 5000          // top users do ~5K+ transactions
        const val MAX_PROGRAMS = 30      // interacting with 30+ programs is very active
        const val MAX_TOKENS = 20        // 20+ unique tokens
        const val MAX_STAKE_DAYS = 365   // 1 year staking
        const val MAX_NFTS = 50          // 50+ NFTs
        const val MAX_WALLET_DAYS = 730  // 2 years
        const val MAX_DAPP = 500         // 500+ dApp interactions
    }

    /**
     * Run the prediction engine on the given metrics.
     */
    fun predict(metrics: ActivityMetrics): PredictorResult {
        val breakdown = mutableMapOf<String, Double>()

        // Score each metric (0-100 scale)
        val txScore = logNormalize(metrics.totalTransactions.toDouble(), Thresholds.MAX_TX.toDouble())
        breakdown["Transactions"] = txScore

        val progScore = linearNormalize(metrics.uniquePrograms.toDouble(), Thresholds.MAX_PROGRAMS.toDouble())
        breakdown["Programs Used"] = progScore

        val tokenScore = linearNormalize(metrics.tokenDiversity.toDouble(), Thresholds.MAX_TOKENS.toDouble())
        breakdown["Token Diversity"] = tokenScore

        val stakingScore = if (metrics.skrStaked) {
            val durationScore = linearNormalize(metrics.stakingDurationDays.toDouble(), Thresholds.MAX_STAKE_DAYS.toDouble())
            max(50.0, durationScore) // staking at all gets at least 50
        } else {
            0.0
        }
        breakdown["SKR Staking"] = stakingScore

        val domainScore = if (metrics.hasSkrDomain) 100.0 else 0.0
        breakdown[".skr Domain"] = domainScore

        val nftScore = logNormalize(metrics.nftCount.toDouble(), Thresholds.MAX_NFTS.toDouble())
        breakdown["NFTs"] = nftScore

        val ageScore = linearNormalize(metrics.walletAgeDays.toDouble(), Thresholds.MAX_WALLET_DAYS.toDouble())
        breakdown["Wallet Age"] = ageScore

        val dappScore = logNormalize(metrics.dappInteractions.toDouble(), Thresholds.MAX_DAPP.toDouble())
        breakdown["dApp Usage"] = dappScore

        val season1Score = when (metrics.season1Tier) {
            AirdropTier.SOVEREIGN -> 100.0
            AirdropTier.LUMINARY -> 85.0
            AirdropTier.VANGUARD -> 65.0
            AirdropTier.PROSPECTOR -> 40.0
            AirdropTier.SCOUT -> 20.0
            AirdropTier.DEVELOPER -> 100.0
            null -> 0.0  // unknown / not in dataset
        }
        breakdown["Season 1 Tier"] = season1Score

        // Weighted composite
        val composite = (
            txScore * Weights.TRANSACTIONS +
            progScore * Weights.PROGRAMS +
            tokenScore * Weights.TOKEN_DIVERSITY +
            stakingScore * Weights.STAKING +
            domainScore * Weights.DOMAIN +
            nftScore * Weights.NFTS +
            ageScore * Weights.WALLET_AGE +
            dappScore * Weights.DAPP_INTERACTIONS +
            season1Score * Weights.SEASON1
        ).coerceIn(0.0, 100.0)

        // Map composite score to percentile
        val percentile = scoreToPercentile(composite)

        // Map percentile to predicted tier
        val predictedTier = percentileToTier(percentile)

        // Data profile strength (how much on-chain data was available for scoring)
        val confidence = when {
            metrics.season1Tier != null && metrics.totalTransactions > 100 -> "Strong Data"
            metrics.totalTransactions > 50 || metrics.skrStaked -> "Moderate Data"
            else -> "Limited Data"
        }

        Log.d(TAG, "Prediction: score=${"%.1f".format(composite)}, " +
            "percentile=${"%.1f".format(percentile)}%, " +
            "tier=${predictedTier.displayName}, confidence=$confidence")

        return PredictorResult(
            compositeScore = composite,
            percentile = percentile,
            predictedTier = predictedTier,
            confidence = confidence,
            breakdown = breakdown
        )
    }

    /**
     * Log-scale normalization for wide-range metrics (transactions, dApp usage).
     * Gives diminishing returns for very high values.
     */
    private fun logNormalize(value: Double, maxValue: Double): Double {
        if (value <= 0) return 0.0
        val logVal = ln(value + 1)
        val logMax = ln(maxValue + 1)
        return min(100.0, (logVal / logMax) * 100.0)
    }

    /**
     * Linear normalization for bounded metrics (programs, token count, age).
     */
    private fun linearNormalize(value: Double, maxValue: Double): Double {
        if (maxValue <= 0) return 0.0
        return min(100.0, (value / maxValue) * 100.0)
    }

    /**
     * Map composite score (0-100) to fleet percentile.
     * Uses a sigmoid-like curve to simulate realistic distribution.
     */
    private fun scoreToPercentile(score: Double): Double {
        // Most users cluster in the middle — use a slight S-curve
        // Low scores (0-20) → bottom 30%
        // Medium scores (20-60) → middle 50%
        // High scores (60-100) → top 20%
        return when {
            score <= 20 -> score * 1.5 // 0-30 percentile
            score <= 60 -> 30.0 + (score - 20) * 1.25 // 30-80 percentile
            else -> 80.0 + (score - 60) * 0.5 // 80-100 percentile
        }.coerceIn(0.0, 99.9)
    }

    /**
     * Map percentile to predicted tier using known Season 1 distribution:
     * - Scout: bottom 19.5%
     * - Prospector: next 64.2% (19.5 - 83.7)
     * - Vanguard: next 11.9% (83.7 - 95.6)
     * - Luminary: next 4% (95.6 - 99.6)
     * - Sovereign: top 0.4% (99.6+)
     */
    private fun percentileToTier(percentile: Double): AirdropTier {
        return when {
            percentile >= 99.6 -> AirdropTier.SOVEREIGN
            percentile >= 95.6 -> AirdropTier.LUMINARY
            percentile >= 83.7 -> AirdropTier.VANGUARD
            percentile >= 19.5 -> AirdropTier.PROSPECTOR
            else -> AirdropTier.SCOUT
        }
    }

    // Score thresholds per tier (used for pace tracking)
    val TIER_SCORE_THRESHOLDS = mapOf(
        AirdropTier.SCOUT to 0.0,
        AirdropTier.PROSPECTOR to 13.0,
        AirdropTier.VANGUARD to 53.0,
        AirdropTier.LUMINARY to 71.0,
        AirdropTier.SOVEREIGN to 79.0
    )

    /**
     * Run prediction on both current and projected metrics.
     * Computes pace status based on where the user's current score
     * sits relative to their projected tier threshold, adjusted for
     * season progress.
     */
    fun predictWithProjection(
        currentMetrics: ActivityMetrics,
        projectedMetrics: ActivityMetrics,
        seasonFractionComplete: Double,
        isSeasonReliable: Boolean
    ): ProjectedPredictorResult {
        val currentResult = predict(currentMetrics)
        val projectedResult = predict(projectedMetrics)

        // Pace: compare current score against expected score at this point
        // "Expected" = projected tier threshold * fractionComplete
        val projectedTierThreshold = TIER_SCORE_THRESHOLDS[projectedResult.predictedTier] ?: 0.0
        val expectedAtThisPoint = projectedTierThreshold * seasonFractionComplete

        val paceStatus = when {
            !isSeasonReliable -> PaceStatus.TOO_EARLY
            expectedAtThisPoint <= 0 -> PaceStatus.AHEAD
            currentResult.compositeScore >= expectedAtThisPoint * 1.1 -> PaceStatus.AHEAD
            currentResult.compositeScore >= expectedAtThisPoint * 0.9 -> PaceStatus.ON_TRACK
            else -> PaceStatus.BEHIND
        }

        // How far the current score is toward the projected tier threshold
        val targetProgress = if (projectedTierThreshold > 0) {
            ((currentResult.compositeScore / projectedTierThreshold) * 100.0).coerceIn(0.0, 100.0)
        } else {
            100.0
        }

        Log.d(TAG, "Projected prediction: current=${"%.1f".format(currentResult.compositeScore)}, " +
            "projected=${"%.1f".format(projectedResult.compositeScore)}, " +
            "pace=$paceStatus, progress=${"%.0f".format(targetProgress)}%")

        return ProjectedPredictorResult(
            current = currentResult,
            projected = projectedResult,
            paceStatus = paceStatus,
            targetTierProgress = targetProgress
        )
    }
}
