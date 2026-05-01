package `in`.sakhi.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "anc_patients")
data class AncPatientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "asha_worker_id") val ashaWorkerId: String,
    @ColumnInfo(name = "abdm_id") val abdmId: String? = null,
    val name: String,
    @ColumnInfo(name = "name_hi") val nameHi: String = "",
    val age: Int,
    val village: String,
    @ColumnInfo(name = "village_hi") val villageHi: String = "",
    val lmp: String? = null,
    @ColumnInfo(name = "gestational_weeks") val gestationalWeeks: Int? = null,
    val gravida: Int? = null,
    val para: Int? = null,
    @ColumnInfo(name = "risk_level") val riskLevel: String = "green",
    val phone: String = "",
    val dirty: Int = 1,                          // 1 = needs sync
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null
)

@Entity(tableName = "newborn_patients")
data class NewbornPatientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "asha_worker_id") val ashaWorkerId: String,
    @ColumnInfo(name = "abdm_id") val abdmId: String? = null,
    val name: String,
    @ColumnInfo(name = "name_hi") val nameHi: String = "",
    val gender: String = "",
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String,
    val village: String,
    @ColumnInfo(name = "village_hi") val villageHi: String = "",
    @ColumnInfo(name = "mother_id") val motherId: String? = null,
    @ColumnInfo(name = "mother_name") val motherName: String = "",
    @ColumnInfo(name = "mother_name_hi") val motherNameHi: String = "",
    @ColumnInfo(name = "birth_weight_kg") val birthWeightKg: Double,
    @ColumnInfo(name = "current_weight_kg") val currentWeightKg: Double? = null,
    @ColumnInfo(name = "risk_level") val riskLevel: String = "green",
    val dirty: Int = 1,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "anc_checkups",
    foreignKeys = [ForeignKey(
        entity = AncPatientEntity::class,
        parentColumns = ["id"],
        childColumns = ["patient_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("patient_id")]
)
data class AncCheckupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    val date: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "fundal_height_cm") val fundalHeightCm: Double,
    @ColumnInfo(name = "bp_systolic") val bpSystolic: Int,
    @ColumnInfo(name = "bp_diastolic") val bpDiastolic: Int,
    @ColumnInfo(name = "fetal_heart_rate") val fetalHeartRate: Int? = null,
    val hemoglobin: Double? = null,
    val symptoms: String = "[]",             // JSON array
    @ColumnInfo(name = "risk_level") val riskLevel: String = "green",
    val notes: String = "",
    val dirty: Int = 1,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "newborn_visits",
    foreignKeys = [ForeignKey(
        entity = NewbornPatientEntity::class,
        parentColumns = ["id"],
        childColumns = ["patient_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("patient_id")]
)
data class NewbornVisitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    val date: String,
    @ColumnInfo(name = "visit_day") val visitDay: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val observations: String = "[]",         // JSON array
    @ColumnInfo(name = "other_observations") val otherObservations: String = "",
    val notes: String = "",
    @ColumnInfo(name = "risk_level") val riskLevel: String = "green",
    val dirty: Int = 1,
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null
)

@Entity(tableName = "assessments")
data class AssessmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "checkup_id") val checkupId: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    @ColumnInfo(name = "patient_type") val patientType: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    @ColumnInfo(name = "risk_reason") val riskReason: String,
    @ColumnInfo(name = "what_sakhi_noticed") val whatSakhiNoticed: String,  // JSON array
    @ColumnInfo(name = "what_to_tell_patient") val whatToTellPatient: String,
    @ColumnInfo(name = "what_to_do_next") val whatToDoNext: String,
    @ColumnInfo(name = "follow_up_date") val followUpDate: String? = null,
    @ColumnInfo(name = "is_offline") val isOffline: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val dirty: Int = 1,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null
)

/** Chat messages are session-local — not synced to Supabase. */
@Entity(tableName = "chat_messages", indices = [Index("session_id")])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "patient_id") val patientId: String? = null,
    @ColumnInfo(name = "patient_type") val patientType: String? = null,
    val role: String,                        // "user" | "assistant"
    val content: String,
    val refer: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    val payload: String,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "asha_worker_id") val ashaWorkerId: String,
    val action: String,
    @ColumnInfo(name = "entity_type") val entityType: String? = null,
    @ColumnInfo(name = "entity_id") val entityId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val synced: Int = 0
)

/** Worker profile — name, phone, consent timestamp. Not a patient. */
@Entity(tableName = "worker_profile")
data class WorkerProfileEntity(
    @PrimaryKey val id: String,           // Supabase user UUID
    val name: String,
    val phone: String,
    val language: String = "en",
    @ColumnInfo(name = "consent_at") val consentAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
