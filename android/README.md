# Sakhi Android

Native Kotlin rewrite of the Sakhi clinical decision-support app. ASHA workers get fully offline AI inference (LiteRT-LM, Gemma 4 E2B) — no cloud dependency for core clinical function. Supabase handles auth and opportunistic sync.

**Min SDK:** API 26 (Android 8.0) · **Target:** API 35 · **Language:** Kotlin 2.0 + Jetpack Compose

---

## Setup

### 1. `local.properties`

The file lives at `android/local.properties` (not committed). Add your Supabase credentials:

```
sdk.dir=/Users/<you>/Library/Android/sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJh...
MODEL_DOWNLOAD_URL=
MODEL_SHA256=
```

Leave the last two blank — debug builds use `MockInferenceEngine` and skip the model download.

### 2. Supabase

1. Create a project at [supabase.com](https://supabase.com)
2. Enable Phone auth: **Authentication → Providers → Phone → Enable**
3. Set a test OTP (emulators can't receive SMS): **Authentication → Providers → Phone** — add a test phone number and a fixed OTP (e.g. `+919999999999` / `123456`)
4. Create the database tables using the schema in [TODO.md](TODO.md) → "Room DB Schema" section

### 3. Android Studio

Open the **`android/`** subfolder — not the repo root. Android Studio will detect the Gradle project and sync automatically.

Create an AVD: **Device Manager → Create Virtual Device → Pixel 6 → API 35, arm64-v8a → Finish**

### 4. FTS5 Guideline Index (optional)

RAG injects MOHFW/WHO guideline context into Ask Sakhi prompts. Without this, the chat still works — just no guideline citations.

```bash
cd /path/to/sakhi
pip install PyMuPDF
python android/scripts/build_fts_index.py
# Writes android/app/src/main/assets/guidelines_fts.db
```

---

## Running the App

Hit **Run** in Android Studio (debug variant). The app starts at Onboarding since there's no session.

```bash
# From android/ directory
./gradlew :app:installDebug          # install on connected device/emulator
./gradlew test                       # JVM unit tests (no emulator)
./gradlew :core:domain:test          # domain layer tests only
```

---

## Module Structure

| Module | Contents |
|---|---|
| `:app` | `MainActivity`, `SakhiNavHost`, `SakhiApplication`, Hilt root modules |
| `:feature:auth` | Onboarding, phone OTP screens |
| `:feature:home` | Patient list, add patient, settings |
| `:feature:checkup` | ANC + newborn checkup forms, assessment screen |
| `:feature:chat` | Ask Sakhi multi-turn chat |
| `:core:domain` | Pure Kotlin — domain models, repository interfaces, `InferenceEngine` interface |
| `:core:data` | Room (SQLCipher), DAOs, `SyncWorker`, `DownloadWorker`, repository impls, `AuthPreferences` |
| `:core:inference` | `MockInferenceEngine`, `LiteRTInferenceEngine`, `PromptBuilder`, `ResponseParser` |
| `:core:network` | `SupabaseClient`, `SupabaseSyncApi`, `SupabaseAuthManager` |
| `:core:rag` | FTS5 guidelines database, `RagRepository` |
| `:core:ui` | `SakhiTheme`, shared composables |

---

## Key Design Decisions

**On-device inference:** `LiteRTInferenceEngine` wraps LiteRT-LM v0.10.1 (`Engine`/`Conversation` API). The model is arm64-only — the emulator uses `MockInferenceEngine` (selected via `BuildConfig.USE_MOCK_INFERENCE`).

**Encrypted storage:** Room uses SQLCipher with a `SHA-256(userId + Keystore_deviceSecret)` passphrase (as `ByteArray` — never String). Tokens and user identity live in `EncryptedSharedPreferences`.

**Sync:** Last-write-wins on `last_modified_at`. SyncWorker pushes `dirty=1` records, then pulls server changes since `lastSyncAt`. Chat messages are not synced (DISHA minimum data principle).

**DISHA compliance:** Consent required at onboarding. ProGuard strips all `Log.*` in release. Account deletion wipes Room → EncryptedSharedPrefs → Keystore → model file in that order.

---

## Secrets

| Key | Where |
|---|---|
| `SUPABASE_URL` | `local.properties` → `BuildConfig.SUPABASE_URL` |
| `SUPABASE_ANON_KEY` | `local.properties` → `BuildConfig.SUPABASE_ANON_KEY` |
| `MODEL_DOWNLOAD_URL` | `local.properties` → `BuildConfig.MODEL_DOWNLOAD_URL` (release only) |
| `MODEL_SHA256` | `local.properties` → `BuildConfig.MODEL_SHA256` (release only) |

`local.properties` is in `.gitignore`. Never commit it.
