package `in`.sakhi.feature.checkup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.model.VisitDay
import `in`.sakhi.core.domain.repository.AssessmentRepository
import `in`.sakhi.core.domain.repository.InferenceEngine
import `in`.sakhi.core.domain.repository.PatientRepository
import `in`.sakhi.core.domain.usecase.LocalAssessmentEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

sealed interface CheckupUiState {
    data object Idle : CheckupUiState
    data object Loading : CheckupUiState
    data class Success(val assessmentId: String) : CheckupUiState
    data class Error(val message: String) : CheckupUiState
}

@HiltViewModel
class CheckupViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val assessmentRepository: AssessmentRepository,
    private val inferenceEngine: InferenceEngine,
    private val authPrefs: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<CheckupUiState>(CheckupUiState.Idle)
    val uiState: StateFlow<CheckupUiState> = _uiState.asStateFlow()

    private val _ancPatient = MutableStateFlow<AncPatient?>(null)
    val ancPatient: StateFlow<AncPatient?> = _ancPatient.asStateFlow()

    private val _newbornPatient = MutableStateFlow<NewbornPatient?>(null)
    val newbornPatient: StateFlow<NewbornPatient?> = _newbornPatient.asStateFlow()

    val language: String get() = authPrefs.getLanguage()

    fun loadAncPatient(patientId: String) {
        viewModelScope.launch { _ancPatient.value = patientRepository.getAncPatient(patientId) }
    }

    fun loadNewbornPatient(patientId: String) {
        viewModelScope.launch { _newbornPatient.value = patientRepository.getNewbornPatient(patientId) }
    }

    fun resetState() { _uiState.value = CheckupUiState.Idle }

    /**
     * Submit an ANC checkup.
     * Tries the InferenceEngine first; falls back to LocalAssessmentEngine if not ready.
     * Persists the checkup and assessment to Room (both dirty=1 for sync).
     */
    fun submitAncCheckup(patient: AncPatient, checkup: AncCheckup) {
        _uiState.value = CheckupUiState.Loading
        viewModelScope.launch {
            try {
                // Persist checkup first so it's saved regardless of assessment outcome
                patientRepository.upsertAncCheckup(checkup)

                val assessment = if (inferenceEngine.isReady()) {
                    inferenceEngine.generateCheckupAssessment(patient, checkup, language)
                } else {
                    LocalAssessmentEngine.assess(patient, checkup)
                }

                // Update patient risk level from assessment
                patientRepository.upsertAncPatient(
                    patient.copy(riskLevel = assessment.riskLevel, dirty = true)
                )
                assessmentRepository.upsertAssessment(assessment)
                _uiState.value = CheckupUiState.Success(assessment.id)
            } catch (e: Exception) {
                _uiState.value = CheckupUiState.Error(e.message ?: "Assessment failed")
            }
        }
    }

    /**
     * Submit a newborn visit.
     * Same fallback logic as ANC.
     */
    fun submitNewbornVisit(patient: NewbornPatient, visit: NewbornVisit) {
        _uiState.value = CheckupUiState.Loading
        viewModelScope.launch {
            try {
                patientRepository.upsertNewbornVisit(visit)

                val assessment = if (inferenceEngine.isReady()) {
                    inferenceEngine.generateCheckupAssessment(patient, visit, language)
                } else {
                    LocalAssessmentEngine.assess(patient, visit)
                }

                patientRepository.upsertNewbornPatient(
                    patient.copy(
                        riskLevel = assessment.riskLevel,
                        currentWeightKg = visit.weightKg,
                        dirty = true
                    )
                )
                assessmentRepository.upsertAssessment(assessment)
                _uiState.value = CheckupUiState.Success(assessment.id)
            } catch (e: Exception) {
                _uiState.value = CheckupUiState.Error(e.message ?: "Assessment failed")
            }
        }
    }

    /** True when the on-device model is loaded and ready for inference. */
    fun isModelReady(): Boolean = inferenceEngine.isReady()

    companion object {
        /** Build AncCheckup from form field values. Validates required fields. */
        fun buildAncCheckup(
            patientId: String,
            bpSystolic: String,
            bpDiastolic: String,
            weightKg: String,
            fundalHeightCm: String,
            fetalHeartRate: String,
            hemoglobin: String,
            symptoms: List<String>
        ): AncCheckup {
            return AncCheckup(
                id = UUID.randomUUID().toString(),
                patientId = patientId,
                date = LocalDate.now().toString(),
                bpSystolic = bpSystolic.trim().toInt(),
                bpDiastolic = bpDiastolic.trim().toInt(),
                weightKg = weightKg.trim().toDouble(),
                fundalHeightCm = fundalHeightCm.trim().toDouble(),
                fetalHeartRate = fetalHeartRate.trim().takeIf { it.isNotEmpty() }?.toInt(),
                hemoglobin = hemoglobin.trim().takeIf { it.isNotEmpty() }?.toDouble(),
                symptoms = symptoms,
                riskLevel = RiskLevel.GREEN,
                dirty = true
            )
        }

        /** Build NewbornVisit from form field values. */
        fun buildNewbornVisit(
            patientId: String,
            visitDay: VisitDay,
            weightKg: String,
            observations: List<String>,
            otherObservations: String
        ): NewbornVisit {
            return NewbornVisit(
                id = UUID.randomUUID().toString(),
                patientId = patientId,
                date = LocalDate.now().toString(),
                visitDay = visitDay,
                weightKg = weightKg.trim().toDouble(),
                observations = observations,
                otherObservations = otherObservations.trim(),
                riskLevel = RiskLevel.GREEN,
                dirty = true
            )
        }
    }
}
