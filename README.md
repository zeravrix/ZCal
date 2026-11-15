# ZCal — Open-Source Camera-First AI Calorie & Macro Tracker (Android)

<p align="center">
  <img src="app/src/main/res/drawable/app_logo_transparent.png" alt="ZCal Logo" width="128" />
  <br>
  <b>Open-source, private, camera-first AI nutrition tracker for Android.</b>
  <br>
  <i>No subscriptions. No private backend. Bring your own AI key (BYOK).</i>
</p>

---

## Overview

**ZCal** is an Android-first calorie, kilojoule, and macro tracking application designed with a **Camera-First** architecture. Point your camera at any meal, and the on-device AI pipeline estimates portions, calories, and macronutrients in seconds.

Inspired by popular apps like Cal AI, Cal+, and Fud AI, ZCal differs in one fundamental way: **there is zero recurring cost and zero data tracking**. Users supply their own API key (Google Gemini free-tier by default, or OpenAI / OpenRouter / local Ollama). Food logs stay exclusively on your device.

- **Primary Brand Theme**: `#23212C` (Luxury Slate Obsidian) with native Material 3, Dark Mode, and AMOLED Pure Black support.
- **Tabular Figures (`tnum`)**: All numeric readouts and animated counters are formatted with tabular numbers to prevent horizontal layout shift.
- **Privacy By Default**: All meal entries, weights, and goals live in a local Room database. API keys are encrypted inside the hardware **Android Keystore** (`AES/GCM/NoPadding`).

---

## Key Features

1. **Camera-First Workflow (`CameraCaptureScreen.kt`)**:
   - Launches directly into a CameraX viewfinder with a live heads-up display (HUD) showing remaining `kcal` & `kJ`, `Protein`, `Fat`, `CLA`, and real-time step counts.
   - Built-in ML Kit Barcode Scanner backed by **Open Food Facts** data with local caching.
2. **Spring-Animated Macro Breakdown (`ReviewMealScreen.kt`)**:
   - Smoothly counts up calories and kilojoules (`kcal × 4.184`) with bouncy physics progress bars for Protein, Carbs, Fat, and CLA.
   - Live portion multiplier pills (`0.5x`, `1x`, `1.5x`, `2x`) and a natural-language "Fix with AI" refinement dialog.
3. **Automated Best-Model Selection (`ModelSelector.kt`)**:
   - Auto-discovers and ranks the optimal vision model for food photo estimation (prioritizing low-latency Flash-tier models).
4. **Health Connect & Sensor Sync (`HealthConnectManager.kt`)**:
   - Bi-directional sync with Android Health Connect for steps, burn, and logged meals, backed by a hardware step counter sensor fallback.
5. **Monthly Auto-Purging Image Cache (`FoodHistoryCacheManager.kt`)**:
   - Automatic 30-day / monthly rollover cleanup of food photos to preserve device storage while keeping permanent aggregated day summaries.
6. **Glance Home-Screen Widget (`ZCalGlanceWidget.kt`)**:
   - Real-time 4×2 home screen widget showing daily calories, macros, and instant scan shortcuts.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Tooling** | Kotlin 2.0.21, AGP 8.7.2, Gradle 8.9, Java 21 |
| **UI Framework** | Jetpack Compose (Material 3), Compose BOM `2024.11.00` |
| **Architecture** | Clean Architecture / MVVM with StateFlow & Hilt DI |
| **Local Database** | Room Database v3 (`ZCalDatabase.kt`) |
| **Networking** | Ktor 3.0.1 on OkHttp with Kotlinx Serialization |
| **Camera & Vision** | CameraX 1.4.0, Google ML Kit Barcode 17.3.0 |
| **Security** | Android Keystore 256-bit AES-GCM (`KeyVault.kt`) |
| **Widget** | Android Jetpack Glance 1.1.1 |

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or JDK 21
- Android SDK Platform 35 (`minSdk = 26`, `targetSdk = 35`)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/zeravrix/ZCal.git
cd ZCal

# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Build optimized release APK (version 1.0.0)
./gradlew assembleRelease
```

The release APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## License

- **Source Code**: Released under the [MIT License](LICENSE).
- **Barcode Food Data**: Powered by [Open Food Facts](https://world.openfoodfacts.org/) under the Open Database License (ODbL).
