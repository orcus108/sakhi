"""
limiter.py — Shared SlowAPI rate-limiter instance.

A single Limiter object is created here and imported by main.py (to register
the 429 exception handler) and by each route module (to apply per-endpoint
limits via the @limiter.limit decorator).

Keyed by remote IP address so limits are per-caller, not global.
Per-endpoint limits are defined at the route level:
  /api/checkup-assessment — 10 requests/minute
  /api/chat               — 20 requests/minute
  /api/transcribe         — 30 requests/minute
"""

from slowapi import Limiter
from slowapi.util import get_remote_address

# Single shared limiter instance imported by main.py and all routes.
limiter = Limiter(key_func=get_remote_address)
