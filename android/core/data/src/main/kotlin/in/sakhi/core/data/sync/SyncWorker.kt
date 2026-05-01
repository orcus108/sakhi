package `in`.sakhi.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.data.db.dao.AncPatientDao
import `in`.sakhi.core.data.db.dao.NewbornPatientDao
import `in`.sakhi.core.data.db.dao.AncCheckupDao
import `in`.sakhi.core.data.db.dao.NewbornVisitDao
import `in`.sakhi.core.data.db.dao.AssessmentDao
import `in`.sakhi.core.data.db.entity.AncCheckupEntity
import `in`.sakhi.core.data.db.entity.AncPatientEntity
import `in`.sakhi.core.data.db.entity.AssessmentEntity
import `in`.sakhi.core.data.db.entity.NewbornPatientEntity
import `in`.sakhi.core.data.db.entity.NewbornVisitEntity
import `in`.sakhi.core.network.SupabaseSyncApi
import `in`.sakhi.core.network.SupabaseSyncApi.AncCheckupDto
import `in`.sakhi.core.network.SupabaseSyncApi.AncPatientDto
import `in`.sakhi.core.network.SupabaseSyncApi.AssessmentDto
import `in`.sakhi.core.network.SupabaseSyncApi.NewbornPatientDto
import `in`.sakhi.core.network.SupabaseSyncApi.NewbornVisitDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

