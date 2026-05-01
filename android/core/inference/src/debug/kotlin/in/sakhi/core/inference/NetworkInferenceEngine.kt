package `in`.sakhi.core.inference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatReply
import `in`.sakhi.core.domain.model.ChatRole
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.Patient
import `in`.sakhi.core.domain.repository.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only InferenceEngine that calls Ollama running on the host machine.
 * From the Android emulator, 10.0.2.2 maps to the host's localhost.
 *
 * Prerequisites:
 *   ollama serve                  (Ollama must be running)
 *   ollama pull gemma4:e2b        (already done)
 *
 * Bound by InferenceModule (debug variant) in place of MockInferenceEngine.
 */
@Singleton
class NetworkInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val promptBuilder: PromptBuilder,
) : InferenceEngine {

    override fun isReady(): Boolean = true

    override suspend fun generateCheckupAssessment(
        patient: Patient,
        checkup: Checkup,
        language: String,
    ): AssessmentResult = withContext(Dispatchers.IO) {
        val formatted = promptBuilder.buildCheckupPrompt(patient, checkup, language)
        val reply = complete(listOf(
            Msg("system", systemFrom(formatted)),
            Msg("user",   userFrom(formatted)),
        ))
        ResponseParser.parseAssessmentResponse(
            raw         = reply,
            checkupId   = checkup.id,
            patientId   = patient.id,
            patientType = if (patient is NewbornPatient) "newborn" else "anc",
        )
    }

    override suspend fun generateChatReply(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        language: String,
    ): ChatReply = withContext(Dispatchers.IO) {
        val formatted = promptBuilder.buildChatPrompt(messages, patientContext, ragChunks = "", language)
        val system = systemFrom(formatted)
        val ollamaMsgs = buildList {
            add(Msg("system", system))
            messages.forEach { m ->
                add(Msg(if (m.role == ChatRole.USER) "user" else "assistant", m.content))
            }
        }
        ResponseParser.parseChatReply(complete(ollamaMsgs))
    }

    override fun generateChatReplyStream(
        messages: List<ChatMessage>,
        patientContext: Patient?,
        language: String,
    ): Flow<ChatReply> = flow {
        val formatted = promptBuilder.buildChatPrompt(messages, patientContext, ragChunks = "", language)
        val system = systemFrom(formatted)
        val ollamaMsgs = buildList {
            add(Msg("system", system))
            messages.forEach { m ->
                add(Msg(if (m.role == ChatRole.USER) "user" else "assistant", m.content))
            }
        }

        val rawBuf = StringBuilder()
        val extractor = JsonStreamExtractor()

        streamTokens(ollamaMsgs).collect { token ->
            rawBuf.append(token)
            val newDisplay = extractor.push(token)
            if (newDisplay.isNotEmpty()) {
                emit(ChatReply(content = extractor.currentDisplay, refer = false))
            }
        }

        val final = try {
            ResponseParser.parseChatReply(rawBuf.toString())
        } catch (_: Exception) {
            ChatReply(content = extractor.currentDisplay, refer = false)
        }
        emit(final)
    }.flowOn(Dispatchers.IO)

    // ── HTTP (non-streaming) ─────────────────────────────────────────────────────

    private fun complete(messages: List<Msg>): String {
        val body = json.encodeToString(ChatRequest(messages = messages))
        val conn = URL("$BASE/v1/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput      = true
        conn.connectTimeout = 10_000
        conn.readTimeout    = 120_000

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val text = (if (code == 200) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        conn.disconnect()

        check(code == 200) { "Ollama $code: $text" }
        return json.decodeFromString<ChatResponse>(text).choices.first().message.content
    }

    // ── HTTP (streaming SSE) ─────────────────────────────────────────────────────

    private fun streamTokens(messages: List<Msg>): Flow<String> = flow {
        val body = json.encodeToString(ChatRequest(messages = messages, stream = true))
        val conn = URL("$BASE/v1/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput      = true
        conn.connectTimeout = 10_000
        conn.readTimeout    = 120_000

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            error("Ollama $code: $err")
        }
        try {
            conn.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val data = line!!
                    if (!data.startsWith("data: ")) continue
                    val payload = data.removePrefix("data: ").trim()
                    if (payload == "[DONE]") break
                    val chunk = json.decodeFromString<StreamChunk>(payload)
                    val token = chunk.choices.firstOrNull()?.delta?.content ?: continue
                    if (token.isNotEmpty()) emit(token)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    // ── Template parsing ──────────────────────────────────────────────────────────

    private fun systemFrom(p: String) = between(p, "<start_of_turn>system\n", "<end_of_turn>")
    private fun userFrom(p: String)   = between(p, "<start_of_turn>user\n",   "<end_of_turn>")

    private fun between(src: String, open: String, close: String): String {
        val s = src.indexOf(open); val e = src.indexOf(close, s)
        return if (s == -1 || e == -1) "" else src.substring(s + open.length, e)
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────────

    @Serializable private data class Msg(val role: String, val content: String)

    @Serializable private data class ChatRequest(
        val model: String = MODEL,
        val messages: List<Msg>,
        val stream: Boolean = false,
        val temperature: Float = 0.2f,
    )

    @Serializable private data class ChatResponse(val choices: List<Choice>) {
        @Serializable data class Choice(val message: Msg)
    }

    @Serializable private data class StreamChunk(val choices: List<StreamChoice>) {
        @Serializable data class StreamChoice(val delta: Delta, val finish_reason: String? = null)
        @Serializable data class Delta(val content: String? = null, val role: String? = null)
    }

    companion object {
        private const val BASE  = "http://10.0.2.2:11434"
        private const val MODEL = "gemma4:e2b"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/**
 * Incrementally extracts the displayable text from a streamed JSON response of the form:
 *   {"response": "...text...", "refer": false}
 *
 * Tokens are fed in one at a time via [push]. Each call returns only the NEW displayable
 * characters introduced by that token. [currentDisplay] returns all text accumulated so far.
 */
private class JsonStreamExtractor {

    private val full    = StringBuilder()
    private val display = StringBuilder()
    private var valueOffset = -1   // index in `full` where the response value starts
    private var inValue = false
    private var prevBackslash = false

    val currentDisplay: String get() = display.toString()

    fun push(chunk: String): String {
        full.append(chunk)
        return when {
            inValue -> processValueContent(chunk)
            valueOffset == -1 -> {
                val offset = findResponseValueOffset(full.toString())
                if (offset == -1) return ""
                valueOffset = offset
                inValue = true
                // Process any content already in the buffer after the opening quote
                processValueContent(full.toString().substring(valueOffset))
            }
            else -> "" // value already fully extracted
        }
    }

    private fun findResponseValueOffset(s: String): Int {
        for (pattern in listOf("\"response\":\"", "\"response\": \"")) {
            val idx = s.indexOf(pattern)
            if (idx != -1) return idx + pattern.length
        }
        return -1
    }

    private fun processValueContent(s: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < s.length && inValue) {
            val c = s[i]
            if (prevBackslash) {
                prevBackslash = false
                val decoded = when (c) {
                    '"'  -> '"'
                    'n'  -> '\n'
                    't'  -> '\t'
                    'r'  -> '\r'
                    '\\' -> '\\'
                    else -> c
                }
                result.append(decoded)
                display.append(decoded)
                i++
                continue
            }
            when (c) {
                '\\' -> { prevBackslash = true; i++ }
                '"'  -> { inValue = false; i++ }
                else -> { result.append(c); display.append(c); i++ }
            }
        }
        return result.toString()
    }
}
