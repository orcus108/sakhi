"""
routes/transcribe.py — POST /api/transcribe

Accepts an audio file (WebM from the browser's MediaRecorder API) and returns
a text transcription using Groq's hosted Whisper endpoint. This powers the
voice input feature in the Ask Sakhi chat screen.

The audio is forwarded to Groq as a multipart upload — the bytes never touch
disk on the server. Groq runs whisper-large-v3-turbo, which is fast enough
for field use and handles accented English well.

Note: Unlike the AI routes in model.py, this route talks directly to Groq's
Whisper API rather than going through the provider cascade. Whisper is a
separate transcription service, not a chat model, so the cascade pattern
doesn't apply here.

Rate limit: 30 requests/minute per IP (enforced by SlowAPI).
"""

from fastapi import APIRouter, HTTPException, Request, UploadFile, File
import httpx
import os
from dotenv import load_dotenv

from limiter import limiter

load_dotenv()

router = APIRouter()

# Groq Whisper endpoint — uses the same API key as the chat/completion routes.
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_WHISPER_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
GROQ_WHISPER_MODEL = "whisper-large-v3-turbo"


@router.post("/transcribe")
@limiter.limit("30/minute")
async def transcribe(request: Request, file: UploadFile = File(...)):
    """Transcribe an uploaded audio file to text via Groq Whisper.

    Accepts any audio format supported by Whisper (WebM, MP3, WAV, etc.).
    The frontend sends WebM from the browser's MediaRecorder API.
    Returns {"text": "<transcription>"} on success.
    Raises 502 on Groq API errors or network failures.
    """
    audio_bytes = await file.read()
    filename = file.filename or "audio.webm"
    content_type = file.content_type or "audio/webm"

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(
                GROQ_WHISPER_URL,
                headers={"Authorization": f"Bearer {GROQ_API_KEY}"},
                files={"file": (filename, audio_bytes, content_type)},
                data={"model": GROQ_WHISPER_MODEL},
            )
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(status_code=502, detail=f"Whisper error: {e.response.text}")
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Transcription failed: {str(e)}")

    return {"text": data.get("text", "").strip()}
