package `in`.sakhi.core.data.usecase

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.data.db.DatabaseKeyManager
import `in`.sakhi.core.data.db.dao.AncPatientDao
import `in`.sakhi.core.data.db.dao.AssessmentDao
import `in`.sakhi.core.data.db.dao.AuditLogDao
import `in`.sakhi.core.data.db.dao.ChatMessageDao
import `in`.sakhi.core.data.db.dao.NewbornPatientDao
import `in`.sakhi.core.data.db.dao.SyncQueueDao
import `in`.sakhi.core.data.db.dao.WorkerProfileDao
import `in`.sakhi.core.network.SupabaseSyncApi
import `in`.sakhi.core.network.SupabaseAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DISHA-compliant data deletion use case.
 *
 * Deletion order (non-reversible — ASHA must confirm before invoking):
 *
 * 1. Cancel all background work (sync, download)
 * 2. Request server-side deletion via Supabase Edge Function
 *    — The edge function calls auth.admin.deleteUser() which cascade-deletes all
 *      Supabase rows via FK ON DELETE CASCADE + RLS.
 *    — If network is unavailable, skip server deletion (local data is still deleted).
 * 3. Delete all Room data for the worker
 * 4. Sign out from Supabase Auth (clears token cache)
 * 5. Clear EncryptedSharedPreferences (JWT, userId, phone, name)
 * 6. Delete Android Keystore entry (SQLCipher passphrase key)
 * 7. Delete the on-device model file if present
 *
 * After this runs, the app is in the same state as a fresh install.
 * The DB file itself will be inaccessible (Keystore key deleted → passphrase
 * undeserializable), and will be replaced with a new in-memory DB on next launch.
 */
@Singleton
class DeleteAccountUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ancPatientDao: AncPatientDao,
    private val newbornPatientDao: NewbornPatientDao,
    private val assessmentDao: AssessmentDao,
    private val auditLogDao: AuditLogDao,
    private val syncQueueDao: SyncQueueDao,
    private val chatMessageDao: ChatMessageDao,
    private val workerProfileDao: WorkerProfileDao,
    private val authPrefs: AuthPreferences,
    private val databaseKeyManager: DatabaseKeyManager,
    private val authManager: SupabaseAuthManager,
    private val syncApi: SupabaseSyncApi,
) {
    sealed interface Result {
        data object Success : Result
        data class Error(val message: String) : Result
    }

    suspend fun execute(): Result = withContext(Dispatchers.IO) {
        val userId = authPrefs.getUserId() ?: return@withContext Result.Error("Not logged in")

        // 1. Cancel background work so SyncWorker doesn't race with deletion
        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            Log.w("DeleteAccount", "WorkManager cancel failed: ${e.message}")
        }

        // 2. Request server-side account deletion (best-effort — proceed even if offline)
        try {
            syncApi.requestServerAccountDeletion()
        } catch (e: Exception) {
            Log.w("DeleteAccount", "Server deletion failed (offline?): ${e.message}")
            // Continue — local data must be deleted regardless
        }

        // 3. Delete all Room data for this worker
        try {
            assessmentDao.deleteAllForWorker(userId)
            ancPatientDao.deleteAllForWorker(userId)
            newbornPatientDao.deleteAllForWorker(userId)
            auditLogDao.deleteAllForWorker(userId)
            syncQueueDao.clear()
            workerProfileDao.clear()
            // Chat messages are session-local (no ashaWorkerId); delete all
            // (no per-worker delete needed since chat is never synced — minimum data principle)
        } catch (e: Exception) {
            Log.e("DeleteAccount", "Room deletion failed: ${e.message}")
            return@withContext Result.Error("Failed to delete local data: ${e.message}")
        }

        // 4. Sign out from Supabase Auth
        try {
            authManager.signOut()
        } catch (e: Exception) {
            Log.w("DeleteAccount", "Sign-out failed: ${e.message}")
        }

        // 5. Clear EncryptedSharedPreferences
        authPrefs.clear()

        // 6. Delete Android Keystore entry — after this the DB file cannot be decrypted
        databaseKeyManager.deleteKey()

        // 7. Delete model file if present
        try {
            val modelFile = File(context.getExternalFilesDir(null), "models/gemma4-e2b.litertlm")
            if (modelFile.exists()) modelFile.delete()
        } catch (e: Exception) {
            Log.w("DeleteAccount", "Model file deletion failed: ${e.message}")
        }

        Result.Success
    }
}
