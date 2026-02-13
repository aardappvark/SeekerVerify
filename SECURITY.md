# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 1.x | Yes |
| < 1.0 | No |

## Reporting a Vulnerability

If you discover a security vulnerability in Seeker Verify, please report it responsibly by emailing **aardappvark@proton.me**.

Please include:

- A description of the vulnerability
- Steps to reproduce the issue
- The potential impact
- Any suggested fixes, if applicable

### Response Timeline

- **Acknowledgment**: within 7 days of your report
- **Resolution target**: within 30 days of acknowledgment

We will keep you informed of progress toward a fix and may ask for additional information or guidance.

## Scope

The following areas are in scope for security reports:

- Application source code
- Dependency vulnerabilities
- Data handling and local storage
- Encryption implementation

## Out of Scope

The following are outside the scope of this policy:

- Vulnerabilities in the Solana blockchain itself
- Issues with third-party RPC providers (e.g., Helius, public Solana RPC endpoints)
- Vulnerabilities in the Solana Mobile Wallet Adapter or the wallet app
- Issues that require physical access to an unlocked device

## Security Features

Seeker Verify is built with security as a core design principle:

- **AES-256 encryption** -- all locally stored data is encrypted using EncryptedSharedPreferences with AES-256-GCM for values and AES-256-SIV for keys
- **No server** -- there is no backend server. No user data leaves the device.
- **HTTPS-only** -- all network communication with Solana RPC endpoints uses HTTPS
- **No analytics** -- no analytics SDKs, no tracking pixels, no advertising frameworks. The app does not collect or transmit usage data.
- **Read-only** -- the app never creates, signs, or submits blockchain transactions
