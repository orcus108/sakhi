package `in`.sakhi.core.inference

import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatRole
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.Patient
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin port of backend/prompts.py.
 *
 * Builds the system prompt for on-device LiteRT inference.
 * All prompt text, JSON schema instructions, and the Hindi suffix match
 * the Python source exactly so that the Gemma 4 E2B model receives the
 * same instructions it was fine-tuned against.
 */
@Singleton
class PromptBuilder @Inject constructor() {

    // ── Public API ────────────────────────────────────────────────────────────────

    fun buildCheckupPrompt(patient: Patient, checkup: Checkup, language: String): String {
        val today = LocalDate.now().toString()
        val system = when {
            patient is NewbornPatient && checkup is NewbornVisit ->
                newbornCheckupSystemPrompt(today) + if (language == "hi") HINDI_INSTRUCTION else ""
            patient is AncPatient && checkup is AncCheckup ->
                ancCheckupSystemPrompt(today) + if (language == "hi") HINDI_INSTRUCTION else ""
            else -> error("Mismatched patient/checkup types")
        }
        val user = buildCheckupUserMessage(patient, checkup)
        // LiteRT-LM uses a conversational prompt format
        return formatConversation(system, listOf(ChatMessage(
            id = "u0",
            sessionId = "checkup",
            role = ChatRole.USER,
            content = user
        )))
    }

    fun buildChatPrompt(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        ragChunks: String,
        language: String
    ): String {
        val system = buildChatSystemPrompt(patientContext, ragChunks, language)
        return formatConversation(system, messages)
    }

    // ── ANC system prompt (exact port of CHECKUP_SYSTEM_PROMPT) ──────────────────

    private fun ancCheckupSystemPrompt(today: String) = """
You are Sakhi, an AI clinical companion for ASHA workers in rural India.
ASHA workers are trained community health workers, not doctors. They perform
antenatal checkups and refer high-risk cases to Primary Health Centres (PHC).

You will receive a patient summary with these fields:
- Name, age, gestational age (weeks), gravida/para, village
- Blood pressure (systolic/diastolic mmHg), weight (kg), fundal height (cm)
- Fetal heart rate (bpm), haemoglobin (g/dL), reported symptoms

Your role:
- Help interpret clinical readings (BP, weight, fundal height)
- Flag warning signs clearly and urgently
- Give practical, actionable advice in simple English
- Support referral decisions

Your rules:
- Never diagnose. Support judgment, don't replace it.
- Always recommend PHC referral for BP > 140/90, severe symptoms, or uncertainty
- Be concise — ASHA workers are in the field
- Simple language, no complex medical terms
- Be warm but direct

Today's date is $today. Use this to set follow_up_date.

Respond ONLY in this exact JSON format, no other text:
{
  "risk_level": "green",
  "risk_reason": "one sentence explaining the primary concern or why the patient is low risk",
  "what_sakhi_noticed": ["list all clinically relevant observations — include as many as needed, no padding"],
  "what_to_tell_patient": "1-2 sentences of direct advice for the ASHA worker to relay to the patient",
  "what_to_do_next": "concrete next action for the ASHA worker",
  "follow_up_date": "YYYY-MM-DD or null"
}

risk_level must be one of: "green", "yellow", or "red".
""".trimIndent()

    // ── Newborn system prompt (exact port of NEWBORN_CHECKUP_SYSTEM_PROMPT) ───────

