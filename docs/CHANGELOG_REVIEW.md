# ZCal 2.0.1 — review & fix log

Static review of the Argon-generated 2.0.0 source. No Kotlin compiler or network was available in the review
environment, so **nothing here has been built or run**: first step on your machine is `./gradlew testDebugUnitTest assembleDebug`.

## Build-breaking
| File | Problem | Fix |
|---|---|---|
| `PromptBuilder.kt` | Unescaped `"` inside string literals (user note / correction lines) — does not compile | Escaped |
| `ExportHelper.kt` | Same defect in CSV writing (`replace(""", """")`) — does not compile | Rewritten: RFC-4180 quoting |

## Data-loss / correctness
| Area | Problem | Fix |
|---|---|---|
| Room | `fallbackToDestructiveMigration()` would wipe the whole food log on any future schema change | Destructive only from dev versions 1–2; DB is v3, `exportSchema = true` — future changes need real migrations |
| JSON export | Exported only `meals.map { it.items }` (meal data lost) | Full meal + item export |
| Image cache purge | Deleted whole previous-month folders but left `photoPath` pointing at deleted files; could delete queued offline-scan photos | Purge per entry, null the meal's `photoPath`, spare queued scans, only sweep files past TTL |
| History cache | Editing a meal inserted a duplicate cache row each time | One row per meal (`replaceHistoryCacheEntry`) |
| Day completeness | A day became "complete" as soon as the 2nd meal was logged, defeating the honesty rule | Only past days auto-complete (daily worker) or user toggles |
| Offline queue | Each Retry tap queued the same photo again; invalid key silently deleted the queued photo | De-duplicated; photo kept on key errors; retries capped at 5; calibration now applied |
| Calibration | Scaled kcal/macros but not kJ or CLA (inconsistent numbers) | kJ and CLA scaled too |
| Adaptive TDEE | Divided weight change by *number of logged days*, not calendar span | Same calendar window for food + weight; requires ≥14 days and ≥60 % logged |
| Health Connect | Every edit wrote another NutritionRecord (duplicates) | `clientRecordId`/version → update in place |
| Delete / reset | Deleting a meal left its photo; "reset all data" kept pending scans, saved meals, barcode cache | Cleaned up |

## AI layer
- OpenAI-compatible path sent `json_object` mode **without the field names** — model had to guess the schema. Added `JSON_SHAPE_HINT`.
- Ktor timeouts (`HttpRequestTimeoutException`, Ktor `SocketTimeoutException`) fell into the `IOException` branch and were reported as *Offline*. Now reported as Timeout.
- `CancellationException` was swallowed by `catch (Exception)`. Now re-thrown.
- HTTP 400 was always "invalid key". Now only when the body says so; otherwise a model/base-URL hint with the provider's message.
- Gemini: concatenates all non-thought text parts (thinking models can return several parts). Image base64 moved off the main thread.
- Request timeout 45 s → 90 s (vision + thinking on the free tier).

## Android / platform
- Widget opened a second Room instance on every refresh and never closed it → now uses the app's singleton DAO via a Hilt `@EntryPoint`; refreshed after save/delete.
- Launcher icon was a bare foreground vector → proper adaptive icon (+ monochrome).
- Health Connect permission-usage screen is an `activity-alias` guarded by `START_VIEW_PERMISSION_USAGE` (required on Android 14+).
- `ACTIVITY_RECOGNITION` was declared but never requested, so the step sensor never delivered → requested after the camera prompt.
- `file_paths.xml` pointed at `meals/` but photos live in `meals_cache/`.
- R8: added Ktor/slf4j `-dontwarn` and the EntryPoint keep rule.
- Open Food Facts: kJ-only products now fall back to `energy_100g / 4.184`.

## New in 2.0.1: automatic best-model selection
Default model is now `auto` (both providers). `ModelSelector` (pure, unit-tested) ranks the models a key can use for
*this* job: photo in, structured nutrition JSON out. Flash / "mini" tier first, then Pro, then Lite; newest generation
within a tier; stable beats preview unless "Allow preview models" is on. Non-vision and special-purpose models
(image, tts, audio, live, embedding, thinking, exp) are never chosen.
- Gemini: lists `GET /v1beta/models` (only `generateContent` models). OpenAI-compatible: `GET /models`; also understands
  OpenRouter ids and local Ollama vision models (qwen-vl, gemma3, llama3.2-vision, llava).
- Verify & Save probes the top three candidates with a real call (a model can be listed yet unusable on a free key).
- The pick is cached for 7 days (newer models are adopted without an app update) and re-picked at once if the model
  returns HTTP 404 (retired). If the list cannot be fetched, the last good pick, else `fallbackModel`, is used.
- Typing a model ID in Settings still overrides auto.
- Files: `ModelSelector.kt` (new), `GeminiAndCompatAnalyzer.kt`, `KeyVault.kt`, `Models.kt`, Settings/Onboarding text, 4 new unit tests.

## New in 2.0.1: provider, key and model freedom in Settings
- **Choose from my key's models**: lists what the key can use (best for food photos first, then the rest) plus an
  "Auto (recommended)" entry; tapping one fills the model field.
- Provider, base URL, key and model can each be changed on their own. Leave the key field blank to change only the
  model or URL: the stored key is used for the check.
- **Safe saving**: new settings are verified with a real call and kept only if they work. A failed check restores the
  previous provider/model/URL, and the typed key stays in the field instead of being cleared.
- Base URL is editable for Gemini too (proxy use), with hints for OpenRouter and Ollama.

## Known limitations (not changed)
- Step sensor "today" is steps since the app first listened, not since midnight. Health Connect is accurate; the sensor fallback is approximate.
- Cleartext HTTP is allowed only for `localhost`, `127.0.0.1`, `10.0.2.2`. A LAN Ollama server (192.168.x.x) needs HTTPS or a custom network-security entry.
- `health-connect-client` is `1.1.0-alpha10`; move to the newest stable and re-check the `Metadata(...)` constructor.
- UI screens (Home, Progress, Review, Onboarding, Camera, Settings, charts) were only spot-checked, not line-reviewed.
- "Best" is a rule (tier, generation, stability), not a measured benchmark. Test auto on ~20 of your own meals and pin a model if another does better.
- Not an upstream-licence issue as far as reviewed, but confirm no Fud AI code or assets were copied in (`LICENSE`, README).
