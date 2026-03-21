![Sakhi](sakhi-repo-banner.png)

# Sakhi — AI Clinical Companion for ASHA Workers

> **Google MedGemma Impact Challenge** · Kaggle · February 2026

Nearly one million ASHA workers in rural India make life-critical referral decisions alone, in the field, without a doctor nearby.

Sakhi (सखी, "female friend") is their AI clinical colleague: a mobile-first, offline-capable tool that assesses antenatal and newborn visits in real time, flags high-risk cases, and guides referral decisions. All powered by a fine-tuned MedGemma model grounded in MOHFW and WHO guidelines.

**[Live Demo →](https://sakhi-asha.vercel.app)** &nbsp;|&nbsp; **[Backend API →](https://docvm-sakhi-api.hf.space/health)** &nbsp;|&nbsp; **[Fine-tuned Model →](https://huggingface.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF)**

---

## Table of Contents

- [The Problem](#the-problem)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Offline-First Mode](#offline-first-mode)
- [MedGemma Integration](#medgemma-integration)
- [API Reference](#api-reference)
- [Local Development](#local-development)
- [Deployment](#deployment)
- [Screens](#screens)
- [Project Structure](#project-structure)

---

## The Problem

India's maternal mortality ratio is 97 per 100,000 live births — one of the highest among middle-income countries, and disproportionately concentrated in rural areas where specialist care is inaccessible. ASHA workers are trained to measure BP, check fetal heart rates, and observe newborns, but they are not clinicians. They have no decision-support tool, no second opinion, and no way to know whether a reading they've just taken warrants an emergency referral or a routine follow-up.

A missed sign at the right moment can cost a life. Sakhi is designed to close that gap.

---

## Key Features

| Feature | Description |
|---|---|
| **ANC Assessment** | AI risk stratification (green / yellow / red) from vitals + symptoms |
| **Newborn Visits** | Age-specific postnatal assessment (Day 1 through 6 weeks) |
| **Ask Sakhi** | Free-form Q&A with optional patient context injection |
| **Hindi Mode** | Full UI and AI responses in Devanagari Hindi |
| **Schedule View** | Follow-up calendar for all patients |
| **Offline-First Mode** | App loads and checkups work with zero connectivity — local MOHFW rule-based triage runs instantly, queues for AI sync when signal returns |
| **Fine-tuned MedGemma** | Custom QLoRA-fine-tuned, merged, and Q4_K_M-quantized model — [`docvm/sakhi-medgemma-1.5-4b-maternal-GGUF`](https://huggingface.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF) — trained on Indian maternal/neonatal clinical data |
| **ABHA Verification** | OTP-based Ayushman Bharat Health Account (ABHA) ID verification via ABDM proxy — credentials stay server-side; demo mode accepts any 6-digit OTP when sandbox creds are absent |
| **Model Cascade** | Auto-fallback across 4 providers — Fine-tuned MedGemma → Gemma 3n → Gemini → Groq |
| **Guideline-Grounded Answers** | Every assessment and chat response is augmented with top-3 relevant chunks retrieved from 10 WHO / MOHFW clinical guideline PDFs (RAG via ChromaDB + `paraphrase-multilingual-MiniLM-L12-v2`) |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   ASHA Worker's Phone               │
│                                                     │
│   React + Vite + Tailwind  (Vercel)                 │
│   ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│   │  Home /  │ │ Checkup  │ │   Ask    │           │
│   │ Patients │ │  Forms   │ │  Sakhi   │           │
│   └────┬─────┘ └────┬─────┘ └────┬─────┘           │
│        └─────────────┼────────────┘                 │
│                      │ HTTPS (VITE_API_URL)          │
└──────────────────────┼──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│           FastAPI Backend  (Hugging Face Spaces)     │
│                                                     │
│   POST /api/checkup-assessment                      │
│   POST /api/chat                                    │
│   POST /api/transcribe                              │
│                                                     │
│   ┌─────────────────────────────────────────────┐   │
│   │           model.py  (cascade)               │   │
│   │                                             │   │
│   │  1. sakhi-medgemma-1.5-4b-maternal-GGUF     │
│   │     (fine-tuned, Q4_K_M, self-hosted)       │   │
│   │     ↓ (if unavailable)                      │   │
│   │  2. Gemma 3n E4B IT  (OpenRouter, free)     │   │
│   │     ↓ (if unavailable)                      │   │
│   │  3. Gemini 2.5 Flash Lite  (Google AI)      │   │
│   │     ↓ (if unavailable)                      │   │
│   │  4. Llama 3.1 8B  (Groq, last resort)       │   │
│   └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## Offline-First Mode

ASHA workers operate in areas with unreliable or absent mobile signal. Sakhi is designed to be fully functional with no connectivity.

### How it works

| Layer | What happens offline |
|---|---|
| **App shell** | Service worker (Workbox via `vite-plugin-pwa`) caches all JS/CSS/HTML on first load — the web app opens normally with no signal |
| **Patient data** | Always available — stored in localStorage since first load |
| **Checkup submission** | Falls back to local MOHFW rule-based triage instantly; result is marked "AI review pending" |
| **Sync** | Pending submissions are queued in localStorage and replayed automatically the moment connectivity returns — no action needed from the ASHA |

### Local triage rules (`src/utils/localAssessment.js`)

The offline fallback implements a subset of MOHFW ANC and HBNC guidelines:

**ANC:** BP ≥ 140/90 → red · BP 130–139/80–89 → yellow · Hb < 7 g/dL → red · Hb < 11 g/dL → yellow · danger symptoms (fits, bleeding, severe headache) → red

**Newborn:** weight < 1.5 kg → red · weight < 2.5 kg → yellow · weight loss > 10% from birth → red · weight loss > 7% → yellow · HBNC danger signs → red

Results carry `_offline: true`. When the AI result arrives after sync, it replaces the local one in storage and on screen in-place — the ASHA doesn't need to navigate anywhere.

---

## MedGemma Integration

### Why MedGemma specifically

- **Domain-trained:** Pre-trained on medical literature and clinical data — significantly better calibrated for clinical reasoning than general-purpose LLMs at the same parameter count
- **Open-weight:** Can be self-hosted; patient data never leaves infrastructure you control, which matters for real-world PHC deployment
- **Safety-aligned:** Built-in refusal of harmful medical advice; this matches Sakhi's "support, don't replace" design philosophy
- **Edge-viable:** The 4B GGUF-quantised variant runs on CPU, making it practical for deployment in low-resource settings where GPU compute is unavailable

### Self-hosted Ollama setup (`medgemma-space/`)

The `medgemma-space/` directory is a complete, deployable Hugging Face Space that:
1. Pulls the Ollama base image
2. Downloads `docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M` — the **fine-tuned, merged, and quantized** model — from HuggingFace Hub on startup
3. Exposes an OpenAI-compatible `/v1/chat/completions` endpoint on port 7860

`backend/model.py` calls this endpoint directly and lists it **first** in the cascade. Once `MEDGEMMA_API_URL` is set to the Space URL, all requests route through the fine-tuned model before any fallback is tried.

### Fine-tuning → Merge → Quantize → Deploy

The full pipeline across three Kaggle notebooks:

1. [`model/finetuning-medgemma.ipynb`](model/finetuning-medgemma.ipynb) — QLoRA fine-tunes `google/medgemma-1.5-4b-it` on maternal/neonatal data, targeting two key gaps:
   - **Indian clinical context:** Recognition of locally prevalent risk factors (severe anaemia, eclampsia, low birth weight patterns common in Rajasthan)
   - **Output reliability:** Improving JSON schema compliance to reduce post-processing failures in production
   - Output: `docvm/sakhi-medgemma-1.5-4b-maternal` LoRA adapter on HF Hub

2. [`model/merge-and-quantize.ipynb`](model/merge-and-quantize.ipynb) — Merges the LoRA adapter into the base model (bfloat16), converts to GGUF via llama.cpp, quantizes to Q4_K_M, and pushes `docvm/sakhi-medgemma-1.5-4b-maternal-GGUF` to HF Hub.

3. `medgemma-space/start.sh` — Serves the resulting GGUF via Ollama on a HF Space.

---

## API Reference

### `POST /api/checkup-assessment`

Rate limit: 10 requests/minute per IP.

**Request**
```json
{
  "patient":      { "name": "Meena Devi", "age": 24, "gestational_weeks": 23, ... },
  "checkup":      { "bp_systolic": 138, "bp_diastolic": 88, "weight_kg": 61, ... },
  "patient_type": "anc | newborn",
  "language":     "en | hi"
}
```

**Response**
```json
{
  "risk_level":           "green | yellow | red",
  "risk_reason":          "One-sentence summary of the primary concern",
  "what_sakhi_noticed":   ["Clinical observation 1", "..."],
  "what_to_tell_patient": "Plain-language advice for the ASHA worker to relay",
  "what_to_do_next":      "Concrete next action (e.g. refer to PHC today)",
  "follow_up_date":       "YYYY-MM-DD | null"
}
```

### `POST /api/abha/request-otp`

Initiates ABDM OTP verification for a patient's ABHA number. Proxies the ABDM call server-side so `clientId`/`clientSecret` never reach the frontend. In demo mode (no ABDM credentials), returns a mock `txnId` immediately.

**Request:** `{ "abha_number": "12-3456-7890-1234" }`
**Response:** `{ "txnId": "...", "message": "OTP sent" }`

### `POST /api/abha/verify-otp`

Verifies the OTP and returns the linked ABHA profile. In demo mode, any 6-digit OTP is accepted.

**Request:** `{ "txnId": "...", "otp": "123456" }`
**Response:** `{ "name": "...", "abhaNumber": "...", "gender": "...", "yearOfBirth": "..." }`

### `POST /api/chat`

Rate limit: 20 requests/minute per IP.

**Request**
```json
{
  "messages":       [{ "role": "user | assistant", "content": "..." }],
  "patient_context": { ...patientSnapshot } | null,
  "language":        "en | hi"
}
```

**Response**
```json
{ "reply": "..." }
```

### `GET /health` / `HEAD /health`

Returns `{"status": "healthy"}`. Used by UptimeRobot to keep the HF Space warm. Accepts both GET and HEAD (UptimeRobot free tier sends HEAD).

---

## Local Development

### Backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env    # fill in at least one API key
uvicorn main:app --reload
# → http://localhost:8000
```

**Environment variables** — configure at least one model provider:

| Variable | Provider | Notes |
|---|---|---|
| `GROQ_API_KEY` | Groq (Llama 3.1) | Fastest to get — free tier at groq.com |
| `OPEN_ROUTER_API_KEY` | OpenRouter (Gemma 3n) | Routes to a free Gemma 3n model |
| `GOOGLE_API_KEY` | Gemini 2.5 Flash Lite | Google AI Studio |
| `MEDGEMMA_API_URL` | MedGemma (Ollama) | URL of your Ollama / llama.cpp server |
| `MEDGEMMA_API_KEY` | MedGemma | Only needed if your server requires auth |

Providers with missing keys are skipped automatically — no configuration of priority needed.

### Frontend

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

The Vite dev server proxies `/api/*` to `http://localhost:8000` automatically. No environment variable is needed for local development.

### Android (Capacitor)

The frontend is wrapped with [Capacitor](https://capacitorjs.com) to produce a native Android APK for the Google Play Store. The React source is unchanged — Capacitor runs it inside a WebView.

**Prerequisites (one-time):** Install [Android Studio](https://developer.android.com/studio) and set `ANDROID_HOME` in your shell.

```bash
cd frontend

# After any React source change, sync to the Android project:
npm run build:android

# Open the Android project in Android Studio (run emulator / build APK from here):
npm run open:android
```

To build a release AAB for the Play Store:
```bash
cd frontend/android
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

---

## Deployment

| Layer | Platform | Config file |
|---|---|---|
| Frontend (web) | Vercel | Root dir: `frontend`, framework: Vite |
| Android app | Google Play Store | [frontend/capacitor.config.ts](frontend/capacitor.config.ts) |
| Backend API | Hugging Face Spaces | [backend/Dockerfile](backend/Dockerfile) |
| MedGemma server | Hugging Face Spaces | [medgemma-space/Dockerfile](medgemma-space/Dockerfile) |
| Uptime monitoring | UptimeRobot | Pings `/health` (HEAD) every 5 minutes |

**Vercel environment variable:**
```
VITE_API_URL = https://YOUR_HF_USERNAME-sakhi-api.hf.space/api
```

**HF Space secrets** (Settings → Variables and Secrets):
```
GROQ_API_KEY, OPEN_ROUTER_API_KEY, GOOGLE_API_KEY, MEDGEMMA_API_URL
```

To make MedGemma the active provider, point `MEDGEMMA_API_URL` at the MedGemma Ollama Space. It will automatically rank first in the cascade — no code changes needed.

**Deploying the backend to HF Spaces:**

Git push doesn't work due to large PDF files in `guidelines/`. Use `huggingface_hub` instead:

```bash
cd backend && source .venv/bin/activate
python3 -c "
from huggingface_hub import HfApi
api = HfApi(token='hf_YOUR_TOKEN')
api.upload_folder(
    folder_path='.',
    repo_id='YOUR_HF_USERNAME/sakhi-api',
    repo_type='space',
    ignore_patterns=['chroma_db/*', '__pycache__/*', '*.pyc', '.venv/*', '.env', '*.DS_Store']
)
"
```

UptimeRobot monitors needed (both every 5 min):
- `https://YOUR_HF_USERNAME-sakhi-api.hf.space/health` — HTTP HEAD
- `https://YOUR_HF_USERNAME-sakhi.hf.space/api/tags` — HTTP HEAD

---

## Screens

| # | Screen | Description |
|---|---|---|
| 0 | Onboarding | ASHA ID + name entry — typing a known ID (ASH1001, ASH2047, ASH3112) auto-fills the worker name; each ID gets its own patient list |
| 1 | Home | Patient list with color-coded risk strip, search, and new checkup CTA |
| 2 | Patient Profile | ANC patient detail, vitals history, past assessments |
| 2b | Newborn Profile | Newborn detail, visit timeline, weight trend |
| 3 | New Checkup Picker | Choose ANC or newborn visit type |
| 4 | Checkup Form | 2-step ANC form: vitals (BP, weight, fundal height, Hb) → symptoms |
| 4b | Newborn Checkup Form | Visit day, current weight, ASHA checklist observations |
| 5 | Assessment | AI output — risk banner, clinical notices, patient script, next action |
| 6 | Ask Sakhi | Free-form chat; patient context injected automatically if selected |
| 7 | Schedule | Follow-up appointments across all patients |

---

## Demo Data

The live demo is pre-loaded with 45 patients spread across three ASHA workers (ASH1001 · Rampur, ASH2047 · Chandpur, ASH3112 · Banswa). All patient data is synthetic and MOHFW-aligned.

**Follow-up dates are intentionally dynamic.** Rather than storing fixed calendar dates, each patient's next checkup is computed relative to `today` when the app loads — so the schedule always shows a realistic mix of upcoming and recently-due visits instead of an ever-growing backlog of overdue ones. This is a demo convenience, not a production behaviour; a real deployment would persist absolute dates.

ABHA verification is in demo mode: any 6-digit OTP is accepted when ABDM sandbox credentials are absent.

---

## Project Structure

```
sakhi/
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── api.js               # All fetch calls — single source of truth
│   │   ├── components/
│   │   │   ├── AbhaVerifyModal.jsx  # 3-step ABHA OTP bottom sheet (number → OTP → confirmed)
│   │   │   ├── BottomNav.jsx        # Tab navigation
│   │   │   ├── Disclaimer.jsx       # "Sakhi supports your judgment…" footer
│   │   │   ├── OfflineBanner.jsx    # Offline / syncing status strip
│   │   │   ├── RiskBadge.jsx        # Color-coded risk level badge
│   │   │   └── TopBar.jsx           # Header with back button + title
│   │   ├── context/
│   │   │   └── AppContext.jsx       # Global state — patients, session, language, offline sync
│   │   ├── data/
│   │   │   └── mockPatients.json    # 45 patients across 3 workers/villages (ASH1001 Rampur, ASH2047 Chandpur, ASH3112 Banswa)
│   │   ├── utils/
│   │   │   ├── localAssessment.js   # MOHFW rule-based triage (offline fallback)
│   │   │   ├── nameUtils.js         # Localised name/village helpers
│   │   │   └── offlineQueue.js      # localStorage queue for pending AI submissions
│   │   ├── hooks/
│   │   │   ├── useDebounce.js       # Debounce hook for patient search
│   │   │   └── useOnlineStatus.js   # Reactive navigator.onLine hook
│   │   ├── locales/
│   │   │   ├── en.json              # English UI strings
│   │   │   └── hi.json              # Hindi UI strings (Devanagari)
│   │   └── pages/
│   │       ├── Onboarding.jsx       # ASHA worker name selection
│   │       ├── Home.jsx             # Patient list with risk summary strip + search
│   │       ├── PatientProfile.jsx   # ANC patient detail + checkup history
│   │       ├── NewbornProfile.jsx   # Newborn detail + visit history
│   │       ├── NewCheckupPicker.jsx # Choose ANC or newborn visit
│   │       ├── CheckupForm.jsx      # 2-step ANC vitals + symptoms form
│   │       ├── NewbornCheckupForm.jsx # Newborn visit observations
│   │       ├── Assessment.jsx       # AI output screen (shared ANC + newborn)
│   │       ├── AskSakhi.jsx         # Free-form chat with patient context
│   │       └── Schedule.jsx         # Follow-up appointment calendar
│   ├── android/                     # Generated Android Studio project (Capacitor)
│   ├── capacitor.config.ts          # Capacitor config — app ID, status bar, webDir
│   ├── package.json
│   ├── tailwind.config.js
│   └── vite.config.js
│
├── backend/
│   ├── main.py                      # FastAPI app — CORS, rate limiting, routing; RAG preload at startup
│   ├── model.py                     # AI provider cascade — the ONLY model caller
│   ├── rag.py                       # Retrieval module — preload() + async retrieve(query) via ChromaDB
│   ├── ingest.py                    # One-time PDF indexing script (run after adding new guidelines)
│   ├── prompts.py                   # All system prompts in one place
│   ├── limiter.py                   # SlowAPI rate limiter instance
│   ├── requirements.txt
│   ├── Dockerfile                   # HF Spaces deployment (port 7860)
│   ├── guidelines/                  # 10 source PDFs: ASHA Modules 1–7, WHO ANC 2016, MOHFW HBNC, MOHFW ANC
│   ├── chroma_db/                   # Persistent ChromaDB vector store (generated by ingest.py)
│   └── routes/
│       ├── checkup.py               # POST /api/checkup-assessment
│       ├── chat.py                  # POST /api/chat
│       ├── abha.py                  # POST /api/abha/request-otp + /api/abha/verify-otp (ABDM proxy)
│       └── transcribe.py            # POST /api/transcribe (voice fallback)
│
├── medgemma-space/
│   ├── Dockerfile                   # Ollama + MedGemma GGUF server for HF Spaces
│   └── start.sh                     # Pulls sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M on startup
│
├── model/                           # Fine-tuning + evaluation pipeline (Kaggle notebooks)
│   ├── finetuning-medgemma.ipynb    # QLoRA fine-tuning on maternal/neonatal data
│   ├── testing-ft-model.ipynb       # Triage evaluation harness (75 labelled cases)
│   ├── merge-and-quantize.ipynb     # Merges LoRA adapter → base model, quantizes to GGUF Q4_K_M
│   ├── data/
│   │   └── maternal_triage_cases.json  # MOHFW-aligned triage dataset for evaluation
│   └── README.md                    # Training config, eval metrics, dataset documentation
│
└── render.yaml                      # Render deployment config (alternative to HF)
```

---

*Sakhi exists because no ASHA worker should have to make a life-or-death decision alone.*
