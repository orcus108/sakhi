package `in`.sakhi.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.network.SupabaseAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class OtpSent(val phone: String) : AuthState
    data object Verified : AuthState
    data class Error(val message: String) : AuthState
}

sealed interface OnboardingState {
    data object Idle : OnboardingState
    data class Filled(val name: String, val language: String, val consentGiven: Boolean) : OnboardingState
    data object Complete : OnboardingState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseAuth: SupabaseAuthManager,
    private val authPrefs: AuthPreferences
) : ViewModel() {

    // ── Onboarding state ──────────────────────────────────────────────────────────

    private val _workerName = MutableStateFlow("")
    val workerName: StateFlow<String> = _workerName.asStateFlow()

    private val _ashaWorkerId = MutableStateFlow("")
    val ashaWorkerId: StateFlow<String> = _ashaWorkerId.asStateFlow()

    private val _language = MutableStateFlow(authPrefs.getLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    private val _consentGiven = MutableStateFlow(false)
    val consentGiven: StateFlow<Boolean> = _consentGiven.asStateFlow()

    fun onNameChange(name: String) { _workerName.value = name }
    fun onAshaWorkerIdChange(id: String) { _ashaWorkerId.value = id }
    fun onLanguageChange(lang: String) { _language.value = lang }
    fun onConsentChange(given: Boolean) { _consentGiven.value = given }

    // ── OTP auth state ────────────────────────────────────────────────────────────

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _resendCountdown = MutableStateFlow(0)
    val resendCountdown: StateFlow<Int> = _resendCountdown.asStateFlow()

    fun onPhoneChange(p: String) { _phone.value = p.filter { it.isDigit() }.take(10) }

    fun sendOtp() {
        val p = _phone.value.trim()
        if (p.length != 10) {
            _authState.value = AuthState.Error("Enter a valid 10-digit number")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                supabaseAuth.sendOtp("+91$p")
                _authState.value = AuthState.OtpSent(p)
                startResendCountdown()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send OTP")
            }
        }
    }

    fun verifyOtp(otp: String) {
        val p = _phone.value.trim()
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val session = supabaseAuth.verifyOtp("+91$p", otp)
                // Persist worker info alongside session
                authPrefs.saveSession(
                    userId = session.userId,
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken
                )
                authPrefs.saveWorkerInfo(
                    name = _workerName.value,
                    phone = p,
                    language = _language.value
                )
                authPrefs.saveAshaId(_ashaWorkerId.value.trim())
                _authState.value = AuthState.Verified
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Invalid OTP — please try again")
            }
        }
    }

    fun resendOtp() {
        if (_resendCountdown.value > 0) return
        sendOtp()
    }

    private fun startResendCountdown() {
        viewModelScope.launch {
            _resendCountdown.value = 60
            while (_resendCountdown.value > 0) {
                delay(1000)
                _resendCountdown.value -= 1
            }
        }
    }

    fun isOnboardingValid(): Boolean =
        _workerName.value.trim().isNotEmpty() && _ashaWorkerId.value.trim().isNotEmpty()

    /** Skip OTP — save a local-only session for development/testing. */
    fun completeWithoutOtp() {
        authPrefs.saveSession(
            userId = "debug-user",
            accessToken = "",
            refreshToken = ""
        )
        authPrefs.saveWorkerInfo(
            name = _workerName.value.trim().ifBlank { "ASHA Worker" },
            phone = "",
            language = _language.value
        )
        authPrefs.saveAshaId(_ashaWorkerId.value.trim())
        _authState.value = AuthState.Verified
    }
}
