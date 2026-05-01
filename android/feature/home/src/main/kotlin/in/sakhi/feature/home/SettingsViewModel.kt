package `in`.sakhi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.data.usecase.DeleteAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DeleteState {
    data object Idle : DeleteState
    data object Confirming : DeleteState
    data object Deleting : DeleteState
    data object Deleted : DeleteState
    data class Error(val message: String) : DeleteState
}

data class SettingsUiState(
    val workerName: String = "",
    val workerPhone: String = "",
    val language: String = "en",
    val deleteState: DeleteState = DeleteState.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authPrefs: AuthPreferences,
    private val deleteAccountUseCase: DeleteAccountUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            workerName = authPrefs.getWorkerName() ?: "",
            workerPhone = maskPhone(authPrefs.getWorkerPhone() ?: ""),
            language = authPrefs.getLanguage(),
        )
    }

    fun requestDeleteAccount() {
        _uiState.value = _uiState.value.copy(deleteState = DeleteState.Confirming)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(deleteState = DeleteState.Idle)
    }

    fun confirmDeleteAccount() {
        _uiState.value = _uiState.value.copy(deleteState = DeleteState.Deleting)
        viewModelScope.launch {
            when (val result = deleteAccountUseCase.execute()) {
                is DeleteAccountUseCase.Result.Success ->
                    _uiState.value = _uiState.value.copy(deleteState = DeleteState.Deleted)
                is DeleteAccountUseCase.Result.Error ->
                    _uiState.value = _uiState.value.copy(deleteState = DeleteState.Error(result.message))
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(deleteState = DeleteState.Idle)
    }

    /** Mask phone: +91XXXXXXXX89 → +91 XXXXXX89 */
    private fun maskPhone(phone: String): String {
        if (phone.length < 4) return phone
        val last4 = phone.takeLast(4)
        val masked = phone.dropLast(4).map { if (it.isDigit()) 'X' else it }.joinToString("")
        return "$masked$last4"
    }
}
