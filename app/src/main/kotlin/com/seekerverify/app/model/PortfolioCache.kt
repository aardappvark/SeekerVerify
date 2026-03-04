package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * Cached snapshot of portfolio data for instant loading on app restart.
 * Saved to device after each successful portfolio refresh.
 */
@Serializable
data class PortfolioCache(
    val solBalance: Double,
    val stakedSol: Double,
    val skrBalance: Double,
    val stakedSkr: Double,
    val cooldownSkr: Double,
    val isStaked: Boolean,
    val solPriceUsd: Double? = null,
    val skrPriceUsd: Double? = null,
    val totalValueUsd: Double? = null,
    val estimatedApy: Double,
    val costBasis: Double,
    val rewards: Double,
    val sharePrice: Long,
    val cachedAt: Long       // epoch millis
)
