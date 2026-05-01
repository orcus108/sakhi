package `in`.sakhi.core.data.repository

import `in`.sakhi.core.data.db.dao.AncCheckupDao
import `in`.sakhi.core.data.db.dao.AncPatientDao
import `in`.sakhi.core.data.db.dao.NewbornPatientDao
import `in`.sakhi.core.data.db.dao.NewbornVisitDao
import `in`.sakhi.core.data.db.entity.toDomain
import `in`.sakhi.core.data.db.entity.toEntity
import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val ancPatientDao: AncPatientDao,
    private val newbornPatientDao: NewbornPatientDao,
    private val ancCheckupDao: AncCheckupDao,
    private val newbornVisitDao: NewbornVisitDao
) : PatientRepository {

    // ── ANC patients ──────────────────────────────────────────────────────────────

    override fun observeAncPatients(ashaWorkerId: String): Flow<List<AncPatient>> =
        // Load only the last checkup per patient (needed for due-date math in HomeViewModel)
        ancPatientDao.observeAll(ashaWorkerId).map { entities ->
            entities.map { entity ->
                val lastCheckup = ancCheckupDao.getLastCheckupForPatient(entity.id)
                entity.toDomain(
                    checkupHistory = listOfNotNull(lastCheckup?.toDomain())
                )
            }
        }

    override suspend fun getAncPatient(id: String): AncPatient? {
        val entity = ancPatientDao.getById(id) ?: return null
        val checkups = ancCheckupDao.observeForPatient(id).first().map { it.toDomain() }
        return entity.toDomain(checkupHistory = checkups)
    }

    override suspend fun upsertAncPatient(patient: AncPatient) =
        ancPatientDao.upsert(patient.toEntity())

    override suspend fun deleteAncPatient(id: String) =
        ancPatientDao.deleteById(id)

    override fun observeAncCheckups(patientId: String): Flow<List<AncCheckup>> =
        ancCheckupDao.observeForPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAncCheckup(id: String): AncCheckup? =
        ancCheckupDao.getById(id)?.toDomain()

    override suspend fun upsertAncCheckup(checkup: AncCheckup) =
        ancCheckupDao.upsert(checkup.toEntity())

    override suspend fun getDirtyAncPatients(): List<AncPatient> =
        ancPatientDao.getDirty().map { it.toDomain() }

    override suspend fun getDirtyAncCheckups(): List<AncCheckup> =
        ancCheckupDao.getDirty().map { it.toDomain() }

    // ── Newborn patients ──────────────────────────────────────────────────────────

    override fun observeNewbornPatients(ashaWorkerId: String): Flow<List<NewbornPatient>> =
        newbornPatientDao.observeAll(ashaWorkerId).map { entities ->
            entities.map { entity ->
                val lastVisit = newbornVisitDao.getLastVisitForPatient(entity.id)
                entity.toDomain(
                    visitHistory = listOfNotNull(lastVisit?.toDomain())
                )
            }
        }

    override suspend fun getNewbornPatient(id: String): NewbornPatient? {
        val entity = newbornPatientDao.getById(id) ?: return null
        val visits = newbornVisitDao.observeForPatient(id).first().map { it.toDomain() }
        return entity.toDomain(visitHistory = visits)
    }

    override suspend fun upsertNewbornPatient(patient: NewbornPatient) =
        newbornPatientDao.upsert(patient.toEntity())

    override suspend fun deleteNewbornPatient(id: String) =
        newbornPatientDao.deleteById(id)

    override fun observeNewbornVisits(patientId: String): Flow<List<NewbornVisit>> =
        newbornVisitDao.observeForPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun getNewbornVisit(id: String): NewbornVisit? =
        newbornVisitDao.getById(id)?.toDomain()

    override suspend fun upsertNewbornVisit(visit: NewbornVisit) =
        newbornVisitDao.upsert(visit.toEntity())

    override suspend fun getDirtyNewbornPatients(): List<NewbornPatient> =
        newbornPatientDao.getDirty().map { it.toDomain() }

    override suspend fun getDirtyNewbornVisits(): List<NewbornVisit> =
        newbornVisitDao.getDirty().map { it.toDomain() }

    override suspend fun deleteAllForWorker(ashaWorkerId: String) {
        ancPatientDao.deleteAllForWorker(ashaWorkerId)
        newbornPatientDao.deleteAllForWorker(ashaWorkerId)
    }
}
