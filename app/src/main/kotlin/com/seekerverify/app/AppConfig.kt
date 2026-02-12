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
        const val SKR_STAKING_PROGRAM = "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ"
        const val SKR_INFLATION_PROGRAM = "SKRiHLtLyB8bbhcJ5HBPYMiLh9GcFLdPaSwozqLteha"
        const val SKR_STAKE_VAULT = "8isViKbwhuhFhsv2t8vaFL74pKCqaFPQXo1KkeQwZbB8"
    }

    object Domains {
        const val ANS_PROGRAM_ID = "ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK"
        const val SKR_TLD = ".skr"
    }

    object Cache {
        const val SGT_CACHE_HOURS = 24L
        const val BALANCE_CACHE_MINUTES = 5L
        const val DOMAIN_CACHE_HOURS = 24L
        const val ACTIVITY_CACHE_HOURS = 1L
        const val COMMUNITY_CACHE_HOURS = 6L
        const val PRICE_CACHE_MINUTES = 5L
    }

    object Season2 {
        const val CLAIM_DEADLINE = "2026-04-20"
    }
}
