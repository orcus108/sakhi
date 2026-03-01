---
title: Sakhi API
emoji: 🏥
colorFrom: blue
colorTo: green
sdk: docker
pinned: false
app_port: 7860
---

# Sakhi — Backend

FastAPI service that powers AI assessments and chat for ASHA workers, deployed on [Hugging Face Spaces](https://docvm-sakhi-api.hf.space/health).

## Stack

| Tool | Version | Purpose |
|---|---|---|
| FastAPI | 0.115 | HTTP framework |
| Uvicorn | 0.32 | ASGI server |
| httpx | 0.28 | Async HTTP client (model API calls) |
| SlowAPI | 0.1.9 | Rate limiting |
| ChromaDB | 0.6 | Vector store for RAG |
| sentence-transformers | 3.4 | Embedding model for RAG |
| pdfplumber | 0.11 | PDF ingestion for RAG index |
| pydantic | 2 | Request / response validation |
| python-dotenv | 1.0 | Environment variable loading |

## Project structure

```
backend/
├── main.py             # FastAPI app — CORS, rate limiting, route registration
├── model.py            # AI provider cascade — the ONLY file that calls any model
├── prompts.py          # All system prompts in one place
├── rag.py              # RAG retrieval — loads ChromaDB index, exposes retrieve()
├── limiter.py          # SlowAPI rate limiter instance (shared across routes)
├── ingest.py           # One-time script to index PDF documents into ChromaDB
├── requirements.txt
├── Dockerfile          # HF Spaces deployment (port 7860)
├── .env.example        # Template — copy to .env and fill in keys
└── routes/
    ├── checkup.py      # POST /api/checkup-assessment
    ├── chat.py         # POST /api/chat
    └── transcribe.py   # POST /api/transcribe
```

## API endpoints

| Method | Path | Rate limit | Description |
|---|---|---|---|
| `POST` | `/api/checkup-assessment` | 10 req/min per IP | ANC + newborn AI risk assessment |
| `POST` | `/api/chat` | 20 req/min per IP | Free-form ASHA worker chat |
| `POST` | `/api/transcribe` | — | Voice-to-text (Whisper fallback) |
| `GET` | `/health` | — | Uptime probe for UptimeRobot |

See the root [CLAUDE.md](../CLAUDE.md) for full request/response schemas.

## Model cascade

`model.py` is the **only file** in the codebase that calls any AI model. All routes import `generate()` or `generate_chat()` from it.

Providers are tried in priority order — the first one with credentials configured wins:

| Priority | Provider | Model | Key required |
|---|---|---|---|
| 1 | MedGemma (Ollama) | `sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M` | `MEDGEMMA_API_URL` |
| 2 | OpenRouter | `google/gemma-3n-e4b-it:free` | `OPEN_ROUTER_API_KEY` |
| 3 | Gemini | `gemini-2.5-flash-lite` | `GOOGLE_API_KEY` |
| 4 | Groq | `llama-3.1-8b-instant` | `GROQ_API_KEY` |

If a provider's key is missing or the call fails, the next provider is tried silently. Configure at least one key or the API will return 500 on every request.

## Local development

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # fill in at least one model API key
uvicorn main:app --reload
# → http://localhost:8000
```

## Environment variables

```bash
# backend/.env

# Provider keys — set at least one
GROQ_API_KEY=               # Free tier at groq.com — fastest to get started
OPEN_ROUTER_API_KEY=        # Free Gemma 3n via openrouter.ai
GOOGLE_API_KEY=             # Gemini 2.5 Flash Lite via Google AI Studio
MEDGEMMA_API_URL=           # URL of your Ollama / llama.cpp server
MEDGEMMA_API_KEY=           # Optional — only if your server requires auth
MEDGEMMA_MODEL_NAME=        # Defaults to hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M
```

## Deployment (Hugging Face Spaces)

The [Dockerfile](Dockerfile) builds a Python 3.11 image that runs Uvicorn on port 7860 (HF Spaces standard).

1. Create a new HF Space with the **Docker** SDK.
2. Push the contents of this `backend/` directory to the Space repo.
3. Add secrets under **Settings → Variables and Secrets**:
   ```
   GROQ_API_KEY
   OPEN_ROUTER_API_KEY
   GOOGLE_API_KEY
   MEDGEMMA_API_URL    ← point at the medgemma-space URL to activate MedGemma
   ```

UptimeRobot (or any cron pinger) should hit `GET /health` every 5 minutes to keep the Space warm.

## The Model Abstraction Rule

> **Never call any model API from anywhere except `model.py`.**

All routes call `generate()` or `generate_chat()` — nothing else. This means switching the active provider is a one-line environment variable change with zero code modifications.
