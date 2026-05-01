package `in`.sakhi.app.debug

/**
 * Seeds a sample ASHA worker + 15 demo patients into the local database on first
 * debug launch. The release implementation is a no-op.
 */
interface DebugDataSeeder {
    suspend fun seedIfNeeded()
}
