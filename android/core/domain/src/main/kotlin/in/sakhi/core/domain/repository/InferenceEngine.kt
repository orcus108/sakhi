package `in`.sakhi.core.domain.repository

import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatReply
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.Patient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Abstraction over on-device inference.
 *
 * Two implementations:
 *  - MockInferenceEngine  (debug builds, always ready, canned responses with 1.2s delay)
 *  - LiteRTInferenceEngine (release builds, requires model file to be downloaded)
 *
 * The interface lives in :core:domain (pure Kotlin, zero Android SDK) so that
 * LocalAssessmentEngine and use cases can reference it without Android dependencies.
 */
interface InferenceEngine {

    /**
     * Generate a clinical assessment for an ANC or newborn checkup.
     *
     * Called by AssessUseCase after LocalAssessmentEngine has already produced an offline result.
     * The AI result replaces the offline result in Room once available.
     *
     * @param patient Full patient record (used for context in prompt)
     * @param checkup The checkup data recorded this visit
     * @param language "en" or "hi"
     * @return AssessmentResult with isOffline = false
     */
    suspend fun generateCheckupAssessment(
        patient: Patient,
        checkup: Checkup,
        language: String
    ): AssessmentResult

    /**
     * Generate a chat reply in the Ask Sakhi conversation.
     *
     * @param messages Full conversation history (user + assistant turns)
     * @param patientContext Optional current patient — injected into system prompt
     * @param language "en" or "hi"
     */
    suspend fun generateChatReply(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        language: String
    ): ChatReply

    /**
     * Like [generateChatReply] but streams partial [ChatReply] values as tokens arrive.
     *
     * Each emitted value has [ChatReply.content] set to the text accumulated so far and
     * [ChatReply.refer] = false. The final emission has the fully-parsed refer flag.
     *
     * Default implementation emits exactly one value (the complete reply), so
     * MockInferenceEngine and LiteRTInferenceEngine work without changes.
     */
    fun generateChatReplyStream(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        language: String
    ): Flow<ChatReply> = flow {
        emit(generateChatReply(messages, patientContext, language))
    }

    /**
     * Returns true when the model is loaded and inference is possible.
     * MockInferenceEngine always returns true.
     * LiteRTInferenceEngine returns true only after the .litertlm file is downloaded and loaded.
     */
    fun isReady(): Boolean

    /**
     * Load the model file and prepare the inference engine.
     * Must be called on a background thread — can take 5-10 seconds.
     *
     * No-op by default (MockInferenceEngine is always ready).
     * LiteRTInferenceEngine overrides this to call Engine.initialize().
     *
     * Called in two places:
     *  1. StartupViewModel init — when the model file already exists on cold start
     *  2. StartupViewModel.onModelDownloaded — after DownloadWorker succeeds
     */
    fun initialize(modelPath: String) {}
}
