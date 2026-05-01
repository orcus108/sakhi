package `in`.sakhi.core.inference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatReply
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.Patient
import `in`.sakhi.core.domain.repository.InferenceEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug stub — LiteRT-LM is never used in debug builds.
 * InferenceModule binds MockInferenceEngine instead.
 */
@Singleton
class LiteRTInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val promptBuilder: PromptBuilder
) : InferenceEngine {

    override fun initialize(modelPath: String): Unit =
        error("LiteRTInferenceEngine is not available in debug builds")

    override fun isReady(): Boolean = false

    override suspend fun generateCheckupAssessment(
        patient: Patient,
        checkup: Checkup,
        language: String
    ): AssessmentResult = error("LiteRTInferenceEngine is not available in debug builds")

    override suspend fun generateChatReply(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        language: String
    ): ChatReply = error("LiteRTInferenceEngine is not available in debug builds")
}
