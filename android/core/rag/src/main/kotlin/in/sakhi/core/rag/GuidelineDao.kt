package `in`.sakhi.core.rag

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raw SQLite access to the FTS5 guidelines index.
 * Not a Room DAO — Room cannot model FTS5 virtual tables as @Entity classes.
 */
class GuidelineDao(private val db: SQLiteDatabase?) {

    suspend fun search(query: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext emptyList()
        try {
            val cursor = database.rawQuery(
                "SELECT chunk_text FROM guidelines_fts WHERE guidelines_fts MATCH ? ORDER BY bm25(guidelines_fts) LIMIT ?",
                arrayOf(query, limit.toString())
            )
            cursor.use { c ->
                val results = mutableListOf<String>()
                while (c.moveToNext()) results.add(c.getString(0))
                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext 0
        try {
            database.rawQuery("SELECT count(*) FROM guidelines_fts", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
