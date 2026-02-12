package com.seekerverify.app.model

/**
 * Season 1 airdrop tiers with known distribution percentages.
 * SKR amounts are in raw token units (6 decimals).
 */
enum class AirdropTier(
    val displayName: String,
    val skrAmount: Long,
    val percentOfWallets: Double
) {
    SCOUT("Scout", 5_000_000_000L, 19.5),
    PROSPECTOR("Prospector", 10_000_000_000L, 64.2),
    VANGUARD("Vanguard", 40_000_000_000L, 11.9),
    LUMINARY("Luminary", 125_000_000_000L, 4.0),
    SOVEREIGN("Sovereign", 750_000_000_000L, 0.4),
    DEVELOPER("Developer", 750_000_000_000L, 0.0);

    val skrDisplay: Double get() = skrAmount / 1_000_000.0

    companion object {
        fun fromSkrAmount(amount: Long): AirdropTier {
            return entries.firstOrNull { it.skrAmount == amount } ?: SCOUT
        }

        fun fromName(name: String): AirdropTier? {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
        }
    }
}
