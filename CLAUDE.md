# Learn Anything: Android AI tutor

Working name only. Package placeholder: `com.example.learnanything` (the owner will rename it).

## What the app does

A personal Android tutor. The learner types a goal ("SQL joins", "basics of thermodynamics"). AI builds a course as Subject > Chapter > Topic. Each topic is a short, interactive lesson. Each chapter ends with a quiz and a practical that the app verifies. Progress is stored on the phone and backed up to the learner's Google Drive, so it can be restored on another phone. AI can come from free cloud models, the learner's own paid API key, or a model running on the phone.

## Decisions log

- **2026-11-16 (M0):** Room 3.0.3 instead of Room 2.x. Navigation 3. OkHttp, not Ktor. minSdk 26. Versions were verified on Maven Central and Google Maven that day and are pinned in `gradle/libs.versions.toml`.
- **2026-11-16 (M0), Gemini structured output:** the current REST docs put the schema at `generationConfig.responseFormat.text.{mimeType: "APPLICATION_JSON", schema}`, while the SDKs still use `responseMimeType` with `responseJsonSchema`. `GeminiProvider` sends the REST form first and switches to the other form once if the server rejects the field. Google also now documents an "Interactions API" (`/v1beta/interactions`). We use `generateContent` until there's a reason to move.
- **2026-11-16 (M0), model choice:** the model list always comes live from `models.list`. `ModelRanker` pre-selects one by naming traits (stable over preview, "flash" preferred, non-text models excluded), never by fixed names. The learner can override it in Settings.
- **2026-11-16 (M1):** question types the code can grade are single choice, multiple choice, number with tolerance and unit, and short exact text. Free-text answers wait for M2 rubric grading. Lesson text supports only **bold**, `inline code`, code blocks and "- " lists, rendered natively (no HTML or WebView). Any AI text containing a URL is rejected. Mastery is an exponential moving average (alpha 0.35). Reviews follow a fixed ladder of 1, 3, 7, 16 and 35 days, moving once per study session, and a wrong answer drops back to day 1. In M1 a chapter is finished when all topics are done and the quiz scores 80% or more; practicals join the gate in M2. Topics aren't locked, but there's a suggested next step. A failed quiz retake never replaces an earlier pass. Lessons are generated only when a topic is opened, with no prefetch.
- **2026-11-16 (M1 fixes from the first device run):** Gemini 5xx "busy" errors are retried twice (after 2 s and 6 s) before falling back. Gemini streams thought summaries (`thinkingConfig.includeThoughts`) so long replies don't hit the read timeout (now 300 s); the field is dropped if a model rejects it. Gemini rejected our schemas as having "too many states", so `GeminiSchema` sends a shape-only schema (no length, count or range limits) and puts the full schema in the system instruction; replies are still validated locally against the full schema. If even the shape-only schema is rejected, the call asks for plain JSON. Error messages now show the detail line.
- **2026-11-16 (M2):** Practicals are stored as `Item` rows (role PRACTICAL) and the best result in `chapter.practicalResult` (Room v3, auto-migration). A chapter is finished when its topics are done, the quiz is passed and the practical is passed; quizzes passed before M2 (`requiresPractical` missing) keep the chapter finished. Kinds: python, javascript, sql, numeric (Exact) and written (AI-graded). Code tasks pass only when every hidden test passes; written answers pass at 70% of rubric points, and every "met" criterion must quote the learner's text word for word (checked by code). Generate, then validate: a code task is discarded if its reference fails its tests or its starter code passes them; we try twice. Runners: Pyodide 314.0.7 (core files only, about 13.5 MB) and sql.js 1.16.0, bundled in `feature/practical/src/main/assets/runner`, run in Web Workers inside a hidden WebView served by `WebViewAssetLoader` (androidx.webkit 1.18.0). Every other request is refused, the page has a strict CSP, there is no JavaScript bridge (the app polls with `evaluateJavascript`), and runs are time-limited (the worker is terminated). `node feature/practical/runner-tests/run.mjs` tests the runners.
- **2026-11-17 (M3):** Lesson prompt v2 (5 to 8 cards of 80 to 180 words, common mistakes and recap); rules send back lessons with fewer than 5 cards or an average under 60 words. **Rewrite lesson** regenerates one topic on request. The rescue ladder has all 8 rungs: six are AI-written (`rescue` call, one schema with per-rung rules), fill-in-the-blank answers are checked by code, and teach-back is graded by rubric (AI-graded, quotes checked by code). After each rung one lesson check question decides whether it helped (`RescueEvent`); the ladder then starts from the rung that has helped most. A `resources` call returns YouTube search queries, an optional PhET catalog key and a diagram description, never URLs. Videos: YouTube Data API `search.list` (embeddable, syndicated, strict safe search, short then medium length) with the learner's optional key, stored encrypted like the Gemini key; without a key the app shows a YouTube search chip. The chosen video is stored per topic (`Resource`); thumbs down forgets it. PhET: 75 allow-listed HTML5 sims (each URL checked 2026-11-17), with CC BY 4.0 attribution shown. `ContentHost` (core/ui) is the one WebView for outside content: only the YouTube nocookie embed host or PhET may load in the frame, other links open in a Custom Tab (androidx.browser 1.10.0), no JavaScript bridge, no autoplay, and the embed gets an app Referer. AI-built visuals are deferred; a diagram description is shown instead. Room v4 adds resource, rescue_event and learner_profile.
- **ML Kit `genai-prompt` is still beta only** (1.0.0-beta5 as of 2026-11-16). Re-check before M5.

