"""
routes/abha.py — ABHA identity verification proxy

Proxies to the ABDM (Ayushman Bharat Digital Mission) sandbox so that
clientId / clientSecret never touch the frontend.

Flow:
  1. POST /api/abha/request-otp  — gets a gateway token, sends OTP to the
                                    patient's Aadhaar-linked mobile, returns txnId
  2. POST /api/abha/verify-otp   — confirms OTP, fetches /profile/me, returns
                                    name / dob / gender / photo

Env vars:
  ABDM_CLIENT_ID      — from ABDM sandbox registration
  ABDM_CLIENT_SECRET  — from ABDM sandbox registration
  ABDM_BASE_URL       — defaults to https://sandbox.abdm.gov.in

Demo mode:
  If ABDM_CLIENT_ID / ABDM_CLIENT_SECRET are not set, both routes operate in
  a local mock mode so the UI flow can be demonstrated without real credentials.
  Mock OTP: any 6-digit number is accepted.
"""

import os
import httpx
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from limiter import limiter

router = APIRouter()

ABDM_BASE = os.getenv("ABDM_BASE_URL", "https://sandbox.abdm.gov.in")
ABDM_CLIENT_ID = os.getenv("ABDM_CLIENT_ID", "")
ABDM_CLIENT_SECRET = os.getenv("ABDM_CLIENT_SECRET", "")

_MOCK_TXN_ID = "mock-txn-demo"

# Demo profile returned when running without real ABDM credentials.
# Name is intentionally generic so the mismatch-detection UI can be
# demonstrated (it will differ from any real patient name in the app).
_MOCK_PROFILE = {
    "name": "Meena Devi (Demo)",
    "dob": "1998-04-12",
    "gender": "Female",
    "photo": None,
    "abha_number": "91-0000-0000-0001",
}


def _is_demo_mode() -> bool:
    return not (ABDM_CLIENT_ID and ABDM_CLIENT_SECRET)


async def _gateway_token() -> str:
    """Exchange clientId + clientSecret for a short-lived ABDM gateway token."""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{ABDM_BASE}/gateway/v0.5/sessions",
            json={"clientId": ABDM_CLIENT_ID, "clientSecret": ABDM_CLIENT_SECRET},
            headers={"Content-Type": "application/json"},
        )
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail="ABDM gateway auth failed")
    token = resp.json().get("accessToken")
    if not token:
        raise HTTPException(status_code=502, detail="No access token from ABDM gateway")
    return token


def _abdm_headers(gateway_token: str) -> dict:
    return {
        "Authorization": f"Bearer {gateway_token}",
        "Content-Type": "application/json",
        "X-CM-ID": "sbx",
    }


def _abdm_error(resp: httpx.Response, fallback: str) -> str:
    try:
        body = resp.json()
        return body.get("message") or body.get("details") or fallback
    except Exception:
        return fallback


# ── Models ────────────────────────────────────────────────────────────────────

class OtpRequest(BaseModel):
    abha_number: str   # e.g. "91-0000-0000-0001"


class OtpVerifyRequest(BaseModel):
    txn_id: str
    otp: str


# ── Routes ────────────────────────────────────────────────────────────────────

@router.post("/abha/request-otp")
@limiter.limit("5/minute")
async def request_otp(request: Request, req: OtpRequest):
    """Step 1 — request an OTP to the patient's Aadhaar-linked phone.

    Returns { txn_id } which the frontend passes back in /verify-otp.
    In demo mode (no ABDM credentials), returns a mock txn_id immediately
    without contacting ABDM.
    """
    if _is_demo_mode():
        return {"txn_id": _MOCK_TXN_ID, "_demo": True}

    token = await _gateway_token()
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{ABDM_BASE}/api/v3/profile/login/request/otp",
            json={
                "scope": ["abha-login", "aadhaar-verify"],
                "loginHint": "abha-number",
                "loginId": req.abha_number,
                "otpSystem": "aadhaar",
            },
            headers=_abdm_headers(token),
        )
    if resp.status_code not in (200, 202):
        raise HTTPException(
            status_code=400,
            detail=_abdm_error(resp, f"ABDM error {resp.status_code}"),
        )
    data = resp.json()
    txn_id = data.get("txnId") or data.get("txn_id") or ""
    if not txn_id:
        raise HTTPException(status_code=502, detail="ABDM did not return a transaction ID")
    return {"txn_id": txn_id}


@router.post("/abha/verify-otp")
@limiter.limit("5/minute")
async def verify_otp(request: Request, req: OtpVerifyRequest):
    """Step 2 — confirm the OTP and return the patient's ABHA profile.

    In demo mode, accepts any 6-digit OTP against the mock txn_id and returns
    a hardcoded demo profile.
    """
    if _is_demo_mode():
        if req.txn_id != _MOCK_TXN_ID:
            raise HTTPException(status_code=400, detail="Invalid transaction ID")
        if len(req.otp) != 6 or not req.otp.isdigit():
            raise HTTPException(status_code=400, detail="Enter a 6-digit OTP")
        return {**_MOCK_PROFILE, "_demo": True}

    token = await _gateway_token()

    # Confirm OTP
    async with httpx.AsyncClient(timeout=10) as client:
        verify_resp = await client.post(
            f"{ABDM_BASE}/api/v3/profile/login/verify",
            json={"scope": ["abha-login"], "txnId": req.txn_id, "otp": req.otp},
            headers=_abdm_headers(token),
        )
    if verify_resp.status_code not in (200, 202):
        raise HTTPException(
            status_code=400,
            detail=_abdm_error(verify_resp, "OTP verification failed"),
        )

    verify_data = verify_resp.json()
    user_token = verify_data.get("token") or verify_data.get("accessToken") or ""
    if not user_token:
        raise HTTPException(status_code=502, detail="No user token returned after OTP verify")

    # Fetch profile
    async with httpx.AsyncClient(timeout=10) as client:
        profile_resp = await client.get(
            f"{ABDM_BASE}/api/v3/profile/me",
            headers={
                **_abdm_headers(token),
                "X-Token": f"Bearer {user_token}",
            },
        )
    if not profile_resp.is_success:
        raise HTTPException(status_code=502, detail="Failed to fetch ABHA profile")

    p = profile_resp.json()
    return {
        "name": p.get("name") or p.get("fullName"),
        "dob": p.get("dateOfBirth") or p.get("dob"),
        "gender": p.get("gender"),
        "photo": p.get("profilePhoto"),
        "abha_number": p.get("healthIdNumber") or p.get("abhaNumber"),
    }
