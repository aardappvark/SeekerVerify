# Contributing to Seeker Verify

Thank you for your interest in contributing to Seeker Verify. This document provides guidelines to help you get started.

## Getting Started

Before contributing, make sure you have the following set up:

- **Android Studio** (latest stable release)
- **JDK 17** (bundled with Android Studio)
- **A Solana Seeker device** connected via USB, or an Android emulator running API 26+

Clone the repository and verify that the project builds:

```bash
git clone https://github.com/your-fork/SeekerVerify.git
cd SeekerVerify
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

## How to Contribute

1. **Fork** the repository on GitHub.
2. **Create a branch** from `main` for your work:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**, keeping commits focused and well-described.
4. **Test your changes** on a device or emulator.
5. **Push your branch** to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
6. **Open a Pull Request** against the `main` branch of the upstream repository.

Please include a clear description of what your PR changes and why.

## Code Style

- **Language**: Kotlin
- **UI framework**: Jetpack Compose
- **Design system**: Material Design 3
- Follow existing code conventions in the project
- Use meaningful names for variables, functions, and classes
- Keep functions focused and concise
- Add comments where the intent is not obvious from the code itself

## Testing

Build and install the debug APK on a connected device:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
```

When testing your changes, verify the following:

- The app builds without errors or warnings
- Existing features continue to work as expected
- Your new feature or fix behaves correctly on a Seeker device or emulator
- The UI renders properly across different screen sizes

## Reporting Issues

When opening an issue, please include:

- A clear and descriptive title
- Steps to reproduce the problem
- Expected behavior vs. actual behavior
- Device model and Android version
- App version
- Screenshots or logs, if applicable

## Code of Conduct

All contributors are expected to be respectful and constructive. When participating in this project:

- Be considerate and welcoming to newcomers
- Provide constructive feedback in code reviews
- Focus on the technical merits of contributions
- Disagree respectfully and seek to understand other perspectives
- Refrain from personal attacks, harassment, or exclusionary behavior

We are committed to providing a positive experience for everyone who contributes to this project.

## Questions

If you have questions about contributing, reach out at **aardappvark@proton.me**.
