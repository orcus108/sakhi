package `in`.sakhi.core.rag

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Opens the pre-built FTS5 guidelines database from assets.
 * Not a Room database — Room can't validate FTS5 virtual tables at compile time.
 *
 * Returns null if the asset doesn't exist (index not yet built with build_fts_index.py).
 * When null, [GuidelineDao] returns empty results and chat still works without RAG context.
 */
object GuidelinesDatabase {
    private const val DB_NAME = "guidelines_fts.db"

    fun open(context: Context): SQLiteDatabase? {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                context.assets.open(DB_NAME).use { input ->
                    dbFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
        } catch (_: Exception) {
            null  // asset not built yet — RagRepository returns empty string
        }
    }
}
