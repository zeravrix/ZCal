# ZCal Developer Guide & Architecture Reference

## Overview
**ZCal** is an open-source, camera-first calorie and macro tracker built on Android (Kotlin + Jetpack Compose) using a Bring-Your-Own-Key (BYOK) architecture. Food scans are analyzed on-device or via the user's personal Google Gemini or OpenAI/Ollama API key. No subscriptions, no private telemetry, zero backend costs.

## Build Commands
- **Run Unit Tests**: `./gradlew testDebugUnitTest`
- **Build Debug APK**: `./gradlew assembleDebug`
- **Build Release APK**: `./gradlew assembleRelease`
- **Lint Check**: `./gradlew lintDebug`

## Key Architecture Patterns
- **Presentation**: Jetpack Compose with Material 3. Primary brand color `#23212C` with adaptive Dark/AMOLED themes.
- **Tabular Figures**: All numeric text styles in `Type.kt` use `fontFeatureSettings = "tnum"` to prevent layout jittering during number animations.
- **Camera First**: Pre-built CameraX viewfinder (`CameraCaptureScreen.kt`) is the main starting destination after onboarding with a live HUD of remaining calories and macros.
- **AI Abstraction**: `FoodAnalyzer` interface implemented by `MultiProviderFoodAnalyzer` supporting Gemini (`generateContent` with strict `responseSchema`) and OpenAI-compatible endpoints (`chat/completions` with JSON schema).
- **Auto Model Selection**: `ModelSelector.kt` automatically ranks and picks the best available Flash-tier vision model for the user's API key.
- **Validation**: `ResponseValidator.kt` reconciles Atwater factors (`4P + 4C + 9F`), clamps impossible estimates, and calculates kilojoules (`kcal × 4.184`) and CLA.
- **Local Storage**: Room database (`ZCalDatabase.kt` v3) with `localDate` grouping (`yyyy-MM-dd`) to guarantee timezone resilience.
- **Key Security**: Android Keystore 256-bit AES-GCM (`KeyVault.kt`) excluded from Android cloud backup.
- **Cache Management**: `FoodHistoryCacheManager.kt` auto-purges meal images on month rollover / 30-day TTL while preserving aggregate data for long-term charts.

## Code Conventions
- Follow Kotlin official style guide.
- Keep dependencies minimal; no bloated abstractions.
- All touch targets must be at least 48 dp.
- Never hardcode API keys or send telemetry.
