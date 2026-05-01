package `in`.sakhi.core.data.repository

import `in`.sakhi.core.data.db.dao.AssessmentDao
import `in`.sakhi.core.data.db.entity.toDomain
import `in`.sakhi.core.data.db.entity.toEntity
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.repository.AssessmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssessmentRepositoryImpl @Inject constructor(
    private val dao: AssessmentDao
) : AssessmentRepository {

    override fun observeAssessmentsForPatient(patientId: String): Flow<List<AssessmentResult>> =
        dao.observeForPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAssessment(id: String): AssessmentResult? =
        dao.getById(id)?.toDomain()

    override suspend fun getLatestAssessmentForCheckup(checkupId: String): AssessmentResult? =
        dao.getLatestForCheckup(checkupId)?.toDomain()

    override suspend fun upsertAssessment(assessment: AssessmentResult) =
        dao.upsert(assessment.toEntity())

    override suspend fun getDirtyAssessments(): List<AssessmentResult> =
        dao.getDirty().map { it.toDomain() }

    override suspend fun deleteAllForWorker(ashaWorkerId: String) =
        dao.deleteAllForWorker(ashaWorkerId)
}
