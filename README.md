# ZCal — Open-Source Camera-First AI Calorie & Macro Tracker (Android)

<p align="center">
  <img src="app/src/main/res/drawable/app_logo_transparent.png" alt="ZCal Logo" width="128" />
  <br>
  <b>Open-source, private, camera-first AI nutrition tracker for Android.</b>
  <br>
  <i>No subscriptions. No private backend. Bring your own AI key (BYOK). Version 1.2.0.</i>
</p>

---

## Overview

**ZCal** is an Android-first calorie, kilojoule, and macro tracking application designed with a **Camera-First** architecture. Point your camera at any meal, and the on-device AI pipeline estimates portions, calories, and macronutrients in seconds.

Inspired by modern food tracking apps like Cal AI and Fud AI, ZCal differs in one fundamental way: **zero recurring cost, zero telemetry, and zero privacy compromises**. Users supply their own API key (Google Gemini free tier or OpenAI / OpenRouter / Ollama). Food logs stay exclusively on your device in a local Room database, and API keys are stored in the hardware **Android Keystore** (`AES/GCM/NoPadding`).

- **Theme Palette**: Luxury Obsidian Slate (`#23212C`) with calm off-white accents, native Material 3, Dark Mode, and AMOLED Pure Black support.
- **Hardware Notch & Display Cutout**: True edge-to-edge rendering with window background color matching to eliminate ugly white notch gaps.
- **Stateless AI Vision Pipeline**: Single-turn requests with no chat memory or image retention overhead, keeping token usage minimal.
- **Multilingual Support**: Fully localized across 8 languages (English, Arabic with RTL, French, Spanish, German, Japanese, Chinese, Korean).

---

## Key Features

1. **Camera-First Workflow (`CameraCaptureScreen.kt`)**:
   - Launches directly into a CameraX viewfinder with a sleek floating glassmorphic heads-up display (HUD).
   - Real-time remaining `kcal` & `kJ`, `Protein`, `Carbs`, `Fat`, and step counts with quick flash and food cache shortcuts.
   - Built-in ML Kit Barcode Scanner backed by **Open Food Facts** data with local caching.
2. **Magnetic Snapping Sliders (`MetricSliderCard`)**:
   - Fluid sliders with magnetic detents and haptic feedback on multiples of 5 and 0 (e.g. 60, 65, 70, 75) plus `-` / `+` precision step buttons.
3. **Full Birthday & Long-Term Health Tracking**:
   - Day, Month, and Year sliders for exact birthday input.
   - Exact age detection using `java.time.Period` with automatic metabolic target recalculation (Mifflin-St Jeor gold standard) when age advances or body measurements change.
   - Body Measurements card in Settings to update weight and height anytime.
4. **Spring-Animated Macro Breakdown (`ReviewMealScreen.kt`)**:
   - Bouncy physics progress bars for Protein, Carbs, Fat, and CLA.
   - Live portion multiplier pills (`0.5x`, `1x`, `1.5x`, `2x`) and stateless "Fix with AI" refinement dialog.
5. **BYOK Fast Verification & Model Selection**:
   - Direct lightweight single-turn verification (<600ms) with an 8-second network timeout.
6. **Monthly Auto-Purging Image Cache (`FoodHistoryCacheManager.kt`)**:
   - Automatic 30-day / monthly rollover cleanup of food photos to preserve device storage while keeping permanent aggregated day summaries.
7. **Glance Home-Screen Widget (`ZCalGlanceWidget.kt`)**:
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
- JDK 21
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

# Build optimized release APK (version 1.2.0)
./gradlew assembleRelease
```

The release APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## License

- **Source Code**: Released under the [MIT License](LICENSE).
- **Barcode Food Data**: Powered by [Open Food Facts](https://world.openfoodfacts.org/) under the Open Database License (ODbL).