/**
 * Opportunistic background sync worker.
 *
 * Sync strategy: last-write-wins on last_modified_at, server assigns server_id.
 * Scheduled every hour when network is connected. Exponential backoff on failure.
 *
 * Push flow:
 *   1. Read dirty=1 records from Room DAOs
 *   2. Map to SupabaseSyncApi DTOs
 *   3. Upsert to Supabase with ON CONFLICT (local_id) DO UPDATE
 *   4. If server's last_modified_at > local → server won; skip (pull cycle handles it)
 *   5. On success → markSynced(id, serverId, serverLastModifiedAt)
 *
 * Pull flow runs after a successful push to pick up any server-side changes
 * made by the same worker on another device.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authPrefs: AuthPreferences,
    private val ancPatientDao: AncPatientDao,
    private val newbornPatientDao: NewbornPatientDao,
    private val ancCheckupDao: AncCheckupDao,
    private val newbornVisitDao: NewbornVisitDao,
    private val assessmentDao: AssessmentDao,
    private val syncApi: SupabaseSyncApi,
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "SyncWorker"
        private const val UNIQUE_WORK_NAME = "sakhi_sync"
        private const val SYNC_INTERVAL_HOURS = 1L
        private const val MAX_RETRIES = 10

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val userId = authPrefs.getUserId() ?: return Result.success()

        if (runAttemptCount >= MAX_RETRIES) {
            // Exceeded max retries — give up for this cycle, wait for next periodic run
            return Result.success()
        }

        return try {
            push(userId)
            // Pull after push to reconcile any server-side wins
            pull(userId)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed (attempt ${runAttemptCount + 1}): ${e.message}")
            Result.retry()
        }
    }

    // ── Push ────────────────────────────────────────────────────────────────────

    private suspend fun push(userId: String) {
        pushAncPatients(userId)
        pushNewbornPatients(userId)
        pushAncCheckups()
        pushNewbornVisits()
        pushAssessments()
    }

    private suspend fun pushAncPatients(userId: String) {
        val dirty = ancPatientDao.getDirty()
        if (dirty.isEmpty()) return

        val dtos = dirty.map { e ->
            AncPatientDto(
                localId = e.id,
                serverId = e.serverId,
                ashaWorkerId = userId,
                name = e.name,
                nameHi = e.nameHi,
                age = e.age,
                village = e.village,
                villageHi = e.villageHi,
                lmp = e.lmp,
                gestationalWeeks = e.gestationalWeeks,
                gravida = e.gravida,
                para = e.para,
                riskLevel = e.riskLevel,
                phone = e.phone,
                abdmId = e.abdmId,
                lastModifiedAt = e.lastModifiedAt,
            )
        }

        val results = syncApi.pushAncPatients(dtos)
        results.forEach { result ->
            ancPatientDao.markSynced(result.localId, result.serverId, result.lastModifiedAt)
        }
        Log.d(TAG, "Pushed ${results.size} ANC patients")
    }

    private suspend fun pushNewbornPatients(userId: String) {
        val dirty = newbornPatientDao.getDirty()
        if (dirty.isEmpty()) return

        val dtos = dirty.map { e ->
            NewbornPatientDto(
                localId = e.id,
                serverId = e.serverId,
                ashaWorkerId = userId,
                name = e.name,
                nameHi = e.nameHi,
                gender = e.gender,
                dateOfBirth = e.dateOfBirth,
                motherId = e.motherId,
                motherName = e.motherName,
                motherNameHi = e.motherNameHi,
                village = e.village,
                villageHi = e.villageHi,
                birthWeightKg = e.birthWeightKg,
                currentWeightKg = e.currentWeightKg,
                riskLevel = e.riskLevel,
                lastModifiedAt = e.lastModifiedAt,
            )
        }

        val results = syncApi.pushNewbornPatients(dtos)
        results.forEach { result ->
            newbornPatientDao.markSynced(result.localId, result.serverId, result.lastModifiedAt)
        }
        Log.d(TAG, "Pushed ${results.size} newborn patients")
    }

    private suspend fun pushAncCheckups() {
        val dirty = ancCheckupDao.getDirty()
        if (dirty.isEmpty()) return

        val dtos = dirty.map { e ->
            AncCheckupDto(
                localId = e.id,
                serverId = e.serverId,
                patientLocalId = e.patientId,
                date = e.date,
                weightKg = e.weightKg,
                fundalHeightCm = e.fundalHeightCm,
                bpSystolic = e.bpSystolic,
                bpDiastolic = e.bpDiastolic,
                fetalHeartRate = e.fetalHeartRate,
                hemoglobin = e.hemoglobin,
                symptoms = json.decodeFromString(e.symptoms),
                riskLevel = e.riskLevel,
                notes = e.notes,
                lastModifiedAt = e.lastModifiedAt,
            )
        }

        val results = syncApi.pushAncCheckups(dtos)
        results.forEach { result ->
            ancCheckupDao.markSynced(result.localId, result.serverId, result.lastModifiedAt)
        }
        Log.d(TAG, "Pushed ${results.size} ANC checkups")
    }

    private suspend fun pushNewbornVisits() {
        val dirty = newbornVisitDao.getDirty()
        if (dirty.isEmpty()) return

        val dtos = dirty.map { e ->
            NewbornVisitDto(
                localId = e.id,
                serverId = e.serverId,
                patientLocalId = e.patientId,
                date = e.date,
                visitDay = e.visitDay,
                weightKg = e.weightKg,
                observations = json.decodeFromString(e.observations),
                otherObservations = e.otherObservations,
                riskLevel = e.riskLevel,
                notes = e.notes,
                lastModifiedAt = e.lastModifiedAt,
            )
        }

        val results = syncApi.pushNewbornVisits(dtos)
        results.forEach { result ->
            newbornVisitDao.markSynced(result.localId, result.serverId, result.lastModifiedAt)
        }
        Log.d(TAG, "Pushed ${results.size} newborn visits")
    }

    private suspend fun pushAssessments() {
        val dirty = assessmentDao.getDirty()
        if (dirty.isEmpty()) return

        val dtos = dirty.map { e ->
            AssessmentDto(
                localId = e.id,
                serverId = e.serverId,
                checkupLocalId = e.checkupId,
                patientLocalId = e.patientId,
                patientType = e.patientType,
                riskLevel = e.riskLevel,
                riskReason = e.riskReason,
                whatSakhiNoticed = json.decodeFromString(e.whatSakhiNoticed),
                whatToTellPatient = e.whatToTellPatient,
                whatToDoNext = e.whatToDoNext,
                followUpDate = e.followUpDate,
                isOffline = e.isOffline == 1,
                createdAt = e.createdAt,
                lastModifiedAt = e.createdAt,   // assessments are immutable; created_at = last_modified
            )
        }

        val results = syncApi.pushAssessments(dtos)
        results.forEach { result ->
            assessmentDao.markSynced(result.localId, result.serverId, result.lastModifiedAt)
        }
        Log.d(TAG, "Pushed ${results.size} assessments")
    }

    // ── Pull ────────────────────────────────────────────────────────────────────

    /**
     * Pull changes from Supabase and merge into Room.
     *
     * Merge rule: **last-write-wins on [last_modified_at].**
     * - Local record exists and local.lastModifiedAt >= server.lastModifiedAt → keep local, skip.
     * - Local record exists and local is older → overwrite from server, dirty=0.
     * - No local record → insert from server, dirty=0. This handles multi-device scenarios.
     *
     * Order matters: patients first so FK constraints are satisfied when inserting
     * checkups/visits/assessments that reference them.
     */
    private suspend fun pull(userId: String) {
        val lastSyncAt = authPrefs.getLastSyncAt()
        val changes = syncApi.pullChanges(userId, lastSyncAt)

        val now = System.currentTimeMillis()

        // 1. ANC patients
        var merged = 0
        for (dto in changes.ancPatients) {
            val local = ancPatientDao.getById(dto.localId)
            if (local != null && local.lastModifiedAt >= dto.lastModifiedAt) continue
            ancPatientDao.upsert(
                AncPatientEntity(
                    id = dto.localId,
                    serverId = dto.serverId,
                    ashaWorkerId = dto.ashaWorkerId,
                    name = dto.name,
                    nameHi = dto.nameHi ?: "",
                    age = dto.age,
                    village = dto.village,
                    villageHi = dto.villageHi ?: "",
                    lmp = dto.lmp,
                    gestationalWeeks = dto.gestationalWeeks,
                    gravida = dto.gravida,
                    para = dto.para,
                    riskLevel = dto.riskLevel,
                    phone = dto.phone ?: "",
                    abdmId = dto.abdmId,
                    dirty = 0,
                    lastModifiedAt = dto.lastModifiedAt,
                    lastSyncedAt = now,
                )
            )
            merged++
        }
        if (merged > 0) Log.d(TAG, "Pull: merged $merged ANC patients")

        // 2. Newborn patients
        merged = 0
        for (dto in changes.newbornPatients) {
            val local = newbornPatientDao.getById(dto.localId)
            if (local != null && local.lastModifiedAt >= dto.lastModifiedAt) continue
            newbornPatientDao.upsert(
                NewbornPatientEntity(
                    id = dto.localId,
                    serverId = dto.serverId,
                    ashaWorkerId = dto.ashaWorkerId,
                    name = dto.name,
                    nameHi = dto.nameHi ?: "",
                    gender = dto.gender,
                    dateOfBirth = dto.dateOfBirth,
                    motherId = dto.motherId,
                    motherName = dto.motherName,
                    motherNameHi = dto.motherNameHi ?: "",
                    village = dto.village,
                    villageHi = dto.villageHi ?: "",
                    birthWeightKg = dto.birthWeightKg,
                    currentWeightKg = dto.currentWeightKg,
                    riskLevel = dto.riskLevel,
                    dirty = 0,
                    lastModifiedAt = dto.lastModifiedAt,
                    lastSyncedAt = now,
                )
            )
            merged++
        }
        if (merged > 0) Log.d(TAG, "Pull: merged $merged newborn patients")

        // 3. ANC checkups (patients must exist first — handled by step 1)
        merged = 0
        for (dto in changes.ancCheckups) {
            val local = ancCheckupDao.getById(dto.localId)
            if (local != null && local.lastModifiedAt >= dto.lastModifiedAt) continue
            // Skip if parent patient doesn't exist locally (FK constraint) — will land next pull
            ancPatientDao.getById(dto.patientLocalId) ?: continue
            ancCheckupDao.upsert(
                AncCheckupEntity(
                    id = dto.localId,
                    serverId = dto.serverId,
                    patientId = dto.patientLocalId,
                    date = dto.date,
                    weightKg = dto.weightKg,
                    fundalHeightCm = dto.fundalHeightCm,
                    bpSystolic = dto.bpSystolic,
                    bpDiastolic = dto.bpDiastolic,
                    fetalHeartRate = dto.fetalHeartRate,
                    hemoglobin = dto.hemoglobin,
                    symptoms = json.encodeToString(dto.symptoms),
                    riskLevel = dto.riskLevel,
                    notes = dto.notes ?: "",
                    dirty = 0,
                    lastModifiedAt = dto.lastModifiedAt,
                    lastSyncedAt = now,
                )
            )
            merged++
        }
        if (merged > 0) Log.d(TAG, "Pull: merged $merged ANC checkups")

        // 4. Newborn visits
        merged = 0
        for (dto in changes.newbornVisits) {
            val local = newbornVisitDao.getById(dto.localId)
            if (local != null && local.lastModifiedAt >= dto.lastModifiedAt) continue
            newbornPatientDao.getById(dto.patientLocalId) ?: continue
            newbornVisitDao.upsert(
                NewbornVisitEntity(
                    id = dto.localId,
                    serverId = dto.serverId,
                    patientId = dto.patientLocalId,
                    date = dto.date,
                    visitDay = dto.visitDay,
                    weightKg = dto.weightKg,
                    observations = json.encodeToString(dto.observations),
                    otherObservations = dto.otherObservations ?: "",
                    riskLevel = dto.riskLevel,
                    notes = dto.notes ?: "",
                    dirty = 0,
                    lastModifiedAt = dto.lastModifiedAt,
                    lastSyncedAt = now,
                )
            )
            merged++
        }
        if (merged > 0) Log.d(TAG, "Pull: merged $merged newborn visits")

        // 5. Assessments (immutable — skip if already exists regardless of timestamp)
        merged = 0
        for (dto in changes.assessments) {
            if (assessmentDao.getById(dto.localId) != null) continue
            assessmentDao.upsert(
                AssessmentEntity(
                    id = dto.localId,
                    serverId = dto.serverId,
                    checkupId = dto.checkupLocalId,
                    patientId = dto.patientLocalId,
                    patientType = dto.patientType,
                    riskLevel = dto.riskLevel,
                    riskReason = dto.riskReason,
                    whatSakhiNoticed = json.encodeToString(dto.whatSakhiNoticed),
                    whatToTellPatient = dto.whatToTellPatient,
                    whatToDoNext = dto.whatToDoNext,
                    followUpDate = dto.followUpDate,
                    isOffline = if (dto.isOffline) 1 else 0,
                    createdAt = dto.createdAt,
                    dirty = 0,
                    lastSyncedAt = now,
                )
            )
            merged++
        }
        if (merged > 0) Log.d(TAG, "Pull: merged $merged assessments")

        // Persist the sync timestamp so the next pull only fetches deltas
        authPrefs.setLastSyncAt(now)
    }
}
