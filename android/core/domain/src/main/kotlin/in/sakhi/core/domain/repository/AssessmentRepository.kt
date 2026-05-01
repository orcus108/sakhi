package `in`.sakhi.core.domain.repository

import `in`.sakhi.core.domain.model.AssessmentResult
import kotlinx.coroutines.flow.Flow

interface AssessmentRepository {
    fun observeAssessmentsForPatient(patientId: String): Flow<List<AssessmentResult>>
    suspend fun getAssessment(id: String): AssessmentResult?
    suspend fun getLatestAssessmentForCheckup(checkupId: String): AssessmentResult?
    suspend fun upsertAssessment(assessment: AssessmentResult)
    suspend fun getDirtyAssessments(): List<AssessmentResult>
    suspend fun deleteAllForWorker(ashaWorkerId: String)
}
