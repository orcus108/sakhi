package `in`.sakhi.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import javax.inject.Inject
import javax.inject.Singleton

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String
)

/**
 * Wraps Supabase Auth for phone OTP flow.
 *
 * DEV NOTE (emulator): The Android emulator cannot receive real SMS.
 * Configure a test OTP in the Supabase Dashboard:
 *   Authentication → Phone → Test SMS numbers → add your test number + OTP
 * Or set {"phone": {"test_otp": "123456"}} in project Auth settings for dev.
 */
@Singleton
class SupabaseAuthManager @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val auth get() = supabase.auth

    suspend fun sendOtp(e164Phone: String) {
        auth.signInWith(OTP) {
            phone = e164Phone
        }
    }

    suspend fun verifyOtp(e164Phone: String, token: String): AuthSession {
        auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = e164Phone, token = token)
        val session = auth.currentSessionOrNull()
            ?: throw IllegalStateException("Verification succeeded but no session found")
        return AuthSession(
            userId = session.user?.id ?: throw IllegalStateException("No user in session"),
            accessToken = session.accessToken,
            refreshToken = session.refreshToken ?: ""
        )
    }

    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null

    suspend fun signOut() {
        auth.signOut()
    }

    fun getUserId(): String? = auth.currentSessionOrNull()?.user?.id
}
