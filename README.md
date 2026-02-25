# Sakhi — AI Clinical Companion for ASHA Workers

> **Google MedGemma Impact Challenge** · Kaggle · February 2026

Sakhi is a mobile-first clinical decision-support tool for ASHA (Accredited Social Health Activist) workers in rural India. It uses a **MedGemma-backed AI cascade** to assess antenatal and newborn patient visits in real time, flagging high-risk cases and guiding referral decisions — in the field, on a phone, with no specialist nearby.

**[Live Demo →](https://sakhi-asha.vercel.app)** &nbsp;|&nbsp; **[Backend API →](https://docvm-sakhi-api.hf.space/health)**

---

## The Problem

India's maternal mortality rate remains among the highest in the world. ASHA workers — ~1 million community health volunteers — are often the only clinical touchpoint for pregnant women and newborns in rural areas. They measure blood pressure, check fetal heart rates, and observe newborns during home visits, but they are not doctors. A missed reading, an unrecognised warning sign, or an uncertain referral decision can cost a life.

Sakhi gives every ASHA worker an expert clinical colleague — always available, warm, and field-appropriate.

---

## Key Features

| Feature | Description |
|---|---|
| **ANC Assessment** | AI risk stratification (green / yellow / red) from vitals + symptoms |
| **Newborn Visits** | Age-specific postnatal assessment (Day 1 through 6 weeks) |
| **Ask Sakhi** | Free-form Q&A with optional patient context injection |
| **Hindi Mode** | Full UI and AI responses in Devanagari Hindi |
| **Schedule View** | Follow-up calendar for all patients |
| **Model Cascade** | Auto-fallback across 4 providers — MedGemma → Gemma 3n → Gemini → Groq |

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
│   │  1. MedGemma 1.5 4B IT  (Ollama / GGUF)    │   │
│   │     ↓ (if unavailable)                      │   │
│   │  2. Gemma 3n E4B IT  (OpenRouter, free)     │   │
│   │     ↓ (if unavailable)                      │   │
│   │  3. Gemini 2.5 Flash Lite  (Google AI)      │   │
│   │     ↓ (if unavailable)                      │   │
│   │  4. Llama 3.1 8B  (Groq, last resort)       │   │
│   └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### The Model Abstraction Rule

`backend/model.py` is the **only file** that calls any AI model. All routes import `generate()` and `generate_chat()` from it — no model API calls anywhere else in the codebase.

The cascade is priority-ordered and automatic: if a provider's API key is missing or the call fails, the next provider is tried silently. This means the app stays online during model outages, and MedGemma can be promoted to first position the moment credentials are available with zero code changes.

---

## Project Structure

```
sakhi/
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── api.js               # All fetch calls — single source of truth
│   │   ├── components/
│   │   │   ├── BottomNav.jsx        # Tab navigation
│   │   │   ├── Disclaimer.jsx       # "Sakhi supports your judgment…" footer
│   │   │   ├── RiskBadge.jsx        # Color-coded risk level badge
│   │   │   └── TopBar.jsx           # Header with back button + title
│   │   ├── context/
│   │   │   └── AppContext.jsx       # Global state — patients, session, language
│   │   ├── data/
│   │   │   └── mockPatients.json    # 6 ANC + 6 newborn patients, all risk levels
│   │   ├── hooks/
│   │   │   └── useDebounce.js       # Debounce hook for patient search
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
│   ├── package.json
│   ├── tailwind.config.js
│   └── vite.config.js
│
├── backend/
│   ├── main.py                      # FastAPI app — CORS, rate limiting, routing
│   ├── model.py                     # AI provider cascade — the ONLY model caller
│   ├── prompts.py                   # All system prompts in one place
│   ├── limiter.py                   # SlowAPI rate limiter instance
│   ├── requirements.txt
│   ├── Dockerfile                   # HF Spaces deployment (port 7860)
│   └── routes/
│       ├── checkup.py               # POST /api/checkup-assessment
│       ├── chat.py                  # POST /api/chat
│       └── transcribe.py            # POST /api/transcribe (voice fallback)
│
├── medgemma-space/
│   ├── Dockerfile                   # Ollama + MedGemma GGUF server for HF Spaces
│   └── start.sh                     # Pulls medgemma-1.5-4b-it-GGUF:Q4_K_M on startup
│
├── model/                           # Fine-tuning + evaluation pipeline (Kaggle notebooks)
│   ├── finetuning-medgemma.ipynb    # QLoRA fine-tuning on maternal/neonatal data
│   ├── testing-ft-model.ipynb       # Triage evaluation harness (75 labelled cases)
│   ├── data/
│   │   └── maternal_triage_cases.json  # MOHFW-aligned triage dataset for evaluation
│   └── README.md                    # Training config, eval metrics, dataset documentation
│
├── render.yaml                      # Render deployment config (alternative to HF)
└── CLAUDE.md                        # Architecture decisions + dev guidelines
```

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

### `GET /health`

Returns `{"status": "healthy"}`. Used by UptimeRobot to keep the HF Space warm.

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

---

## Deployment

| Layer | Platform | Config file |
|---|---|---|
| Frontend | Vercel | Root dir: `frontend`, framework: Vite |
| Backend API | Hugging Face Spaces | [backend/Dockerfile](backend/Dockerfile) |
| MedGemma server | Hugging Face Spaces | [medgemma-space/Dockerfile](medgemma-space/Dockerfile) |
| Uptime monitoring | UptimeRobot | Pings `GET /health` every 5 minutes |

**Vercel environment variable:**
```
VITE_API_URL = https://YOUR_HF_USERNAME-sakhi-api.hf.space/api
```

**HF Space secrets** (Settings → Variables and Secrets):
```
GROQ_API_KEY, OPEN_ROUTER_API_KEY, GOOGLE_API_KEY, MEDGEMMA_API_URL
```

To make MedGemma the active provider, point `MEDGEMMA_API_URL` at the MedGemma Ollama Space. It will automatically rank first in the cascade — no code changes needed.

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
2. Downloads `medgemma-1.5-4b-it-GGUF:Q4_K_M` from HuggingFace Hub on startup
3. Exposes an OpenAI-compatible `/v1/chat/completions` endpoint on port 7860

`backend/model.py` calls this endpoint directly. Once `MEDGEMMA_API_URL` is set to the Space URL, MedGemma becomes the default with no further changes.

### Fine-tuning

[`model/finetuning-medgemma.ipynb`](model/finetuning-medgemma.ipynb) documents the fine-tuning pipeline, targeting two key gaps:
- **Indian clinical context:** Recognition of locally prevalent risk factors (severe anaemia, eclampsia, low birth weight patterns common in Rajasthan)
- **Output reliability:** Improving JSON schema compliance to reduce post-processing failures in production

---

## Design Principles

- **Mobile-first:** Max content width 430px; all interactive elements ≥ 48px touch target
- **Data-forward:** Vital numbers are always the largest element on screen (`text-3xl font-bold`) — BP, weight, and Hb are hero numbers, not label-value pairs
- **Warm tone:** "Sakhi is thinking…" not "Loading…"; "Tell the patient…" not "Recommendation:"
- **Actionable over informational:** Every assessment ends with one concrete next step
- **Safe by design:** The disclaimer — *"Sakhi supports your judgment — always refer when unsure"* — is shown on every Assessment and Chat screen. The AI never diagnoses; it flags and supports.

---

## Screens

| # | Screen | Description |
|---|---|---|
| 0 | Onboarding | Worker name selection — no password, no friction |
| 1 | Home | Patient list with color-coded risk strip, search, and new checkup CTA |
| 2 | Patient Profile | ANC patient detail, vitals history, past assessments |
| 2b | Newborn Profile | Newborn detail, visit timeline, weight trend |
| 3 | New Checkup Picker | Choose ANC or newborn visit type |
| 4 | Checkup Form | 2-step ANC form: vitals (BP, weight, fundal height, Hb) → symptoms |
| 4b | Newborn Checkup Form | Visit day, current weight, ASHA checklist observations |
| 5 | Assessment | AI output — risk banner, clinical notices, patient script, next action |
| 6 | Ask Sakhi | Free-form chat; patient context injected automatically if selected |
| 7 | Schedule | Follow-up appointments across all patients |