## Owner context

- The owner is an experienced Salesforce developer and architect, but newer to Android. When a step needs Android Studio, Google Cloud Console or the phone itself, give exact click-by-click instructions.
- The owner builds and runs the app in Android Studio on a real phone. On-device models do not run reliably on emulators.
- Personal use first. A Play Store release may come later, so avoid choices that would block one.

## Non-negotiable principles

1. **AI writes the structure; code does the checking.** Wherever an answer can be checked by code (exact match, numeric tolerance, unit tests, SQL result sets), code checks it, not the model.
2. **Every AI response is JSON, validated against a schema.** On failure, retry once with the validation error, then fall back to the next provider. Never render unvalidated model output.
3. **The AI never supplies URLs.** It returns search queries or catalog keys (for example `phet:states-of-matter`), and the app builds the URL itself.
4. **Room is the single source of truth.** Screens never call a model, the network or Drive directly; they go through use cases and repositories.
5. **Never hard-code model names or free-tier quotas.** List models from each provider at runtime, and treat rate-limit errors as normal.
6. **Clutter-free UI.** One primary action per screen, with detail in bottom sheets.

## Tech stack

Before adding any dependency, check its current stable version on Maven Central or the official docs. Do not trust versions remembered from training data.

- Kotlin, Jetpack Compose, Material 3 (dynamic colour, true dark theme)
- MVVM with unidirectional state (`StateFlow`), Hilt, Kotlin coroutines and Flow
- Room 3 (`androidx.room3`, KSP only, coroutine-only DAOs, `AndroidSQLiteDriver`) with UUID string primary keys, `updatedAt` on every row, and `deletedAt` tombstones for deletes. The exported schema lives in `core/data/schemas` and is committed.
- DataStore for settings. API keys are encrypted with AES-256-GCM under an Android Keystore key (not `security-crypto`, which is deprecated) and are never backed up. Android Auto Backup is off (`allowBackup=false` plus data extraction rules).
- OkHttp 5 (server-sent-event streaming read directly from the response body), plus kotlinx.serialization
- JSON Schema validation: `io.github.optimumcode:json-schema-validator` (draft 2020-12, built on kotlinx.serialization)
- Navigation 3 (`androidx.navigation3`), with ViewModels scoped to back-stack entries
- WorkManager for model downloads and backups
- Gradle Kotlin DSL with a version catalog (`libs.versions.toml`) and convention plugins in `build-logic/`. AGP 9 compiles Kotlin itself, so don't apply `org.jetbrains.kotlin.android`, and don't use kapt.
- `minSdk 26`: the AAR manifests require LiteRT-LM 24, ML Kit `genai-prompt` 26 and Room 3 23. `compileSdk 37`, `targetSdk 36`. All of these are set in `build-logic/convention/src/main/kotlin/ProjectConfig.kt`.

