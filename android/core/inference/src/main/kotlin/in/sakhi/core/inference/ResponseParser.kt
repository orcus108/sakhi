package `in`.sakhi.core.inference

import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.ChatReply
import `in`.sakhi.core.domain.model.RiskLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Parses raw model output into typed domain objects.
 *
 * LiteRT-LM (Gemma 4 E2B) often wraps JSON in markdown code fences,
 * matching the behavior observed in the Python backend.
 * Python backend strips these with: re.sub(r"```(?:json)?\s*", "", raw)
 * We replicate that regex here.
 */
object ResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true        // tolerate minor formatting issues from the model
    }

    // ── Checkup Assessment ────────────────────────────────────────────────────────

    fun parseAssessmentResponse(
        raw: String,
        checkupId: String,
        patientId: String,
        patientType: String
    ): AssessmentResult {
        val cleaned = stripMarkdownFences(raw)
        val dto = json.decodeFromString<AssessmentResponseDto>(cleaned)
        return AssessmentResult(
            id = UUID.randomUUID().toString(),
            checkupId = checkupId,
            patientId = patientId,
            patientType = patientType,
            riskLevel = RiskLevel.from(dto.riskLevel),
            riskReason = dto.riskReason,
            whatSakhiNoticed = dto.whatSakhiNoticed,
            whatToTellPatient = dto.whatToTellPatient,
            whatToDoNext = dto.whatToDoNext,
            followUpDate = dto.followUpDate,
            isOffline = false
        )
    }

    // ── Chat Reply ────────────────────────────────────────────────────────────────

    fun parseChatReply(raw: String): ChatReply {
        val cleaned = stripMarkdownFences(raw)
        val dto = json.decodeFromString<ChatReplyDto>(cleaned)
        return ChatReply(content = dto.response, refer = dto.refer)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /**
     * Strips markdown code fences from model output.
     * Handles: ```json\n...\n```, ```\n...\n```, and bare output.
     * Equivalent to Python: re.sub(r"```(?:json)?\s*", "", raw).strip()
     */
    fun stripMarkdownFences(raw: String): String {
        return raw
            .replace(Regex("```(?:json)?\\s*"), "")
            .trim()
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────────

    @Serializable
    private data class AssessmentResponseDto(
        @SerialName("risk_level") val riskLevel: String,
        @SerialName("risk_reason") val riskReason: String,
        @SerialName("what_sakhi_noticed") val whatSakhiNoticed: List<String>,
        @SerialName("what_to_tell_patient") val whatToTellPatient: String,
        @SerialName("what_to_do_next") val whatToDoNext: String,
        @SerialName("follow_up_date") val followUpDate: String? = null
    )

    @Serializable
    private data class ChatReplyDto(
        val response: String,
        val refer: Boolean = false
    )
}
