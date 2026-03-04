# Seeker Verify

A privacy-first companion dApp for Solana Seeker device owners. Built for the Solana dApp Store and optimized for the Seeker mobile experience.

Seeker Verify lets you verify your Seeker Genesis Token (SGT), predict your Season 2 airdrop tier, track your SKR/SOL portfolio, check in daily on-chain via Seed Vault, and explore community fleet statistics -- all with strong privacy guarantees.

Published by **MidMightBit Games** (Australia).

## Features

### Identity & Verification
- **SGT (Seeker Genesis Token) verification** -- confirm ownership of your Seeker Genesis Token on-chain via Token-2022 parsing
- **Fleet member number** -- discover your position as an early adopter in the Seeker fleet
- **.skr domain resolution** -- look up and display your .skr domain name (AllDomains integration)
- **Season 1 airdrop tier detection** -- identify and display your actual Season 1 airdrop tier

### Season 2 Airdrop Predictor
- **Multi-factor prediction engine** -- estimates your Season 2 airdrop tier using SGT ownership, SKR staking, SOL staking, on-chain activity, community participation, and historical Season 1 data
- **What-If Simulator** -- drag sliders to explore how changes in staking, activity, or portfolio would affect your predicted tier
- **Score trajectory chart** -- track how your composite score changes over time with historical snapshots
- **Tier reveal animation** -- cinematic reveal of your predicted tier with animated radar chart breakdown
- **Prediction caching** -- instant reload of last prediction with background refresh

### Portfolio
- **SKR token balance and staking** -- real-time SKR balance, staked amount, pending rewards, and share price from on-chain StakeConfig
- **SOL balance and staking positions** -- SOL holdings, native stake accounts, validator info, and activation status
- **Portfolio health score** -- composite health metric based on diversification, staking participation, and activity
- **AI-powered insights** -- contextual tips based on your portfolio composition and market conditions

### Community & Analytics
- **Fleet statistics** -- total Seekers, active stakers, total staked SKR, and staking participation percentage
- **You vs Fleet card** -- compare your staked SKR against fleet average and fleet mode (most common staked amount)
- **Fleet Mode loading animation** -- animated scanner visualization while community data loads from chain
- **Leaderboard** -- anonymous tier distribution, score distribution, and country leaders
- **Anonymous geo-analytics** -- privacy-preserving usage tracking via Cloudflare Worker (see Privacy section)

### Engagement
- **Daily check-in with streak tracking** -- maintain your streak and earn achievements
- **On-chain check-in via Seed Vault** -- record check-ins as on-chain memos using Solana Mobile Stack transaction signing
- **14 achievements** -- unlock badges for streaks, predictions, portfolio milestones, and community participation
- **Achievement backup** -- HMAC-SHA256 secured backup to device storage (survives app uninstall)
- **Push notifications** -- daily streak reminders and tier change alerts via WorkManager
- **Home screen widget** -- at-a-glance view of your predicted tier and streak

### Share & Social
- **Share card generator** -- create 1080x1350 social media cards with your prediction, tier, score, and percentile
- **Identity sharing** -- share your fleet position and .skr domain

### Accessibility & Customization
- **20 languages** -- full localization support
- **Light/dark/system theme** -- Material 3 dynamic theming
- **Onboarding flow** -- guided first-time experience
- **Guest mode** -- explore the app without connecting a wallet
- **Haptic feedback** -- configurable tactile responses

## Architecture

Seeker Verify is designed around four core principles:

- **Privacy-first** -- all personal data is stored on-device using AES-256 encryption. Analytics are fully anonymous (see Privacy section below).
- **On-chain data via Solana RPC** -- all blockchain data is fetched directly from Solana RPC endpoints (public mainnet or Helius).
- **Seed Vault integration** -- on-chain check-ins use the Solana Mobile Stack's transaction signing flow for secure memo transactions.
- **Resilient caching** -- prediction, portfolio, and community data are cached for instant reload with background refresh.

### Privacy Architecture

