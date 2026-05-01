package `in`.sakhi.core.domain.repository

import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import kotlinx.coroutines.flow.Flow

interface PatientRepository {

    // ANC patients
    fun observeAncPatients(ashaWorkerId: String): Flow<List<AncPatient>>
    suspend fun getAncPatient(id: String): AncPatient?
    suspend fun upsertAncPatient(patient: AncPatient)
    suspend fun deleteAncPatient(id: String)

    // ANC checkups
    fun observeAncCheckups(patientId: String): Flow<List<AncCheckup>>
    suspend fun getAncCheckup(id: String): AncCheckup?
    suspend fun upsertAncCheckup(checkup: AncCheckup)

    // Newborn patients
    fun observeNewbornPatients(ashaWorkerId: String): Flow<List<NewbornPatient>>
    suspend fun getNewbornPatient(id: String): NewbornPatient?
    suspend fun upsertNewbornPatient(patient: NewbornPatient)
    suspend fun deleteNewbornPatient(id: String)

    // Newborn visits
    fun observeNewbornVisits(patientId: String): Flow<List<NewbornVisit>>
    suspend fun getNewbornVisit(id: String): NewbornVisit?
    suspend fun upsertNewbornVisit(visit: NewbornVisit)

    // Dirty record queries (for sync)
    suspend fun getDirtyAncPatients(): List<AncPatient>
    suspend fun getDirtyNewbornPatients(): List<NewbornPatient>
    suspend fun getDirtyAncCheckups(): List<AncCheckup>
    suspend fun getDirtyNewbornVisits(): List<NewbornVisit>

    // Bulk delete (DISHA data deletion flow)
    suspend fun deleteAllForWorker(ashaWorkerId: String)
}
