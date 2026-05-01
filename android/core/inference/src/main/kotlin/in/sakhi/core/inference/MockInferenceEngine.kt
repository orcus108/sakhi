package `in`.sakhi.core.inference

import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatReply
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.Patient
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.repository.InferenceEngine
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Development implementation of InferenceEngine.
 *
 * Always ready. Simulates 1.2 second inference delay.
 * Returns canned [MOCK]-prefixed responses with realistic field shapes.
 *
 * Used in debug builds via InferenceModule. LiteRT cannot run in the x86_64
 * emulator (it requires arm64-v8a), so this is the only way to test
 * inference-dependent screens on M1 Mac + emulator.
 */
@Singleton
class MockInferenceEngine @Inject constructor() : InferenceEngine {

    override fun isReady(): Boolean = true

    override suspend fun generateCheckupAssessment(
        patient: Patient,
        checkup: Checkup,
        language: String
    ): AssessmentResult {
        delay(1200)  // Simulate on-device inference time
        return AssessmentResult(
            id = UUID.randomUUID().toString(),
            checkupId = checkup.id,
            patientId = patient.id,
            patientType = if (patient is `in`.sakhi.core.domain.model.NewbornPatient) "newborn" else "anc",
            riskLevel = RiskLevel.YELLOW,
            riskReason = "[MOCK] Some findings need monitoring — follow up within 7 days",
            whatSakhiNoticed = listOf(
                "[MOCK] Blood pressure within acceptable range",
                "[MOCK] Haemoglobin slightly below normal — IFA tablets recommended",
                "[MOCK] AI model not loaded — this is a development placeholder"
            ),
            whatToTellPatient = if (language == "hi")
                "[MOCK] अपनी IFA गोलियाँ रोज़ लें और 7 दिन में वापस आएँ।"
            else
                "[MOCK] Take your IFA tablets daily and return within 7 days.",
            whatToDoNext = "[MOCK] Schedule follow-up within 7 days. Provide IFA tablets and nutrition counselling.",
            followUpDate = LocalDate.now().plusDays(7).toString(),
            isOffline = false
        )
    }

    override suspend fun generateChatReply(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        language: String
    ): ChatReply {
        delay(800)
        val content = if (language == "hi")
            "[MOCK] यह एक development प्रतिक्रिया है। production में, Gemma 4 E2B on-device उत्तर देगा।"
        else
            "[MOCK] This is a development response. In production, Gemma 4 E2B will answer on-device using LiteRT-LM."
        return ChatReply(content = content, refer = false)
    }
}
