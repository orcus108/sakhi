package `in`.sakhi.core.data.repository

import `in`.sakhi.core.data.db.dao.ChatMessageDao
import `in`.sakhi.core.data.db.entity.toDomain
import `in`.sakhi.core.data.db.entity.toEntity
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao
) : ChatRepository {

    override fun observeSession(sessionId: String): Flow<List<ChatMessage>> =
        dao.observeSession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun appendMessage(message: ChatMessage) =
        dao.insert(message.toEntity())

    override suspend fun clearSession(sessionId: String) =
        dao.clearSession(sessionId)
}