Analytics are handled by a Cloudflare Worker that derives country/city from the request IP via Cloudflare's edge network (`request.cf`). The app sends **only the event type** (e.g., `app_open`). No IP addresses, wallet addresses, device IDs, or any PII is ever stored. Leaderboard submissions contain only bucketed scores and tier names. Users can opt out entirely via Settings.

This architecture is compliant with GDPR (Recital 26 -- anonymous data), CCPA, LGPD, APPI, and PIPA without requiring consent.

### Data Backup

Critical data survives app uninstall via MediaStore backup to the Downloads folder:
- Check-in streaks and prediction history
- Achievement progress (HMAC-SHA256 integrity verification tied to wallet address)
- Analytics opt-in preference

## Tech Stack

| Component | Detail |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| Target SDK | Android SDK 35 (minSdk 26) |
| Wallet integration | Solana Mobile Wallet Adapter 2.0.3 |
| SGT verification | seeker-verify library (included as local module) |
| On-chain signing | Seed Vault via MWA transaction signing |
| Local encryption | EncryptedSharedPreferences (AES-256-GCM + AES-256-SIV) |
| Serialization | kotlinx.serialization |
| HTTP | OkHttp 4.12 |
| Background work | AndroidX WorkManager 2.9 |
| Analytics backend | Cloudflare Worker + D1 database (see `analytics-worker/`) |

## Project Structure

```
SeekerVerify/
  app/                          # Main Android application
    src/main/kotlin/.../
      data/                     # AppPreferences, CheckInBackupManager
      engine/                   # PredictorEngine, AchievementEngine, InsightsEngine, HealthScoreEngine
      model/                    # Data classes (Achievement, caches, records)
      rpc/                      # Solana RPC clients (Community, Staking, Activity, Sol, RpcProvider)
      service/                  # GeoAnalyticsService, NotificationService, ShareCardGenerator
      ui/
        components/             # GlassCard, RadarChart, ScoreTrajectoryChart, WhatIfSimulator, TierRevealAnimation
        screens/                # Identity, Predictor, Portfolio, Community, Settings, Onboarding, WalletConnect
        viewmodel/              # Community, Portfolio, Predictor ViewModels
        navigation/             # AppNavigation
        theme/                  # Color, Theme
        util/                   # HapticUtils
      wallet/                   # WalletManager, SolanaTransactionBuilder
      widget/                   # SeekerWidgetProvider
      worker/                   # DailyCheckInWorker, TierChangeWorker, WidgetRefreshWorker
  seeker-verify/                # SGT verification library (local module)
  analytics-worker/             # Cloudflare Worker for anonymous geo-analytics
  assets/                       # dApp Store assets (icon, screenshots, banner)
  docs/                         # Legal pages (privacy, terms, copyright)
```

## Build Instructions

Prerequisites:
- Android Studio (latest stable)
- JDK 17 (bundled with Android Studio)
- A Solana Seeker device connected via USB

### Setup

1. Clone the repository
2. Create `local.properties` in the project root:
```properties
sdk.dir=/path/to/Android/sdk

# Signing credentials
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password

# Optional: Helius RPC key for faster/more reliable RPC
HELIUS_API_KEY=your_helius_key

# Optional: Analytics Worker API key
ANALYTICS_API_KEY=your_analytics_key
```

3. Place your `release-keystore.jks` in the project root directory.

### Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
```

### Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Solana Mobile Stack Integration

Seeker Verify uses multiple Solana Mobile Stack components:

| Component | Usage |
|---|---|
| Mobile Wallet Adapter (MWA) | Sign In With Solana (SIWS) for wallet connection |
| Seed Vault | Transaction signing for on-chain check-in memos |
| SGT Token-2022 | Verify Seeker Genesis Token ownership |
| SKR Staking Program | Read staking positions, share price, rewards |
| dApp Store | Published on Solana dApp Store for Seeker |

## Legal

- [Privacy Policy](docs/privacy.html)
- [Terms of Use](docs/terms.html)
- [Copyright Notice](docs/copyright.html)

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Contact

- Publisher: MidMightBit Games (Australia)
- Email: aardappvark@proton.me
