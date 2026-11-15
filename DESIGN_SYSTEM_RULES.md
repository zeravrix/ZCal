# ZCal Design System & Figma MCP Integration Rules

> Comprehensive specification for Figma Model Context Protocol (MCP) design tokens, UI components, styling architecture, and asset management for **ZCal** (Open-Source AI Calorie & Macro Tracker).

---

## 1. Token Definitions

### File Locations
- **Colors & Semantics**: [`app/src/main/java/app/zcal/ui/theme/Color.kt`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/java/app/zcal/ui/theme/Color.kt)
- **Typography Scale**: [`app/src/main/java/app/zcal/ui/theme/Type.kt`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/java/app/zcal/ui/theme/Type.kt)
- **Shapes & Corner Radii**: [`app/src/main/java/app/zcal/ui/theme/Shape.kt`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/java/app/zcal/ui/theme/Shape.kt)
- **Theme Engine & Providers**: [`app/src/main/java/app/zcal/ui/theme/Theme.kt`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/java/app/zcal/ui/theme/Theme.kt)
- **XML System Colors**: [`app/src/main/res/values/colors.xml`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/res/values/colors.xml)

### Primary Color: `#23212C` (Luxury Obsidian Slate)
The core brand color is `#23212C` (RGB `35, 33, 44`). It establishes a calm, professional, high-end feel inspired by modern flagship mobile applications (Cal AI, Apple Health, Linear).

### Color Token Structure
| Token Name | Light Theme | Dark Theme (Obsidian) | AMOLED Mode | Role / Semantic Meaning |
| :--- | :--- | :--- | :--- | :--- |
| **`primary`** | `#23212C` | `#D6D0EA` | `#D6D0EA` | Main action buttons, active navigation indicators, key highlights |
| **`onPrimary`** | `#FFFFFF` | `#23212C` | `#23212C` | High-contrast label/icon on top of primary container |
| **`canvas`** | `#F7F8F5` | `#16151F` | `#000000` | App background canvas (smooth off-white or deep obsidian) |
| **`surface`** | `#FFFFFF` | `#23212C` | `#0A090F` | Elevated cards, dialogs, bottom sheets, navigation bar |
| **`surfaceVariant`**| `#EEF1EC` | `#2E2B3A` | `#1C1828` | Progress ring tracks, text input fields, chip containers |
| **`outline`** | `#DDE2DA` | `#3E3A4D` | `#2A2638` | Subtle 1 dp card borders and section dividers |
| **`textPrimary`** | `#101512` | `#F0EEF5` | `#FFFFFF` | Main headlines, food names, calorie numerals (WCAG AAA > 13.5:1) |
| **`textSecondary`**| `#5B655E` | `#A6A2B6` | `#A6A2B6` | Subtitles, units, macro labels, timestamps |
| **`protein`** | `#E5533D` | `#FF7A66` | `#FF7A66` | Fixed semantic color for Protein grams & charts |
| **`carbs`** | `#C27C0E` | `#F0B04A` | `#F0B04A` | Fixed semantic color for Carbohydrates & over-goal warning |
| **`fat`** | `#3B7BF2` | `#7BA7FF` | `#7BA7FF` | Fixed semantic color for Dietary Fats & charts |
| **`error`** | `#C62828` | `#FF8A80` | `#FF8A80` | Input validation errors, API failure states |

### Spacing & Grid System
8 dp modular grid:
- **`4.dp`**: Inner icon gaps, micro spacers
- **`8.dp`**: Chip padding, small element gaps
- **`12.dp`**: Standard gap between list cards
- **`16.dp`**: Standard card inner padding
- **`20.dp`**: Screen horizontal edge padding, hero card padding
- **`24.dp`**: Section header margins
- **`32.dp`**: Large component spacing
- **`48.dp`**: Minimum touch target size for accessible touch elements

### Corner Radii Tokens
Defined in `ZCalShapes`:
```kotlin
object ZCalShapes {
    val Chip = RoundedCornerShape(12.dp)
    val Input = RoundedCornerShape(16.dp)
    val Thumbnail = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(24.dp)
    val SheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Hero = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(percent = 50)
}
```

### Typography Scale with Tabular Figures (`tnum`)
Every numerical style enables `fontFeatureSettings = "tnum"` so animated numbers and macro totals never jitter horizontally during spring animations:
- **`Numeral`**: `48 / 52 sp`, Weight 700 (Calorie ring center display)
- **`Display`**: `40 / 44 sp`, Weight 700 (Review breakdown, goal results)
- **`Headline`**: `28 / 34 sp`, Weight 600 (Top screen titles)
- **`Title`**: `20 / 26 sp`, Weight 600 (Card titles, food item names)
- **`Body`**: `16 / 24 sp`, Weight 400 (Body copy, descriptions)
- **`BodySmall`**: `14 / 20 sp`, Weight 400 (Subtitles, helpers, time labels)
- **`Label`**: `12 / 16 sp`, Weight 500 (Chips, axis labels, badges)

---

## 2. Component Library

