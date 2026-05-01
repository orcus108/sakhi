package `in`.sakhi.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import `in`.sakhi.core.data.db.entity.AncCheckupEntity
import `in`.sakhi.core.data.db.entity.AncPatientEntity
import `in`.sakhi.core.data.db.entity.AssessmentEntity
import `in`.sakhi.core.data.db.entity.AuditLogEntity
import `in`.sakhi.core.data.db.entity.ChatMessageEntity
import `in`.sakhi.core.data.db.entity.NewbornPatientEntity
import `in`.sakhi.core.data.db.entity.NewbornVisitEntity
import `in`.sakhi.core.data.db.entity.SyncQueueEntity
import `in`.sakhi.core.data.db.entity.WorkerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AncPatientDao {
    @Query("SELECT * FROM anc_patients WHERE asha_worker_id = :ashaWorkerId ORDER BY risk_level DESC, name ASC")
    fun observeAll(ashaWorkerId: String): Flow<List<AncPatientEntity>>

    @Query("SELECT * FROM anc_patients WHERE id = :id")
    suspend fun getById(id: String): AncPatientEntity?

    @Upsert
    suspend fun upsert(patient: AncPatientEntity)

    @Query("DELETE FROM anc_patients WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM anc_patients WHERE dirty = 1")
    suspend fun getDirty(): List<AncPatientEntity>

    @Query("DELETE FROM anc_patients WHERE asha_worker_id = :ashaWorkerId")
    suspend fun deleteAllForWorker(ashaWorkerId: String)

    @Query("UPDATE anc_patients SET dirty = 0, last_synced_at = :syncedAt, server_id = :serverId WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, syncedAt: Long)
}

@Dao
interface NewbornPatientDao {
    @Query("SELECT * FROM newborn_patients WHERE asha_worker_id = :ashaWorkerId ORDER BY risk_level DESC, name ASC")
    fun observeAll(ashaWorkerId: String): Flow<List<NewbornPatientEntity>>

    @Query("SELECT * FROM newborn_patients WHERE id = :id")
    suspend fun getById(id: String): NewbornPatientEntity?

    @Upsert
    suspend fun upsert(patient: NewbornPatientEntity)

    @Query("DELETE FROM newborn_patients WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM newborn_patients WHERE dirty = 1")
    suspend fun getDirty(): List<NewbornPatientEntity>

    @Query("DELETE FROM newborn_patients WHERE asha_worker_id = :ashaWorkerId")
    suspend fun deleteAllForWorker(ashaWorkerId: String)

    @Query("UPDATE newborn_patients SET dirty = 0, last_synced_at = :syncedAt, server_id = :serverId WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, syncedAt: Long)
}

@Dao
interface AncCheckupDao {
    @Query("SELECT * FROM anc_checkups WHERE patient_id = :patientId ORDER BY date DESC")
    fun observeForPatient(patientId: String): Flow<List<AncCheckupEntity>>

    @Query("SELECT * FROM anc_checkups WHERE id = :id")
    suspend fun getById(id: String): AncCheckupEntity?

    @Upsert
    suspend fun upsert(checkup: AncCheckupEntity)

    @Query("SELECT * FROM anc_checkups WHERE dirty = 1")
    suspend fun getDirty(): List<AncCheckupEntity>

    @Query("UPDATE anc_checkups SET dirty = 0, last_synced_at = :syncedAt, server_id = :serverId WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, syncedAt: Long)

    @Query("SELECT * FROM anc_checkups WHERE patient_id = :patientId ORDER BY date DESC LIMIT 1")
    suspend fun getLastCheckupForPatient(patientId: String): AncCheckupEntity?
}

@Dao
interface NewbornVisitDao {
    @Query("SELECT * FROM newborn_visits WHERE patient_id = :patientId ORDER BY date DESC")
    fun observeForPatient(patientId: String): Flow<List<NewbornVisitEntity>>

    @Query("SELECT * FROM newborn_visits WHERE id = :id")
    suspend fun getById(id: String): NewbornVisitEntity?

    @Upsert
    suspend fun upsert(visit: NewbornVisitEntity)

    @Query("SELECT * FROM newborn_visits WHERE dirty = 1")
    suspend fun getDirty(): List<NewbornVisitEntity>

    @Query("UPDATE newborn_visits SET dirty = 0, last_synced_at = :syncedAt, server_id = :serverId WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, syncedAt: Long)

    @Query("SELECT * FROM newborn_visits WHERE patient_id = :patientId ORDER BY date DESC LIMIT 1")
    suspend fun getLastVisitForPatient(patientId: String): NewbornVisitEntity?
}

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM assessments WHERE patient_id = :patientId ORDER BY created_at DESC")
    fun observeForPatient(patientId: String): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE id = :id")
    suspend fun getById(id: String): AssessmentEntity?

    @Query("SELECT * FROM assessments WHERE checkup_id = :checkupId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestForCheckup(checkupId: String): AssessmentEntity?

    @Upsert
    suspend fun upsert(assessment: AssessmentEntity)

    @Query("SELECT * FROM assessments WHERE dirty = 1")
    suspend fun getDirty(): List<AssessmentEntity>

    @Query("UPDATE assessments SET dirty = 0, last_synced_at = :syncedAt, server_id = :serverId WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, syncedAt: Long)

    @Query("DELETE FROM assessments WHERE patient_id IN (SELECT id FROM anc_patients WHERE asha_worker_id = :ashaWorkerId) OR patient_id IN (SELECT id FROM newborn_patients WHERE asha_worker_id = :ashaWorkerId)")
    suspend fun deleteAllForWorker(ashaWorkerId: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun observeSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun clearSession(sessionId: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun remove(id: String)

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1, last_attempt_at = :now WHERE id = :id")
    suspend fun incrementRetry(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE synced = 0 ORDER BY created_at ASC LIMIT 100")
    suspend fun getUnsynced(): List<AuditLogEntity>

    @Query("UPDATE audit_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM audit_log WHERE asha_worker_id = :ashaWorkerId")
    suspend fun deleteAllForWorker(ashaWorkerId: String)
}

@Dao
interface WorkerProfileDao {
    @Query("SELECT * FROM worker_profile LIMIT 1")
    suspend fun get(): WorkerProfileEntity?

    @Upsert
    suspend fun upsert(profile: WorkerProfileEntity)

    @Query("DELETE FROM worker_profile")
    suspend fun clear()
}
