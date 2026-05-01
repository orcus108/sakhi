package `in`.sakhi.app.startup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.sakhi.app.BuildConfig
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.domain.repository.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface StartDestination {
    /** User is not logged in — go to Onboarding. */
    data object Onboarding : StartDestination

    /**
     * User is logged in but model file is missing/corrupt (release build only).
     * Debug builds always use MockInferenceEngine and skip this.
     */
    data object Download : StartDestination

    /** User is logged in and app is ready. */
    data object Home : StartDestination
}

/**
 * Determines the app's start destination on launch.
 * Runs synchronously (cheap file check + shared pref read) so the NavHost
 * can render the correct initial screen without any loading state.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPrefs: AuthPreferences,
    private val inferenceEngine: InferenceEngine,
) : ViewModel() {

    private val _startDestination = MutableStateFlow(resolveStartDestination())
    val startDestination: StateFlow<StartDestination> = _startDestination.asStateFlow()

    private fun resolveStartDestination(): StartDestination {
        if (!authPrefs.isLoggedIn()) return StartDestination.Onboarding

        // Debug builds always use MockInferenceEngine — no model download needed.
        if (BuildConfig.USE_MOCK_INFERENCE) return StartDestination.Home

        // Release build: check if the model file is present
        val modelFile = File(context.getExternalFilesDir(null), "models/gemma4-e2b.litertlm")
        return if (modelFile.exists() && modelFile.length() > 0) {
            // Initialize the engine on a background thread — cold start with model present.
            viewModelScope.launch(Dispatchers.IO) {
                inferenceEngine.initialize(modelFile.absolutePath)
            }
            StartDestination.Home
        } else {
            StartDestination.Download
        }
    }

    /** Called after a successful OTP login so the next navigation can be determined. */
    fun onLoginComplete() {
        _startDestination.value = resolveStartDestination()
    }

    /**
     * Called after DownloadWorker completes successfully.
     * Kicks off engine initialization on a background thread before navigating to Home.
     *
     * @param modelPath absolute path to the downloaded .litertlm file
     */
    fun onModelDownloaded(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            inferenceEngine.initialize(modelPath)
        }
        _startDestination.value = StartDestination.Home
    }

    /** Called after account deletion completes — user must re-authenticate. */
    fun onAccountDeleted() {
        _startDestination.value = StartDestination.Onboarding
    }
}
