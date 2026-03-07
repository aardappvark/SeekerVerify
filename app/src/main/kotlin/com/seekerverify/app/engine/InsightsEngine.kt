package com.seekerverify.app.engine

/**
 * Rule-based on-device advisor that generates prioritized insights
 * from prediction results and activity metrics.
 * Now includes estimated point gains via simulated metric changes.
 * No external AI API — all logic is local.
 */
object InsightsEngine {

    enum class Category { STAKING, ACTIVITY, DIVERSITY, DOMAIN, TIMING }
    enum class Impact { HIGH, MEDIUM, LOW }

    data class InsightCard(
        val title: String,
        val description: String,
        val category: Category,
        val impact: Impact,
        val actionable: Boolean,
        val estimatedGain: Double = 0.0
    )

    /**
     * Generate insights from a prediction result.
     * When currentMetrics is provided, each insight includes an estimated
     * point gain computed by simulating the recommended change.
     * Returns sorted by estimated gain (highest first), then impact.
     */
    fun generate(
        breakdown: Map<String, Double>,
        compositeScore: Double,
        predictedTierName: String,
        isStaking: Boolean,
        skrBalance: Double,
        hasDomain: Boolean,
        currentMetrics: PredictorEngine.ActivityMetrics? = null
    ): List<InsightCard> {
        val insights = mutableListOf<InsightCard>()

        // Staking insight (21% weight — biggest lever)
        val stakingScore = breakdown["SKR Staking"] ?: 0.0
        if (stakingScore < 50) {
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(skrStaked = true, stakingDurationDays = maxOf(m.stakingDurationDays, 30)))
            } ?: 0.0
            insights.add(InsightCard(
                title = if (!isStaking) "Start Staking SKR" else "Increase SKR Stake",
                description = if (!isStaking)
                    "SKR staking is the single largest factor (21% weight). Even a small stake significantly boosts your score."
                else
                    "Your staking score is ${stakingScore.toInt()}/100. Increasing your staked amount could improve your tier prediction.",
                category = Category.STAKING,
                impact = Impact.HIGH,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // Transaction volume
        val txScore = breakdown["Transactions"] ?: 0.0
        if (txScore < 60) {
            val target = when {
                txScore < 20 -> 50
                txScore < 40 -> 100
                else -> 200
            }
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(totalTransactions = m.totalTransactions + target))
            } ?: 0.0
            insights.add(InsightCard(
                title = "Increase Transaction Volume",
                description = "Your transaction score is ${txScore.toInt()}/100. Aim for $target+ transactions to improve this metric (14% weight).",
                category = Category.ACTIVITY,
                impact = if (txScore < 30) Impact.HIGH else Impact.MEDIUM,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // .skr domain (easy 2% boost)
        if (!hasDomain) {
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(hasSkrDomain = true))
            } ?: 0.0
            insights.add(InsightCard(
                title = "Register a .skr Domain",
                description = "Owning a .skr domain is a quick win — it contributes 2% to your score and signals Seeker ecosystem engagement.",
                category = Category.DOMAIN,
                impact = Impact.MEDIUM,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // Token diversity
        val tokenScore = breakdown["Token Diversity"] ?: 0.0
        if (tokenScore < 50) {
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(tokenDiversity = m.tokenDiversity + 5))
            } ?: 0.0
            insights.add(InsightCard(
                title = "Diversify Token Holdings",
                description = "Holding a wider variety of SPL tokens shows ecosystem participation. Score: ${tokenScore.toInt()}/100 (8% weight).",
                category = Category.DIVERSITY,
                impact = Impact.MEDIUM,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // dApp usage
        val dappScore = breakdown["dApp Frequency"] ?: 0.0
        if (dappScore < 50) {
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(dappInteractions = m.dappInteractions + 50))
            } ?: 0.0
            insights.add(InsightCard(
                title = "Explore More dApps",
                description = "Interacting with diverse Solana programs improves your dApp score (${dappScore.toInt()}/100, 10% weight).",
                category = Category.ACTIVITY,
                impact = Impact.MEDIUM,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // Programs used
        val progScore = breakdown["Unique dApps"] ?: 0.0
        if (progScore < 50) {
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(uniquePrograms = m.uniquePrograms + 5))
            } ?: 0.0
            insights.add(InsightCard(
                title = "Use More Programs",
                description = "Your programs score is ${progScore.toInt()}/100 (11% weight). Try DeFi, NFT marketplaces, or governance programs.",
                category = Category.ACTIVITY,
                impact = Impact.MEDIUM,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // Consistency (5% weight)
        val consistencyScore = breakdown["Consistency"] ?: 0.0
        if (consistencyScore < 50) {
            val gain = currentMetrics?.let { m ->
                simulateGain(m, m.copy(uniqueActiveDays = m.uniqueActiveDays + 15))
            } ?: 0.0
            insights.add(InsightCard(
                title = "Improve Consistency",
                description = "Your consistency score is ${consistencyScore.toInt()}/100 (5% weight). Use your wallet on more unique days to boost this metric.",
                category = Category.ACTIVITY,
                impact = Impact.MEDIUM,
                actionable = true,
                estimatedGain = gain
            ))
        }

        // Close to next tier
        val tierThresholds = mapOf(
            "Prospector" to 13.0, "Vanguard" to 53.0, "Luminary" to 71.0, "Sovereign" to 79.0
        )
        for ((tier, threshold) in tierThresholds) {
            if (compositeScore < threshold && compositeScore >= threshold - 15) {
                insights.add(InsightCard(
                    title = "Close to $tier Tier",
                    description = "You're ${(threshold - compositeScore).toInt()} points from $tier. Focus on your weakest metrics for the biggest boost.",
                    category = Category.TIMING,
                    impact = Impact.HIGH,
                    actionable = false
                ))
                break
            }
        }

        // Strong profile acknowledgment
        if (compositeScore >= 75 && insights.size <= 1) {
            insights.add(0, InsightCard(
                title = "Strong Activity Profile",
                description = "Your on-chain activity is well-diversified across all metrics. Keep maintaining your current engagement.",
                category = Category.ACTIVITY,
                impact = Impact.LOW,
                actionable = false
            ))
        }

        // Sort by estimated gain (highest first), then impact
        return insights.sortedWith(compareByDescending<InsightCard> { it.estimatedGain }
            .thenBy {
                when (it.impact) {
                    Impact.HIGH -> 0
                    Impact.MEDIUM -> 1
                    Impact.LOW -> 2
                }
            })
    }

    /**
     * Simulate the score gain from a metric change.
     */
    private fun simulateGain(
        current: PredictorEngine.ActivityMetrics,
        simulated: PredictorEngine.ActivityMetrics
    ): Double {
        val currentScore = PredictorEngine.predict(current).compositeScore
        val simulatedScore = PredictorEngine.predict(simulated).compositeScore
        return (simulatedScore - currentScore).coerceAtLeast(0.0)
    }
}
