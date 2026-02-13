package com.seekerverify.app.engine

import com.seekerverify.app.model.AirdropTier
import kotlin.math.ln
import kotlin.math.min

/**
 * Analyzes the user's on-chain activity in the context of their Season 1 tier.
 * Produces activity highlights, an overall score, and tier-relative percentile.
 */
object Season1Engine {

    data class ActivityHighlight(
        val label: String,
        val description: String,
        val isStrength: Boolean
    )

    data class Season1Analysis(
        val detectedTier: AirdropTier?,
        val tierPercentile: Double,
        val activityHighlights: List<ActivityHighlight>,
        val overallActivityScore: Double
    )

    // Season 1 percentile ranges per tier (cumulative floor/ceiling)
    private data class TierRange(val floor: Double, val ceiling: Double)
    private val TIER_RANGES = mapOf(
        AirdropTier.SCOUT to TierRange(0.0, 19.5),
        AirdropTier.PROSPECTOR to TierRange(19.5, 83.7),
        AirdropTier.VANGUARD to TierRange(83.7, 95.6),
        AirdropTier.LUMINARY to TierRange(95.6, 99.6),
        AirdropTier.SOVEREIGN to TierRange(99.6, 100.0)
    )

    /**
     * Analyze activity metrics in the context of Season 1.
     * Note: S1 scoring excludes SKR staking and domain since SKR didn't exist
     * during the Season 1 activity period (pre-Jan 2026). Those are S2-only factors.
     */
    fun analyze(
        metrics: PredictorEngine.ActivityMetrics,
        detectedTier: AirdropTier?
    ): Season1Analysis {
        // Calculate overall activity score (0-100)
        // S1 context: NO staking/domain — SKR didn't exist during S1 season
        val txScore = logNormalize(metrics.totalTransactions.toDouble(), 5000.0)
        val tokenScore = linearNormalize(metrics.tokenDiversity.toDouble(), 20.0)
        val nftScore = logNormalize(metrics.nftCount.toDouble(), 50.0)
        val ageScore = linearNormalize(metrics.walletAgeDays.toDouble(), 730.0)
        val dappScore = logNormalize(metrics.dappInteractions.toDouble(), 500.0)

        // Weights redistributed without staking (was 10% → split across txs and dapp)
        val overallScore = (
            txScore * 0.30 +
            tokenScore * 0.15 +
            nftScore * 0.10 +
            ageScore * 0.20 +
            dappScore * 0.25
        ).coerceIn(0.0, 100.0)

        // Calculate position within tier band
        val tierPercentile = calculateTierPercentile(detectedTier, overallScore)

        // Generate activity highlights
        val highlights = buildHighlights(metrics)

        return Season1Analysis(
            detectedTier = detectedTier,
            tierPercentile = tierPercentile,
            activityHighlights = highlights,
            overallActivityScore = overallScore
        )
    }

    /**
     * Estimate where the user falls within their tier's percentile range.
     */
    private fun calculateTierPercentile(tier: AirdropTier?, score: Double): Double {
        val range = tier?.let { TIER_RANGES[it] } ?: return score
        val tierWidth = range.ceiling - range.floor
        // Map the 0-100 score to the tier's percentile range
        val position = (score / 100.0) * tierWidth + range.floor
        return position.coerceIn(range.floor, range.ceiling)
    }

    /**
     * Generate human-readable activity highlights for Season 1 context.
     * S1 highlights focus on pre-airdrop on-chain activity only.
     * SKR staking and .skr domains are excluded — SKR didn't exist during S1.
     *
     * Thresholds are calibrated for the Seeker community — typical active users
     * have 100-2000 txs, 5-30 tokens, and wallets from May 2025+.
     */
    private fun buildHighlights(metrics: PredictorEngine.ActivityMetrics): List<ActivityHighlight> {
        val highlights = mutableListOf<ActivityHighlight>()

        // Wallet age highlights
        when {
            metrics.walletAgeDays >= 365 -> highlights.add(ActivityHighlight(
                label = "Early Adopter",
                description = "Wallet active for ${metrics.walletAgeDays}+ days",
                isStrength = true
            ))
            metrics.walletAgeDays >= 180 -> highlights.add(ActivityHighlight(
                label = "Established User",
                description = "Active for ${metrics.walletAgeDays} days since Seeker launch",
                isStrength = true
            ))
            metrics.walletAgeDays < 60 && metrics.walletAgeDays > 0 -> highlights.add(ActivityHighlight(
                label = "Late Arrival",
                description = "Wallet only ${metrics.walletAgeDays} days old",
                isStrength = false
            ))
        }

        // Transaction count highlights
        when {
            metrics.totalTransactions >= 1000 -> highlights.add(ActivityHighlight(
                label = "Power User",
                description = "${metrics.totalTransactions} on-chain transactions recorded",
                isStrength = true
            ))
            metrics.totalTransactions >= 200 -> highlights.add(ActivityHighlight(
                label = "Active Participant",
                description = "${metrics.totalTransactions} transactions on-chain",
                isStrength = true
            ))
            metrics.totalTransactions >= 50 -> highlights.add(ActivityHighlight(
                label = "Regular User",
                description = "${metrics.totalTransactions} transactions recorded",
                isStrength = true
            ))
            metrics.totalTransactions < 20 && metrics.totalTransactions > 0 -> highlights.add(ActivityHighlight(
                label = "Low Activity",
                description = "Only ${metrics.totalTransactions} transactions",
                isStrength = false
            ))
        }

        // Token diversity (general Solana tokens, not SKR-specific)
        when {
            metrics.tokenDiversity >= 20 -> highlights.add(ActivityHighlight(
                label = "DeFi Explorer",
                description = "Interacted with ${metrics.tokenDiversity} unique tokens",
                isStrength = true
            ))
            metrics.tokenDiversity >= 5 -> highlights.add(ActivityHighlight(
                label = "Token Collector",
                description = "Holds ${metrics.tokenDiversity} different tokens",
                isStrength = true
            ))
        }

        // NFT collection
        when {
            metrics.nftCount >= 10 -> highlights.add(ActivityHighlight(
                label = "NFT Collector",
                description = "Holds ${metrics.nftCount} NFTs",
                isStrength = true
            ))
            metrics.nftCount >= 1 -> highlights.add(ActivityHighlight(
                label = "NFT Holder",
                description = "Owns ${metrics.nftCount} NFT${if (metrics.nftCount > 1) "s" else ""}",
                isStrength = true
            ))
        }

        // dApp engagement
        when {
            metrics.dappInteractions >= 500 -> highlights.add(ActivityHighlight(
                label = "Protocol Pioneer",
                description = "Engaged with ${metrics.uniquePrograms}+ programs extensively",
                isStrength = true
            ))
            metrics.dappInteractions >= 50 -> highlights.add(ActivityHighlight(
                label = "dApp Explorer",
                description = "${metrics.dappInteractions} successful program interactions",
                isStrength = true
            ))
        }

        // NOTE: SKR staking and .skr domain highlights are intentionally excluded
        // from Season 1 — SKR hadn't been airdropped during the S1 activity period.
        // Those badges appear in Season 2 predictions via PredictorEngine instead.

        return highlights
    }

    private fun logNormalize(value: Double, maxValue: Double): Double {
        if (value <= 0) return 0.0
        val logVal = ln(value + 1)
        val logMax = ln(maxValue + 1)
        return min(100.0, (logVal / logMax) * 100.0)
    }

    private fun linearNormalize(value: Double, maxValue: Double): Double {
        if (maxValue <= 0) return 0.0
        return min(100.0, (value / maxValue) * 100.0)
    }
}
