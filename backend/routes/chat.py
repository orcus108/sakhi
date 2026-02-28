"""
routes/chat.py — POST /api/chat

Powers the "Ask Sakhi" free-form chat screen. Supports multi-turn conversation
and optional patient context injection so the ASHA worker can ask patient-specific
questions without repeating clinical details in the chat itself.

Flow:
  1. Receive the full message history + optional patient context from the frontend
  2. Build a system prompt that includes patient data (if provided) as context
  3. Call model.generate_chat() with the message history
  4. Return the model's reply as a plain string

Patient context is injected into the system prompt rather than the message
history so it doesn't consume visible chat space and can't be confused with
something the ASHA worker said.

Rate limit: 20 requests/minute per IP (enforced by SlowAPI).
"""

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel
from typing import Any, Optional

from prompts import get_chat_prompt
from model import generate_chat
from limiter import limiter
from rag import retrieve

router = APIRouter()


# ── Request / Response models ─────────────────────────────────────────────────

class Message(BaseModel):
    """A single turn in the conversation history."""
    role: str   # "user" | "assistant"
    content: str


class ChatRequest(BaseModel):
    """Incoming payload from the Ask Sakhi screen.

    messages:        Full conversation history (oldest first).
    patient_context: Optional patient object from the frontend. When provided,
                     key clinical data is appended to the system prompt so the
                     model can give patient-specific answers.
    language:        "hi" triggers Hindi responses; "en" is the default.
    """
    messages: list[Message]
    patient_context: Optional[dict[str, Any]] = None
    language: str = "en"   # "en" | "hi"


class ChatResponse(BaseModel):
    """Response returned to the frontend."""
    reply: str


# ── System prompt builder ─────────────────────────────────────────────────────

def _build_system_prompt(patient: Optional[dict], language: str = "en") -> str:
    """Construct the system prompt, optionally appending patient context.

    If no patient is provided (general chat), returns the base CHAT_SYSTEM_PROMPT.
    If a patient is provided, appends demographics, current vitals, and the
    latest Sakhi assessment so the model has full clinical context.
    """
    base = get_chat_prompt(language)

    if not patient:
        return base

    # Start with core demographics — always present for both ANC and newborn.
    lines = [
        f"\n\nCurrent patient context:",
        f"- Name: {patient.get('name')}",
        f"- Age: {patient.get('age')} years",
        f"- Risk level: {patient.get('risk_level')}",
    ]

    # ANC-specific fields
    if patient.get('gestational_weeks'):
        lines.append(f"- Gestational age: {patient.get('gestational_weeks')} weeks")
    if patient.get('gravida') is not None:
        lines.append(f"- Gravida: {patient.get('gravida')}, Para: {patient.get('para')}")

    # Newborn-specific fields
    if patient.get('mother_name'):
        lines.append(f"- Mother: {patient.get('mother_name')}")

    # Include vitals from the most recent checkup/visit if available.
    checkup = patient.get('current_checkup')
    if checkup:
        lines.append("\nToday's checkup readings:")
        if checkup.get('bp_systolic'):
            lines.append(f"- BP: {checkup['bp_systolic']}/{checkup['bp_diastolic']} mmHg")
        if checkup.get('weight_kg'):
            lines.append(f"- Weight: {checkup['weight_kg']} kg")
        if checkup.get('fundal_height_cm'):
            lines.append(f"- Fundal height: {checkup['fundal_height_cm']} cm")
        if checkup.get('fetal_heart_rate'):
            lines.append(f"- Fetal heart rate: {checkup['fetal_heart_rate']} bpm")
        if checkup.get('hemoglobin'):
            lines.append(f"- Haemoglobin: {checkup['hemoglobin']} g/dL")
        if checkup.get('symptoms'):
            lines.append(f"- Symptoms reported: {', '.join(checkup['symptoms'])}")
        if checkup.get('observations'):
            lines.append(f"- Observations: {', '.join(checkup['observations'])}")
        if checkup.get('visit_day'):
            lines.append(f"- Visit: {checkup['visit_day']}")

    # Include the latest Sakhi assessment summary if one exists (post-assessment chat).
    assessment = patient.get('assessment_summary')
    if assessment:
        lines.append("\nSakhi's assessment from this checkup:")
        lines.append(f"- Risk level: {assessment.get('risk_level')}")
        lines.append(f"- Reason: {assessment.get('risk_reason')}")
        lines.append(f"- Recommended action: {assessment.get('what_to_do_next')}")

    return base + "\n".join(lines)


# ── Route ─────────────────────────────────────────────────────────────────────

@router.post("/chat", response_model=ChatResponse)
@limiter.limit("20/minute")
async def chat(request: Request, req: ChatRequest):
    """Handle a chat turn from the Ask Sakhi screen.

    Builds the system prompt (with optional patient context), passes the full
    message history to the model, and returns the model's trimmed reply.
    Raises 502 if all model providers fail.
    """
    system_prompt = _build_system_prompt(req.patient_context, req.language)
    messages = [m.model_dump() for m in req.messages]

    # Augment system prompt with relevant guideline excerpts (no-op if index unavailable)
    query = req.messages[-1].content if req.messages else ""
    guideline_context = await retrieve(query)
    if guideline_context:
        system_prompt += (
            "\n\nRelevant guidelines (use only if directly applicable):\n"
            + guideline_context
        )

    try:
        reply = await generate_chat(system_prompt, messages)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Model error: {str(e)}")

    return ChatResponse(reply=reply.strip())
