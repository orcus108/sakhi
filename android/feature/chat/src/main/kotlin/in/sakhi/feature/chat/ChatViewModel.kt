package `in`.sakhi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatRole
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.Patient
import `in`.sakhi.core.domain.repository.ChatRepository
import `in`.sakhi.core.domain.repository.InferenceEngine
import `in`.sakhi.core.domain.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import `in`.sakhi.core.domain.model.ChatReply
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val streamingContent: String? = null,  // non-null while tokens are arriving
    val error: String? = null,
    val modelReady: Boolean = false,
    val patientName: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val patientRepository: PatientRepository,
    private val inferenceEngine: InferenceEngine,
    private val authPrefs: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val sessionId = UUID.randomUUID().toString()
    private var patientContext: Patient? = null
    val language: String get() = authPrefs.getLanguage()

    init {
        // Observe chat messages for this session
        chatRepository.observeSession(sessionId).onEach { messages ->
            _uiState.value = _uiState.value.copy(messages = messages)
        }.launchIn(viewModelScope)

        // Check model readiness
        _uiState.value = _uiState.value.copy(modelReady = inferenceEngine.isReady())
    }

    /**
     * Load patient context for this chat session.
     * Keeps only lean fields in memory — no full checkup history (token efficiency).
     */
    fun loadPatient(patientId: String, patientType: String) {
        viewModelScope.launch {
            val patient = if (patientType == "newborn") {
                patientRepository.getNewbornPatient(patientId)
            } else {
                patientRepository.getAncPatient(patientId)
            }
            patientContext = patient
            _uiState.value = _uiState.value.copy(
                patientName = patient?.name
            )
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading) return

        viewModelScope.launch {
            // Snapshot messages BEFORE inserting — Room flow update is async
            // so _uiState.value.messages won't reflect the new message yet.
            val previousMessages = _uiState.value.messages
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                patientId = patientContext?.id,
                patientType = when (patientContext) {
                    is AncPatient -> "anc"
                    is NewbornPatient -> "newborn"
                    else -> null
                },
                role = ChatRole.USER,
                content = text
            )
            chatRepository.appendMessage(userMsg)
            _uiState.value = _uiState.value.copy(inputText = "", isLoading = true, error = null)

            try {
                val history = (previousMessages + userMsg).takeLast(10)
                var lastReply: ChatReply? = null
                inferenceEngine.generateChatReplyStream(
                    messages = history,
                    patientContext = patientContext,
                    language = language
                ).onEach { partial ->
                    lastReply = partial
                    _uiState.value = _uiState.value.copy(streamingContent = partial.content)
                }.collect { }

                val reply = lastReply ?: ChatReply(content = "")
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    patientId = patientContext?.id,
                    patientType = when (patientContext) {
                        is AncPatient -> "anc"
                        is NewbornPatient -> "newborn"
                        else -> null
                    },
                    role = ChatRole.ASSISTANT,
                    content = reply.content,
                    refer = reply.refer
                )
                chatRepository.appendMessage(assistantMsg)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Could not get response — ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, streamingContent = null)
            }
        }
    }

    fun sendQuickQuestion(question: String) {
        _uiState.value = _uiState.value.copy(inputText = question)
        send()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSession() {
        viewModelScope.launch {
            chatRepository.clearSession(sessionId)
        }
    }
}
