"""
routes/checkup.py — POST /api/checkup-assessment

Handles AI-powered clinical assessments for both ANC (antenatal care) and
newborn postnatal visits. The patient_type field in the request body determines
which system prompt and message builder are used.

Flow:
  1. Receive patient + checkup data from the frontend
  2. Build a structured natural-language prompt from the raw fields
  3. Call model.generate() — which cascades through available AI providers
  4. Strip any markdown fences the model may have wrapped around the JSON
  5. Parse and validate the JSON against AssessmentResponse
  6. Return the structured assessment to the frontend

Rate limit: 10 requests/minute per IP (enforced by SlowAPI).
"""

import json
import re
from datetime import date
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel
from typing import Any, Optional

from prompts import get_checkup_prompt
from model import generate
from limiter import limiter
from rag import retrieve

router = APIRouter()


# ── Request / Response models ─────────────────────────────────────────────────

class CheckupRequest(BaseModel):
    """Incoming payload from the frontend checkup forms.

    patient:      Full patient object from mockPatients.json (ANC or newborn).
    checkup:      Vitals/observations entered by the ASHA worker this visit.
    patient_type: Determines prompt + message builder. "anc" is the default.
    language:     "hi" triggers Hindi responses; "en" is the default.
    """
    patient: dict[str, Any]
    checkup: dict[str, Any]
    patient_type: str = "anc"   # "anc" | "newborn"
    language: str = "en"        # "en" | "hi"


class AssessmentResponse(BaseModel):
    """Structured AI assessment returned to the frontend.

    Mirrors the JSON schema the model is instructed to produce in prompts.py.
    Pydantic validates the model output before it reaches the caller.
    """
    risk_level: str             # "green" | "yellow" | "red"
    risk_reason: str            # One-sentence summary of the primary concern
    what_sakhi_noticed: list[str]   # 2-3 clinical observations
    what_to_tell_patient: str   # Plain-language advice for the ASHA to relay
    what_to_do_next: str        # Concrete next action (e.g. "Refer to PHC today")
    follow_up_date: Optional[str]   # "YYYY-MM-DD" or null


# ── Message builders ──────────────────────────────────────────────────────────

def _build_anc_message(patient: dict, checkup: dict) -> str:
    """Format ANC patient data as a plain-text prompt for the AI model.

    Combines static patient demographics (age, gravida/para, gestational age)
    with today's vitals (BP, weight, fundal height, Hb) and reported symptoms.
    """
    symptoms = ", ".join(checkup.get("symptoms", [])) or "none reported"
    return f"""Patient information:
- Name: {patient.get('name')}
- Age: {patient.get('age')} years
- Gestational age: {patient.get('gestational_weeks')} weeks
- Gravida: {patient.get('gravida')}, Para: {patient.get('para')}
- Village: {patient.get('village')}

Today's checkup readings:
- Blood pressure: {checkup.get('bp_systolic')}/{checkup.get('bp_diastolic')} mmHg
- Weight: {checkup.get('weight_kg')} kg
- Fundal height: {checkup.get('fundal_height_cm')} cm
- Fetal heart rate: {checkup.get('fetal_heart_rate', 'not recorded')} bpm
- Hemoglobin: {checkup.get('hemoglobin', 'not recorded')} g/dL
- Symptoms reported: {symptoms}

Please assess this patient and respond in the required JSON format."""


def _build_newborn_message(patient: dict, checkup: dict) -> str:
    """Format newborn visit data as a plain-text prompt for the AI model.

    Includes birth details (DOB, birth weight) alongside today's visit day,
    current weight, and the ASHA worker's checklist observations. The model
    uses visit_day to apply age-specific clinical rules (see prompts.py).
    """
    observations = checkup.get("observations", [])
    obs_text = "\n".join(f"  - {o}" for o in observations) if observations else "  - none selected"
    other = checkup.get("other_observations", "").strip() or "none"

    dob = patient.get("date_of_birth", "unknown")
    birth_weight = patient.get("birth_weight_kg", "unknown")

    return f"""Newborn information:
- Name: {patient.get('name')}
- Date of birth: {dob}
- Gender: {patient.get('gender', 'not recorded')}
- Birth weight: {birth_weight} kg
- Mother: {patient.get('mother_name', 'unknown')}
- Village: {patient.get('village')}

Today's visit:
- Visit day: {checkup.get('visit_day', 'not specified')}
- Current weight: {checkup.get('weight_kg')} kg
- Observations (ASHA checklist):
{obs_text}
- Other observations: {other}

Please assess this newborn and respond in the required JSON format."""


# ── Route ─────────────────────────────────────────────────────────────────────

@router.post("/checkup-assessment", response_model=AssessmentResponse)
@limiter.limit("10/minute")
async def checkup_assessment(request: Request, req: CheckupRequest):
    """Run an AI clinical assessment for an ANC or newborn patient visit.

    Selects the correct system prompt and message builder based on patient_type,
    then calls the model cascade in model.py. Raises 502 on model failure or
    if the model returns malformed JSON instead of a valid assessment object.
    """
    system_prompt = get_checkup_prompt(req.patient_type, req.language, today=date.today().isoformat())

    if req.patient_type == "newborn":
        user_message = _build_newborn_message(req.patient, req.checkup)
    else:
        user_message = _build_anc_message(req.patient, req.checkup)

    # Augment system prompt with relevant guideline excerpts (no-op if index unavailable)
    symptoms = req.checkup.get("symptoms") or req.checkup.get("observations") or []
    rag_query = f"{req.patient_type} {' '.join(symptoms)}"
    guideline_context = await retrieve(rag_query)
    if guideline_context:
        system_prompt += (
            "\n\nRelevant guidelines (use only if directly applicable):\n"
            + guideline_context
        )

    try:
        raw = await generate(system_prompt, user_message)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Model error: {str(e)}")

    # Some models wrap their JSON in markdown code fences — strip them before parsing.
    raw = re.sub(r"```(?:json)?\s*", "", raw).strip().rstrip("```").strip()

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        raise HTTPException(
            status_code=502,
            detail=f"Model returned invalid JSON: {raw[:200]}"
        )

    return AssessmentResponse(**data)