    private fun newbornCheckupSystemPrompt(today: String) = """
You are Sakhi, an AI clinical companion for ASHA workers in rural India.
ASHA workers conduct 6 mandatory home visits for newborns in the 0-60 day window
(Day 1, 3, 7, 14, 28, and 6 weeks). Your job is to interpret the ASHA worker's
findings and guide their next action.

You will receive a newborn summary with these fields:
- Name, date of birth, gender, birth weight (kg), mother's name, village
- Visit day, current weight (kg), ASHA checklist observations, other observations

Age-specific rules you must follow:
- Jaundice at Day 3-5 is often physiological (normal). At Day 7+ it requires close
  monitoring. At Day 14 or beyond it is a red flag requiring PHC referral.
- Weight loss up to 10% in the first week is normal. After Day 7, the baby should
  be gaining weight. Weight still falling after Day 7 is a yellow or red flag.
- The umbilical cord should fall off by Day 10-14. Redness, swelling, or foul
  smell from the cord is always a red flag, any day.
- Lethargy, not feeding, or labored/fast breathing (>60 breaths/min) are
  IMMEDIATE red flags on ANY visit day — always recommend PHC referral urgently.
- Low birth weight (under 2.5 kg) means higher risk throughout — flag it.
- Temperature below 36.5°C (cold to touch, not feeding well) is hypothermia —
  a red flag requiring immediate warming and PHC referral.
- Central cyanosis (blue lips or tongue) is an IMMEDIATE red flag any day.

Your rules:
- Never diagnose. Support the ASHA worker's judgment.
- Be concise and direct — they are in the field.
- Simple English only, no medical jargon.
- Warm but clear about urgency when needed.

Today's date is $today. Use this to set follow_up_date.

Respond ONLY in this exact JSON format, no other text:
{
  "risk_level": "green",
  "risk_reason": "one sentence explaining the primary concern or why the newborn is low risk",
  "what_sakhi_noticed": ["list all clinically relevant observations — include as many as needed, no padding"],
  "what_to_tell_patient": "1-2 sentences for the ASHA worker to relay to the mother or family",
  "what_to_do_next": "concrete next action for the ASHA worker",
  "follow_up_date": "YYYY-MM-DD or null"
}

risk_level must be one of: "green", "yellow", or "red".
""".trimIndent()

    // ── Chat system prompt (port of CHAT_SYSTEM_PROMPT) ───────────────────────────

    private fun buildChatSystemPrompt(patient: Patient?, ragChunks: String, language: String): String {
        val base = """
Your name is Sakhi. You are an AI clinical companion for ASHA workers in rural India.
You are always speaking directly to the ASHA worker — a trained community health worker
who uses you as an expert colleague for clinical guidance. The patient never uses this app.

The ASHA worker you are speaking to has their own name — address them directly using
"you" (e.g. "You should check...", "For your patient..."). Never use the name "Sakhi"
when addressing the ASHA worker.

Your role:
- Answer the ASHA worker's questions about maternal health, ANC protocols, and patient care
- If patient context is provided, give the ASHA worker specific clinical guidance about that patient
- If clinical guidelines are provided below, use them to inform your answer
- Always address the ASHA worker directly (e.g. "Your patient's BP suggests...", "You should watch for...")
- Never address or speak to the patient

Rules:
- Be concise, warm, and practical. Use simple English.
- Never diagnose. Support the ASHA worker's clinical judgment.
- Always recommend PHC referral if there is any doubt.
- If asked about anything unrelated to maternal or newborn health, politely redirect:
  "I'm here to help with maternal and newborn care. For other questions, please check with your supervisor."

Respond ONLY in this exact JSON format, no other text:
{
  "response": "your answer to the ASHA worker",
  "refer": false
}

Set refer to true if your answer recommends a PHC referral for the patient.
""".trimIndent()

        val contextSection = if (patient != null) buildPatientContextSection(patient) else ""
        val ragSection = if (ragChunks.isNotBlank()) "\n\nRelevant clinical guidelines:\n$ragChunks" else ""
        val hindi = if (language == "hi") "\n\n$HINDI_INSTRUCTION" else ""

        return base + contextSection + ragSection + hindi
    }

    // ── User message builders ─────────────────────────────────────────────────────

    private fun buildCheckupUserMessage(patient: Patient, checkup: Checkup): String = when {
        patient is AncPatient && checkup is AncCheckup -> buildAncUserMessage(patient, checkup)
        patient is NewbornPatient && checkup is NewbornVisit -> buildNewbornUserMessage(patient, checkup)
        else -> error("Mismatched types")
    }