### Module layout

```
app/
core/model      // domain types, JSON schemas
core/data       // Room, repositories, DataStore
core/ai         // AiProvider interface, router, cloud providers, validation, cache
core/ui         // theme, shared components, ContentHost (WebView)
ai/local        // LiteRT-LM + ML Kit Prompt API implementations of LocalLlm
feature/home, feature/create, feature/subject, feature/topic,
feature/test, feature/practical, feature/progress, feature/settings
```

## AI layer

```kotlin
interface AiProvider {
    val id: String
    suspend fun listModels(): List<ModelInfo>
    fun generate(request: AiRequest): Flow<AiChunk>   // streaming
}
interface LocalLlm : AiProvider {
    suspend fun status(): LocalStatus                  // unavailable / downloadable / ready
}
```

**Cloud providers.** Gemini API (AI Studio key; the free tier is Flash and Flash-Lite models), Groq, OpenRouter (`:free` models), and a generic OpenAI-compatible endpoint where the user enters base URL, key and model. Paid keys for OpenAI, Anthropic or Gemini plug in the same way.

**On-device engines, in priority order:**
1. **Gemini Nano through the ML Kit GenAI Prompt API** (`com.google.mlkit:genai-prompt`). There is no download, but only listed phones support it, it works only in the foreground, and each app has a quota. Use it for hints and rephrasing only. Call `checkStatus()` first.
2. **Gemma 4 E2B through LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`). This is the default offline model. The model file comes from Hugging Face `litert-community/gemma-4-E2B-it-litert-lm` (about 2.58 GB), and the download may need a Hugging Face token. Use the GPU backend when OpenCL is available, otherwise the CPU. Use LiteRT-LM's JSON-schema constrained decoding (`response_format`). Use Google's open-source AI Edge Gallery app (github.com/google-ai-edge/gallery) as the reference implementation.
3. **Later (M6, do not build now):** llama.cpp through the NDK, for Qwen3.5 GGUF models.

Do not use the MediaPipe LLM Inference API, which is in maintenance-only mode.

**Router.** Each call has a tier. Strong-tier calls (`outline`, `practical`, `grade`) prefer the strongest connected cloud model. Small and medium calls (`lesson`, `rescue`, `hint`) may use the on-device model, and offline always means on-device. Never grade open-ended answers with an on-device model.

**Calls.** `clarify`, `outline`, `lesson`, `quiz`, `practical`, `grade`, `rescue`, `resources`. Each has a versioned prompt template and a JSON schema in `core/model`. Cache every response in Room by a hash of the prompt.

**Model choice by RAM** (read `ActivityManager.MemoryInfo.totalMem`):
- Nano available: Nano for hints, plus an offer to download Gemma for lessons
- 6 to 8 GB: Gemma 4 E2B
- 12 GB or more: offer Gemma 4 E4B
- Under 6 GB: cloud only

Model downloads run in WorkManager: resumable, Wi-Fi only by default, checksum verified, and excluded from backups.

## Learning loop

- **Create a course:** goal, level and weekly time, then 2 or 3 clarifying questions, then an outline the learner can edit. Lesson content is generated only when a topic is first opened.
- **Topic:** why it matters, swipeable explanation cards, a worked example, an inline "try it" exercise, then 2 or 3 check questions that update mastery.
- **"I don't get it" rescue ladder,** one rung per tap, stopping when a check question is answered correctly:
  1. Simpler words
  2. An analogy from the learner's profile
  3. A worked example with blanks
  4. A short video clip
  5. A simulation or an AI-built visual
  6. Socratic questions
  7. Teach-back
  8. A prerequisite check
- Record which rung helped (`RescueEvent`) and start there next time.

## Tests and practicals

- **Chapter gate:** a quiz (default pass mark 80%) plus one practical. Every result shows a trust label: Exact or AI-graded.
- **Code practical (Python, JavaScript):** runs in a sandboxed WebView against hidden unit tests. Generate, then validate: the AI also writes a reference solution, and the app discards any task where the reference fails its own tests.
- **SQL practical:** runs in SQLite compiled to WebAssembly, and result sets are compared.
- **Numeric practical:** the answer is computed with a tolerance and units are checked.
- **Written answers:** rubric grading by a strong cloud model, quoting the learner's text as evidence for each criterion.

## Content host (one WebView composable)

- **YouTube:** use the IFrame embed player. Set an HTTP Referer that identifies the app. The player must be at least 200 by 200 dp, and autoplay may start only when more than half of it is visible. Find videos with YouTube Data API `search.list` using the learner's own key (filter to embeddable, short videos) and cache the chosen ID per topic. Without a key, show a search chip instead.
- **PhET simulations:** use an allow-list of catalog entries with the required attribution, and keep the PhET logo visible.
- **Code runners:** bundle Pyodide locally so they work offline.
- **AI-built visuals:** a self-contained HTML page in a WebView with no network access.
- **Security:** an allow-list of hosts, no JavaScript bridge on external pages, and anything off the list opens in a Custom Tab.

## Data model (Room)

| Entity | Fields |
| --- | --- |
| Subject | title, goal, level, status |
| Chapter | subject, order, title, goal, gate result |
| Topic | chapter, order, objectives, prerequisites, lesson JSON |
| Item | owner (topic or chapter), kind, payload, answer key or tests |
| Attempt | item, answer, score, feedback, trust, hints used |
| Concept | name, mastery, next review date |
| Resource | topic, type, source reference, start and end, rating |
| RescueEvent | topic, rung, helped |
| LearnerProfile | background, known skills, ladder order |
| AiCache | prompt hash, response; not backed up |

## Backup (M4)

- **Sign-in and access:** Credential Manager for sign-in, then `AuthorizationClient` for the `drive.appdata` scope. Do not use the deprecated GoogleSignIn classes.
- **Backup format:** a zip of JSON (one file per table plus a schema version) uploaded to Drive's `appDataFolder`. Keep the last 5 snapshots.
- **Excluded from backups:** keys, model files and the AI cache.
- **When it runs:** after a chapter is finished (debounced), daily on an unmetered network, and on a manual button.
- **Restore:** Replace or Merge (newest `updatedAt` wins, tombstones are honoured).
- **Export:** the same zip through the Storage Access Framework.

## Screens

Bottom navigation: **Home** (Continue card, reviews due), **Library**, **You** (progress, settings).

Other screens: New subject, Subject map, Topic, Chapter test, Practical workspace, Settings (providers and fallback order, local model manager, backup, privacy notes).

## Milestones (stop at each gate and report)

| Milestone | Scope | Gate |
| --- | --- | --- |
| M0 Foundations | Project skeleton, modules, theme, Room, one cloud provider (Gemini), the outline call with schema validation, settings for the API key | An outline is generated and saved on the phone |
| M1 Core loop | Course creation, lazy lessons, topic cards, quiz, mastery, progress screens | The owner finishes one real chapter |
| M2 Practicals | Pyodide and SQLite runners, generate-then-validate, rubric grading | A coding task fails on a wrong answer and passes on a right one |
| M3 Enrichment | YouTube clips, PhET simulations, the rescue ladder | A hard topic becomes clear |
| M4 Drive backup | Backup, restore, merge, zip export | A second phone restores the same progress |
| M5 On-device AI | LiteRT-LM with Gemma 4, Gemini Nano, model manager, router | A lesson generates in airplane mode |

## Working rules

- Plan before coding each milestone: list the files, the dependencies (with verified current versions) and the open questions, then wait for approval.
- Keep `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` passing at every commit. Write unit tests for schema validation, the router, grading and backup merge.
- Small, focused commits, with conventional commit messages.
- Put secrets in `local.properties` or the in-app settings, never in source control.
- If documentation contradicts this file (for example, an API was renamed), follow the documentation and tell the owner what changed.
- Update this file when a decision changes.
