"""
main.py — FastAPI application entry point for the Sakhi backend.

Responsibilities:
- Creates the FastAPI app instance and registers all routers under /api
- Configures CORS so the Vite frontend (localhost:5173) can reach the API
- Wires up SlowAPI rate limiting and its 429 error handler
- Exposes /health for Render's uptime checks

Registered routes (all under /api prefix):
  POST /api/checkup-assessment  — ANC + newborn AI assessment (routes/checkup.py)
  POST /api/chat                — Free-form ASHA worker chat  (routes/chat.py)
  POST /api/transcribe          — Voice-to-text via Whisper   (routes/transcribe.py)
"""

import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from limiter import limiter
from routes.checkup import router as checkup_router
from routes.chat import router as chat_router
from routes.transcribe import router as transcribe_router
from routes.abha import router as abha_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Preload the RAG embedding model in a thread so it's warm before the
    # first request arrives. If the index doesn't exist yet (pre-ingest),
    # rag.preload() logs a warning and continues — no crash.
    from rag import preload
    await asyncio.to_thread(preload)
    yield


app = FastAPI(title="Sakhi API", version="1.0.0", lifespan=lifespan)

# Attach the SlowAPI limiter to app state so the @limiter.limit decorators
# on individual routes can find it at request time.
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# Allow all origins during development/hackathon. Restrict to the Vercel
# deployment URL before any production hardening.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Tighten in production
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(checkup_router, prefix="/api")
app.include_router(chat_router, prefix="/api")
app.include_router(transcribe_router, prefix="/api")
app.include_router(abha_router, prefix="/api")


@app.get("/")
async def root():
    """Root probe — confirms the service is reachable."""
    return {"status": "ok", "service": "Sakhi API"}


@app.api_route("/health", methods=["GET", "HEAD"])
async def health():
    """Health check endpoint used by Render for uptime monitoring."""
    return {"status": "healthy"}