    private fun buildAncUserMessage(patient: AncPatient, checkup: AncCheckup): String {
        val sb = StringBuilder()
        sb.appendLine("Patient: ${patient.name}, ${patient.age} years")
        patient.gestationalWeeks?.let { sb.appendLine("Gestational age: $it weeks") }
        patient.gravida?.let { sb.appendLine("Gravida: $it, Para: ${patient.para ?: "unknown"}") }
        sb.appendLine("Village: ${patient.village}")
        sb.appendLine()
        sb.appendLine("Today's checkup (${checkup.date}):")
        sb.appendLine("  BP: ${checkup.bpSystolic}/${checkup.bpDiastolic} mmHg")
        sb.appendLine("  Weight: ${checkup.weightKg} kg")
        sb.appendLine("  Fundal height: ${checkup.fundalHeightCm} cm")
        checkup.fetalHeartRate?.let { sb.appendLine("  Fetal heart rate: $it bpm") }
        checkup.hemoglobin?.let { sb.appendLine("  Haemoglobin: $it g/dL") }
        if (checkup.symptoms.isNotEmpty()) {
            sb.appendLine("  Symptoms reported: ${checkup.symptoms.joinToString(", ")}")
        }
        val prev = patient.checkupHistory.lastOrNull()
        if (prev != null) {
            sb.appendLine()
            sb.appendLine("Previous visit (${prev.date}): weight ${prev.weightKg} kg, BP ${prev.bpSystolic}/${prev.bpDiastolic} mmHg")
        }
        return sb.toString()
    }

    private fun buildNewbornUserMessage(patient: NewbornPatient, visit: NewbornVisit): String {
        val sb = StringBuilder()
        sb.appendLine("Newborn: ${patient.name}")
        sb.appendLine("Date of birth: ${patient.dateOfBirth}")
        sb.appendLine("Birth weight: ${patient.birthWeightKg} kg")
        sb.appendLine("Mother: ${patient.motherName}")
        sb.appendLine("Village: ${patient.village}")
        sb.appendLine()
        sb.appendLine("Visit: ${visit.visitDay.key.replace("_", " ")} (${visit.date})")
        sb.appendLine("  Current weight: ${visit.weightKg} kg")
        if (visit.observations.isNotEmpty()) {
            sb.appendLine("  Observations: ${visit.observations.joinToString(", ")}")
        }
        if (visit.otherObservations.isNotBlank()) {
            sb.appendLine("  Other: ${visit.otherObservations}")
        }
        return sb.toString()
    }

    private fun buildPatientContextSection(patient: Patient): String {
        val sb = StringBuilder("\n\nCurrent patient context:\n")
        when (patient) {
            is AncPatient -> {
                sb.appendLine("Patient: ${patient.name}, ${patient.age} years, ${patient.gestationalWeeks ?: "?"} weeks pregnant")
                sb.appendLine("Risk level: ${patient.riskLevel.key}")
                patient.checkupHistory.lastOrNull()?.let { last ->
                    sb.appendLine("Last checkup (${last.date}): BP ${last.bpSystolic}/${last.bpDiastolic}, weight ${last.weightKg} kg")
                }
            }
            is NewbornPatient -> {
                sb.appendLine("Newborn: ${patient.name}, birth weight ${patient.birthWeightKg} kg")
                sb.appendLine("Risk level: ${patient.riskLevel.key}")
                patient.visitHistory.lastOrNull()?.let { last ->
                    sb.appendLine("Last visit (${last.date}, ${last.visitDay.key}): weight ${last.weightKg} kg")
                }
            }
        }
        return sb.toString()
    }

    // ── Conversation formatter ────────────────────────────────────────────────────

    /**
     * Format as Gemma 4 instruction-tuned chat template.
     * Template: <start_of_turn>system\n{system}<end_of_turn>\n<start_of_turn>user\n{msg}<end_of_turn>\n<start_of_turn>model\n
     */
    private fun formatConversation(system: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>system\n$system<end_of_turn>\n")
        for (msg in messages) {
            val role = if (msg.role == ChatRole.USER) "user" else "model"
            sb.append("<start_of_turn>$role\n${msg.content}<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")  // Trailing prompt — model completes from here
        return sb.toString()
    }

    // ── Hindi instruction (port of HINDI_INSTRUCTION) ─────────────────────────────

    private val HINDI_INSTRUCTION = """
IMPORTANT LANGUAGE INSTRUCTION:
Respond entirely in Hindi (Devanagari script).
These MUST remain in English: JSON key names, BP, PHC, ASHA, kg, cm, bpm, g/dL, mmHg, dates, and the values "green"/"yellow"/"red" for risk_level.
Example: "risk_reason": "BP 156/100 mmHg है — तुरंत PHC भेजें"
""".trimIndent()
}
