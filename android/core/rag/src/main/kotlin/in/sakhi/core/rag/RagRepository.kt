package `in`.sakhi.core.rag

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrieves relevant guideline chunks for RAG (Retrieval-Augmented Generation).
 *
 * Uses FTS5 BM25 keyword search — appropriate for clinical content because:
 * - Medical terminology is exact (MOHFW/WHO numeric thresholds, procedure names)
 * - Semantic search gives no meaningful advantage over BM25 for "BP >140/90"
 * - Pre-built index ships in assets (~3-8 MB); no runtime ingestion
 * - FTS5 is built into Android SQLite (API 26+, guaranteed)
 *
 * Query sanitization:
 * FTS5 special characters (", *, !, ^, :, (, ), .) are removed before querying.
 * Multi-word queries are wrapped in double-quotes for phrase matching where the
 * query is 2-4 words; otherwise terms are searched individually (OR semantics).
 *
 * Result format: chunks are joined with newlines, ready for system prompt injection.
 */
@Singleton
class RagRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: GuidelineDao
) {

    companion object {
        private const val DEFAULT_TOP_K = 3
        // FTS5 special characters to strip from user queries
        private val FTS5_SPECIAL = Regex("""["\*\!\^\:\(\)\.]""")
    }

    /**
     * Retrieve top-k guideline chunks relevant to [query].
     *
     * @return concatenated chunks as a single string, or empty string if no results.
     */
    suspend fun retrieve(query: String, topK: Int = DEFAULT_TOP_K): String =
        withContext(Dispatchers.IO) {
            val sanitized = sanitize(query)
            if (sanitized.isEmpty()) return@withContext ""
            val chunks = dao.search(sanitized, topK)
            chunks.joinToString("\n\n---\n\n")
        }

    private fun sanitize(raw: String): String {
        val cleaned = raw.trim().replace(FTS5_SPECIAL, " ").replace(Regex("\\s+"), " ")
        val words = cleaned.split(" ").filter { it.length > 2 }
        if (words.isEmpty()) return ""
        return if (words.size in 2..4) {
            "\"${words.joinToString(" ")}\""   // phrase match
        } else {
            words.joinToString(" ")            // individual term match (OR)
        }
    }
}
