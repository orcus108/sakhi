package `in`.sakhi.core.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores authentication tokens and user identity in EncryptedSharedPreferences.
 * DISHA compliance: all PII (JWT, user ID, phone) encrypted at rest via Android Keystore.
 */
@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sakhi_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(userId: String, accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveWorkerInfo(name: String, phone: String, language: String) {
        prefs.edit()
            .putString(KEY_WORKER_NAME, name)
            .putString(KEY_WORKER_PHONE, phone)
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun saveAshaId(id: String) = prefs.edit().putString(KEY_ASHA_ID, id).apply()
    fun getAshaId(): String = prefs.getString(KEY_ASHA_ID, "") ?: ""

    fun getWorkerName(): String? = prefs.getString(KEY_WORKER_NAME, null)
    fun getWorkerPhone(): String? = prefs.getString(KEY_WORKER_PHONE, null)
    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

    fun setLanguage(code: String) = prefs.edit().putString(KEY_LANGUAGE, code).apply()

    fun isLoggedIn(): Boolean = getUserId() != null && getAccessToken() != null

    /** Unix-ms timestamp of the last successful Supabase pull. 0 on first sync (fetches all). */
    fun getLastSyncAt(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
    fun setLastSyncAt(timestampMs: Long) = prefs.edit().putLong(KEY_LAST_SYNC_AT, timestampMs).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_WORKER_NAME = "worker_name"
        private const val KEY_WORKER_PHONE = "worker_phone"
        private const val KEY_ASHA_ID = "asha_id"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}
