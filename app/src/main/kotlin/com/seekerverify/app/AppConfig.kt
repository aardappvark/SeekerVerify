package com.seekerverify.app

object AppConfig {

    object Identity {
        const val NAME = "Seeker Verify"
        const val URI = "https://aardappvark.github.io/SeekerVerify"
        const val ICON_URI = "favicon.png"
    }

    object Wallet {
        const val CLUSTER = "mainnet-beta"
        const val CHAIN = "solana:mainnet"
    }

    object Rpc {
        private const val HELIUS_MAINNET_BASE = "https://mainnet.helius-rpc.com/?api-key="
        fun heliusUrl(apiKey: String): String = "$HELIUS_MAINNET_BASE$apiKey"
        const val PUBLIC_MAINNET = "https://api.mainnet-beta.solana.com"
        const val COINGECKO_PRICE_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd"
    }

    object Tokens {
        const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
        const val SKR_DECIMALS = 6
        const val SKR_DECIMALS_DIVISOR = 1_000_000.0
        const val SKR_STAKING_PROGRAM = "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ"
        const val SKR_INFLATION_PROGRAM = "SKRiHLtLyB8bbhcJ5HBPYMiLh9GcFLdPaSwozqLteha"
        const val SKR_STAKE_VAULT = "8isViKbwhuhFhsv2t8vaFL74pKCqaFPQXo1KkeQwZbB8"
        const val SKR_STAKE_CONFIG = "4HQy82s9CHTv1GsYKnANHMiHfhcqesYkK6sB3RDSYyqw"
        const val SHARE_PRICE_PRECISION = 1_000_000_000L
        const val FALLBACK_SHARE_PRICE = 1_015_000_000L  // ~1.015 as of Feb 2026
    }

    object Domains {
        const val ANS_PROGRAM_ID = "ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK"
        const val TLD_HOUSE_PROGRAM_ID = "TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S"
        const val NAME_HOUSE_PROGRAM_ID = "NH3uX6FtVE2fNREAioP7hm5RaozotZxeL6khU1EHx51"
        const val SKR_TLD = ".skr"
        const val SKR_TLD_NAME = "skr"
        const val NAME_RECORD_HEADER_SIZE = 200 // bytes
        const val HASH_PREFIX = "ALT Name Service"
    }

    object Cache {
        const val SGT_CACHE_HOURS = 24L
        const val BALANCE_CACHE_MINUTES = 5L
        const val DOMAIN_CACHE_HOURS = 24L
        const val ACTIVITY_CACHE_HOURS = 1L
        const val COMMUNITY_CACHE_HOURS = 6L
        const val PRICE_CACHE_MINUTES = 5L
        const val SEASON1_CACHE_HOURS = 168L  // 7 days — S1 data is immutable history
    }

    object Season1 {
        const val CLAIM_START_EPOCH = 1768953600L  // Jan 21, 2026 00:00 UTC (SKR claim launch)
        const val CLAIM_END_EPOCH = 1776815999L    // Apr 21, 2026 23:59 UTC (90-day claim window)
    }

    object Season2 {
        const val ASSUMED_START = "2025-05-15"   // SKR token launch / Seeker ship date
        const val ASSUMED_END = "2026-10-15"     // ~17 months — assumed S2 close
        const val CLAIM_DEADLINE = "2026-04-20"  // S1 claim deadline (reference only)
    }
}
