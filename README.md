# Seeker Verify

A privacy-first companion dApp for Solana Seeker device owners.

Seeker Verify lets you verify your Seeker Genesis Token (SGT), track your SKR token portfolio, and stay informed about airdrops and community fleet statistics -- all without ever sending your data to a server.

Published by **MidMightBit Games** (Australia).

## Features

- **SGT (Seeker Genesis Token) verification** -- confirm ownership of your Seeker Genesis Token on-chain
- **SKR token portfolio tracking** -- view your balance, staking status, and rewards
- **SOL balance and staking display** -- see your SOL holdings and staking positions
- **.skr domain resolution** -- look up .skr domain names
- **Season 1 airdrop tier detection** -- identify your tier for the Season 1 airdrop
- **Season 2 airdrop predictor with projections** -- estimate your Season 2 airdrop based on current data
- **Community fleet position and statistics** -- see where you rank within the Seeker fleet
- **Daily check-in engagement tracker** -- maintain a streak and track your daily engagement

## Architecture

Seeker Verify is designed around four core principles:

- **Zero-server** -- all data is stored on-device using AES-256 encryption. There is no backend server.
- **Read-only** -- the app never creates, signs, or submits blockchain transactions.
- **Privacy-first** -- no analytics, no tracking, no advertising SDKs. Your data stays on your device.
- **On-chain data via Solana RPC** -- all blockchain data is fetched directly from Solana RPC endpoints (public or Helius).

## Tech Stack

| Component | Detail |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design system | Material Design 3 |
| Target SDK | Android SDK 34 (minSdk 26) |
| Wallet integration | Solana Mobile Wallet Adapter 2.0.3 |
| SGT verification | seeker-verify library |
| Local encryption | EncryptedSharedPreferences (AES-256-GCM + AES-256-SIV) |

## Localization

Seeker Verify supports 20 languages.

## Build Instructions

Prerequisites:

- Android Studio (latest stable)
- JDK 17 (bundled with Android Studio)
- A Solana Seeker device connected via USB, or an Android emulator

Build the debug APK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Install on a connected device:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
```

## Legal

- [Privacy Policy](docs/privacy.html)
- [Terms of Use](docs/terms.html)
- [Copyright Notice](docs/copyright.html)

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Contact

- Publisher: MidMightBit Games (Australia)
- Email: aardappvark@proton.me
