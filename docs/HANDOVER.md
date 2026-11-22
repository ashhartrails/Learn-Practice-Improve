# Handover: where the project stands

Read this first when you pick the project up in a new session, then `CLAUDE.md` (the spec, rules and decisions log) and `docs/SETUP.md` (the owner's click-by-click steps).

_Last updated: 2026-11-22._

## Where the code is

- **Branch:** `claude/keen-bohr-tuhx2n`. All work is on this branch.
- **Pull request:** https://github.com/ashhartrails/Learn-Practice-Improve/pull/1 (draft, covers M0 to M4.5). Not merged yet.
- **Last commit at handover:** `0f31295` "Groq and OpenRouter as free fallback providers".

## Milestone status

| Milestone | Built | Owner's gate on device |
| --- | --- | --- |
| M0 Foundations | Yes | Passed |
| M1 Core loop | Yes | Passed |
| M2 Practicals | Yes | Passed |
| M3 Enrichment | Yes | Passed ("better"; lesson quality is ongoing work) |
| M4 Drive backup | Yes | **Not yet run.** Needs the one-time Google Cloud setup (docs/SETUP.md, "M4: Google Drive backup") |
| M4.5 Free fallbacks (Groq, OpenRouter) | Yes | **Not yet tried** with real keys |
| M5 On-device AI | No | Next milestone: plan it first and wait for approval |

## Next steps, in order

1. **Wait for the owner's results:**
   - The M4 gate: a second phone restores the same progress. Exporting a file on one phone and importing it on the other is the fallback test.
   - The Groq and OpenRouter keys, tried with real replies.
   - Fix whatever comes back. Ask for the full error text, including its "Detail:" line.
2. **M5 On-device AI.** Write the plan first, list the dependencies with versions verified that day, and wait for approval. In scope:
   - LiteRT-LM with Gemma 4 E2B (download about 2.58 GB, run by WorkManager: resumable, Wi-Fi only by default, checksum verified).
   - Gemini Nano through ML Kit `genai-prompt` (it was beta only on 2026-11-16, so re-check).
   - A model manager in Settings.
   - Router tiers: lessons, rescue and hints may run on the device; outlines, practicals and grading never do; offline always means on-device.
   - The gate: a lesson generates in airplane mode.
   - Note: on-device models don't run reliably on emulators, so the owner must test on the phone.
3. **Known follow-ups (not blocking):**
   - Let the learner set the fallback order (it is fixed today: Gemini, then Groq, then OpenRouter).
   - A generic OpenAI-compatible provider where the learner enters the base URL, key and model.
   - AI-built visuals (a self-contained HTML page with no network access) for the simulation rung. A text diagram description stands in for now.
   - Lesson quality and length keep needing tuning. The prompt is `LessonPrompt` (v2) and the rules are `LessonRules`. The minimum average is 40 words per card.
   - The app is still the placeholder package `com.example.learnanything`. Renaming it later needs a new Google OAuth Android client.

## How to build and check

```bash
export ANDROID_HOME=/root/android-sdk
./gradlew assembleDebug testDebugUnitTest --max-workers=2
node feature/practical/runner-tests/run.mjs
```

- **Expected results:** the build passes, all unit tests pass (184 at handover, including Robolectric smoke tests that walk the app), and the runner checks print "12 runner checks passed".
- **If Maven returns 429:** that's a download limit, so run the command again.
- **No emulator in the cloud container** (no KVM). Robolectric screenshots go to `app/build/smoke-screens/`.
- **Cold start:** a new container may need a first `./gradlew assembleDebug` to fill the Gradle cache.

## Working agreement with the owner

- The owner is a Salesforce architect who is newer to Android. Give exact click-by-click steps for Android Studio, Google Cloud Console and the phone.
- For each milestone: plan, wait for approval, build, keep both Gradle commands green at every commit, make small conventional commits, push, update the PR, then stop at the gate and report.
- Never commit secrets. Keys live in the app's encrypted settings, and the optional `google.webClientId` lives in `local.properties`.
- Update the `CLAUDE.md` decisions log whenever a decision changes.

## Map of the code

- `core/model`: domain types, prompts, JSON schemas (`src/main/resources/schemas`) and the pure rules (grading, mastery, rescue ladder, backup merge, PhET catalog).
- `core/ai`: providers and the call pipeline.
  - `StructuredCaller`: cache, then the provider, then the schema check and rules, then one retry with the errors, then the next provider. It also retries busy errors.
  - Providers: `GeminiProvider` (with `GeminiSchema` shape-only schemas), `OpenAiCompatibleProvider` (Groq and OpenRouter) and `YouTubeVideoSearch`.
- `core/data`: data and use cases.
  - Room v4 with schemas committed in `core/data/schemas`, plus repositories and use cases.
  - Backup: `BackupStore`, `BackupZip`, `DriveClient`, `DriveAuth` and `BackupRepository`.
  - Settings: DataStore settings with Keystore-encrypted keys.
- `core/ui`: the theme, `LessonText`/`RichText`, question views, and `ContentHost`, the locked-down WebView that opens outside links in Custom Tabs.
- `feature/*`: screens.
  - `create` (course creation and the outline editor), `subject`, `topic` (lesson and rescue ladder), `test` (quiz and review), `practical` (workspace, `WebCodeRunner` and the bundled Pyodide and sql.js under assets), `progress`, `home`.
  - `settings`: keys, models, About you, YouTube, More free AI and Backup.
- `app`: the `LearnApp` navigation keys, plus WorkManager backup in `backup/BackupWork.kt`.
