package `in`.sakhi.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentResult(
    val id: String,
    val checkupId: String,
    val patientId: String,
    val patientType: String,               // "anc" | "newborn"
    val riskLevel: RiskLevel,
    val riskReason: String,
    val whatSakhiNoticed: List<String>,
    val whatToTellPatient: String,
    val whatToDoNext: String,
    val followUpDate: String? = null,      // YYYY-MM-DD or null
    val isOffline: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val serverId: String? = null,
    val dirty: Boolean = true,
    val lastSyncedAt: Long? = null
)

@Serializable
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val patientId: String? = null,
    val patientType: String? = null,
    val role: ChatRole,
    val content: String,
    val refer: Boolean = false,            // assistant-only: true = recommend PHC referral
    val createdAt: Long = System.currentTimeMillis()
)

enum class ChatRole { USER, ASSISTANT }

@Serializable
data class ChatReply(
    val content: String,
    val refer: Boolean = false
)
