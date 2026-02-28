"""
Model abstraction layer — the ONLY file that talks to the AI model.

Provider priority (automatic cascade — first available wins):
  1. MedGemma   — fine-tuned + Q4_K_M quantized, Ollama on HF Space (set MEDGEMMA_API_URL)
  2. OpenRouter  — google/gemma-3n-e4b-it:free (set OPEN_ROUTER_API_KEY)
  3. Gemini      — gemini-2.5-flash-lite via Google AI (set GOOGLE_API_KEY)
  4. Groq        — llama-3.1-8b-instant, last resort (set GROQ_API_KEY)

Each provider is skipped automatically if its key/URL is not configured.

MedGemma setup (fine-tuned LoRA merged + GGUF quantized, served via Ollama):
  MEDGEMMA_API_URL=https://docvm-sakhi-medgemma.hf.space
  MEDGEMMA_MODEL_NAME=hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M
"""

import logging
import os
import httpx
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

# ── Credentials ───────────────────────────────────────────────────────────────

MEDGEMMA_API_URL = os.getenv("MEDGEMMA_API_URL", "")
MEDGEMMA_API_KEY = os.getenv("MEDGEMMA_API_KEY", "")
MEDGEMMA_MODEL_NAME = os.getenv("MEDGEMMA_MODEL_NAME", "hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M")

OPEN_ROUTER_API_KEY = os.getenv("OPEN_ROUTER_API_KEY", "")
OPEN_ROUTER_MODEL = os.getenv("OPEN_ROUTER_MODEL", "google/gemma-3n-e4b-it:free")
OPEN_ROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite")
GEMINI_API_URL = (
    f"https://generativelanguage.googleapis.com/v1beta/models/"
    f"{GEMINI_MODEL}:generateContent?key={GOOGLE_API_KEY}"
)

GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_MODEL = "llama-3.1-8b-instant"
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"


# ── Public API ────────────────────────────────────────────────────────────────

async def generate(system_prompt: str, user_message: str) -> str:
    """Single-turn completion. Cascades through providers until one succeeds."""
    return await _cascade(system_prompt, [{"role": "user", "content": user_message}])


async def generate_chat(system_prompt: str, messages: list[dict]) -> str:
    """Multi-turn chat completion. Cascades through providers until one succeeds."""
    return await _cascade(system_prompt, messages)


# ── Cascade ───────────────────────────────────────────────────────────────────

async def _cascade(system_prompt: str, messages: list[dict]) -> str:
    """Try providers in priority order, skipping any whose credentials are missing."""
    errors = []

    if MEDGEMMA_API_URL:
        try:
            return await _call_medgemma(system_prompt, messages)
        except Exception as e:
            logger.warning("MedGemma failed (%s) — trying OpenRouter", e)
            errors.append(f"medgemma: {e}")

    if OPEN_ROUTER_API_KEY:
        try:
            return await _call_openrouter(system_prompt, messages)
        except Exception as e:
            logger.warning("OpenRouter failed (%s) — trying Gemini", e)
            errors.append(f"openrouter: {e}")

    if GOOGLE_API_KEY:
        try:
            return await _call_gemini(system_prompt, messages)
        except Exception as e:
            logger.warning("Gemini failed (%s) — trying Groq", e)
            errors.append(f"gemini: {e}")

    if GROQ_API_KEY:
        try:
            return await _call_groq(system_prompt, messages)
        except Exception as e:
            errors.append(f"groq: {e}")

    raise RuntimeError(f"All providers failed — {'; '.join(errors)}")


# ── Provider implementations ──────────────────────────────────────────────────

async def _call_medgemma(system_prompt: str, messages: list[dict]) -> str:
    """llama.cpp OpenAI-compatible server running the GGUF-quantized MedGemma model.
    Start with: llama-server -m medgemma-1.5-4b-it-Q4_K_M.gguf --port 8080
    """
    url = f"{MEDGEMMA_API_URL.rstrip('/')}/v1/chat/completions"
    headers = {"Content-Type": "application/json"}
    if MEDGEMMA_API_KEY:
        headers["Authorization"] = f"Bearer {MEDGEMMA_API_KEY}"
    payload = {
        "model": MEDGEMMA_MODEL_NAME,
        "messages": [{"role": "system", "content": system_prompt}] + messages,
        "temperature": 0.2,
        "max_tokens": 1024,
    }
    async with httpx.AsyncClient(timeout=120.0) as client:
        resp = await client.post(url, headers=headers, json=payload)
        resp.raise_for_status()
        data = resp.json()
    return data["choices"][0]["message"]["content"]


async def _call_openrouter(system_prompt: str, messages: list[dict]) -> str:
    """OpenRouter API — routes to google/gemma-3n-e4b-it:free by default.
    Uses the OpenAI-compatible /chat/completions endpoint.
    Sends Referer + X-Title headers as required by OpenRouter's usage policy.
    """
    payload = {
        "model": OPEN_ROUTER_MODEL,
        "messages": [{"role": "system", "content": system_prompt}] + messages,
        "temperature": 0.2,
        "max_tokens": 1024,
    }
    headers = {
        "Authorization": f"Bearer {OPEN_ROUTER_API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://sakhi.app",
        "X-Title": "Sakhi",
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.post(OPEN_ROUTER_API_URL, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
    return data["choices"][0]["message"]["content"]


async def _call_gemini(system_prompt: str, messages: list[dict]) -> str:
    """Google Gemini API (REST) — uses the generateContent endpoint.
    Maps the OpenAI-style role 'assistant' to Gemini's 'model' role.
    The system prompt is passed via the system_instruction field rather than
    as a message in the contents array (Gemini's preferred pattern).
    """
    contents = []
    for m in messages:
        role = "user" if m["role"] == "user" else "model"
        contents.append({"role": role, "parts": [{"text": m["content"]}]})
    payload = {
        "system_instruction": {"parts": [{"text": system_prompt}]},
        "contents": contents,
        "generationConfig": {"temperature": 0.2, "maxOutputTokens": 1024},
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.post(GEMINI_API_URL, json=payload)
        resp.raise_for_status()
        data = resp.json()
    return data["candidates"][0]["content"]["parts"][0]["text"]


async def _call_groq(system_prompt: str, messages: list[dict]) -> str:
    """Groq API — llama-3.1-8b-instant, last-resort fallback.
    Uses Groq's OpenAI-compatible /chat/completions endpoint.
    Fast inference, but not a medical-domain model — used only when
    MedGemma, OpenRouter, and Gemini are all unavailable.
    """
    payload = {
        "model": GROQ_MODEL,
        "messages": [{"role": "system", "content": system_prompt}] + messages,
        "temperature": 0.2,
        "max_tokens": 1024,
    }
    headers = {
        "Authorization": f"Bearer {GROQ_API_KEY}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.post(GROQ_API_URL, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
    return data["choices"][0]["message"]["content"]
