package `in`.sakhi.core.domain.repository

import `in`.sakhi.core.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Chat messages are session-local only — not synced to Supabase.
 * Stored in Room so conversation persists across app restarts within a session.
 * Sessions are cleared when the user starts a new conversation.
 */
interface ChatRepository {
    fun observeSession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun appendMessage(message: ChatMessage)
    suspend fun clearSession(sessionId: String)
}