### Components Overview
Located under [`app/src/main/java/app/zcal/ui/components/`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/java/app/zcal/ui/components/):
- **`CalorieRingCard`**: 220 dp ring, 14 dp stroke, round caps, track in `surfaceVariant`, progress in `primary`. Over-goal draws an inner 8 dp lap in `carbs` amber. Displays remaining kcal in `Numeral` with accessible TalkBack descriptions.
- **`MacroRowCards`**: 3 equal-width cards for Protein (P), Carbs (C), and Fat (F). Displays eaten / goal grams in tabular figures with 6 dp rounded pill progress bars.
- **`FloatingBottomNav`**: 64 dp floating pill bar with 16 dp side margin, featuring a 56 dp elevated circular Camera/Scan button and glass-surface navigation tabs.
- **`ConfidenceAndMealCard`**: High / Medium / Low confidence badges with colored indicators and accessible textual descriptions; meal thumbnail with 16 dp radius.
- **`AnalyticsCharts`**: Interactive 7-day, 30-day, and 90-day calorie bar charts and macro distribution donuts.

---

## 3. Frameworks & Libraries

- **UI Framework**: Native Android Jetpack Compose with Material 3 (`androidx.compose.material3`).
- **Compose BOM**: `2024.11.00`.
- **Dependency Injection**: Google Dagger Hilt `2.52`.
- **Database**: Android Jetpack Room `2.6.1` with KSP.
- **Async Runtime**: Kotlin Coroutines `1.9.0` with `StateFlow` and `SharedFlow`.
- **Networking**: Ktor `3.0.1` on OkHttp engine with `kotlinx.serialization`.
- **Camera & Barcode**: CameraX `1.4.0` & Google ML Kit Barcode Scanning `17.3.0`.
- **Image Pipeline**: Coil Compose `2.7.0`.
- **Health Sync**: Android Health Connect `1.1.0-alpha10` + Hardware Step Sensor fallback.
- **Build System**: Gradle 8.9 with Android Gradle Plugin `8.7.2` & Java 21 / Kotlin 2.0.21.

---

## 4. Asset Management

### In-App Drawables
Located under [`app/src/main/res/drawable/`](file:///home/u1m/Documents/ZERAVRIX/ZCal/app/src/main/res/drawable/):
- `app_logo_transparent.png`: High-resolution transparent ZCal logo used for in-app header, onboarding, and boot screen.
- `app_logo_dark.png`: Dark-mode square branded icon with `#181621` background.
- `splash_logo.png`: 288x288 logo optimized for the Android 12 SplashScreen API.
- `ic_launcher_foreground.png`: 432x432 adaptive launcher icon foreground positioned inside the safe mask.

### Launcher Mipmaps
- Mipmap densities provided: `mdpi` (48px), `hdpi` (72px), `xhdpi` (96px), `xxhdpi` (144px), `xxxhdpi` (192px).
- Adaptive XML in `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` pairing `@color/ic_launcher_background` (`#23212C`) with `@drawable/ic_launcher_foreground`.

---

## 5. Icon System

- Standard Android Material Icons Extended: `androidx.compose.material.icons.Icons`.
- Auto-mirrored navigation and direction icons (`Icons.AutoMirrored.Outlined.DirectionsWalk`).
- High-contrast iconography with accessible content descriptions for screen readers.

---

## 6. Styling Approach

- Declarative Jetpack Compose styling using `LocalZCalColors.current`.
- Dark Mode and AMOLED Pure Black mode support from day one.
- No heavy dropshadows; clean surfaces with 1 dp `outline` borders.
- Spring animations (`Spring.DampingRatioLowBouncy`) and duration-controlled transitions.

---

## 7. Project Structure

```
app/src/main/java/app/zcal/
├── MainActivity.kt               # Single Activity, Camera-First NavHost, Boot Screen
├── ZCalApplication.kt           # Application class, Hilt Android App, Worker Init
├── di/
│   └── AppModule.kt             # Hilt modules (Room, Ktor, KeyVault, HealthConnect)
├── data/
│   ├── ai/                      # AI Analysis (Gemini + OpenAI/Ollama), ModelSelector, Validators
│   ├── cache/                   # Monthly image auto-purge cache manager
│   ├── food/                    # Open Food Facts API client
│   ├── health/                  # Health Connect & Hardware Step Counter Manager
│   ├── local/                   # Room Entities, DAOs, and TypeConverters
│   ├── repo/                    # Repositories (Meal, Profile, Weight)
│   └── secure/                  # Android Keystore AES-GCM KeyVault
├── domain/
│   ├── model/                   # Domain entities (Meal, FoodItem, Goals, DaySummary)
│   └── usecase/                 # Business logic (ComputeGoals, AnalyzeMeal, AdaptiveTdee)
├── ui/
│   ├── cache/                   # History Cache screen
│   ├── camera/                  # CameraX live viewfinder with heads-up display (HUD)
│   ├── components/              # Reusable design system cards, rings, nav, charts
│   ├── home/                    # Daily Dashboard with Calorie Ring & Macro Rows
│   ├── onboarding/              # First-time target calculator & API key verification
│   ├── progress/                # Month calendar heat map & trend analytics
│   ├── review/                  # Animated nutritional breakdown & item editor
│   ├── settings/                # BYOK, model selector, theme, export & calibration
│   └── theme/                   # Color tokens, Typography, Shapes, Theme engine
├── util/                        # DateHelpers, ImageResizer, ExportHelper
├── widget/                      # Glance AppWidget (4x2 home screen calorie ring)
└── worker/                      # WorkManager background sync & cache cleanup workers
```
