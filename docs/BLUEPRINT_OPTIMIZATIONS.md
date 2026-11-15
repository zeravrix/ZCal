# ZCal Blueprint v2 — Camera-First Architecture, kcal/kJ/CLA, Health Connect & Monthly Auto-Purging Cache

This document details the complete v2 architecture implemented in the ZCal Android codebase (`ZCal-Android-Full-Source.zip`).

## 1. Core Architectural Upgrades

| Module | Implementation in Source Code |
| :--- | :--- |
| **1. First-Time Setup & Day/Week/Month Target Generator** | `OnboardingScreen.kt` + `ComputeGoalsUseCase.kt` collect **Weight (`kg`)**, **Height / Tall (`cm`)**, **Age/Sex**, **Goal**, and **API Key** before the app starts for the first time, generating exact **Daily (`1D`)**, **Weekly (`7D`)**, and **Monthly (`30D`)** targets across **Calories (`kcal`)**, **Kilojoules (`kJ`)**, **Protein (`g`)**, **Fat (`g`)**, **Carbs (`g`)**, **CLA (`mg`)**, and **Fiber (`g`)**. |
| **2. Pre-Built Camera as Main Page** | `MainActivity.kt` routes `startDestination` directly to `BottomNavTab.CAMERA.route` once onboarding is complete. `CameraCaptureScreen.kt` overlays a live HUD bar showing remaining `kcal` & `kJ`, `Protein`, `Fat`, `CLA`, and live **Steps** above the CameraX viewfinder. |
| **3. Animated Post-Capture Breakdown (`kcal`, `kJ`, `Protein`, `Fat`, `Carbs`, `CLA`)** | `ReviewMealScreen.kt` uses `animateIntAsState` and `spring(dampingRatio = Spring.DampingRatioLowBouncy)` to animate both **Calories (`kcal`)** and **Kilojoules (`kJ = kcal × 4.184`)**, plus spring progress bars for **Protein (`g`)**, **Fat (`g`)**, **Carbs (`g`)**, and **CLA (`mg`)**. |
| **4. Health Connect & Step Sensor Sync** | `HealthConnectManager.kt` integrates `androidx.health.connect:connect-client` (`StepsRecord`, `ActiveCaloriesBurnedRecord`, `NutritionRecord`, `WeightRecord`) paired with a live `Sensor.TYPE_STEP_COUNTER` hardware fallback so steps and active burn (`kcal` & `kJ`) are always available. |
| **5. Monthly Auto-Emptying Food Image & Detail Cache** | `FoodHistoryCacheManager.kt` + `MonthlyCacheCleanupWorker.kt` + `HistoryCacheScreen.kt` store meal images in `filesDir/meals_cache/YYYY-MM/` and details in `food_history_cache`, automatically purging previous months' images and details on month rollover / 30-day TTL while keeping `day_summary` rows for long-term charts. |
| **6. Hardware Keystore Vault & Multi-Provider BYOK** | `KeyVault.kt` encrypts API keys with 256-bit `AES/GCM/NoPadding` inside `AndroidKeyStore` (excluded from cloud backup via `backup_rules.xml` and `data_extraction_rules.xml`), supporting Google Gemini and OpenAI/OpenRouter/Ollama. |
