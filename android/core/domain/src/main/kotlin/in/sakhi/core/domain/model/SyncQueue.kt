package `in`.sakhi.core.domain.model

data class SyncQueueEntry(
    val id: String,
    val entityType: String,    // "anc_patient" | "newborn_patient" | "anc_checkup" | "newborn_visit" | "assessment"
    val entityId: String,
    val operation: String,     // "upsert" | "delete"
    val payload: String,       // JSON snapshot of the entity at enqueue time
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AuditEntry(
    val id: String,
    val ashaWorkerId: String,
    val action: String,        // "view_patient" | "run_assessment" | "export" | "delete"
    val entityType: String? = null,
    val entityId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
