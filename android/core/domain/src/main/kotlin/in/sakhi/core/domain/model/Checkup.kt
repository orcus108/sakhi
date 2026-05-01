package `in`.sakhi.core.domain.model

import kotlinx.serialization.Serializable

/** Sealed type so use cases can accept ANC or newborn checkup uniformly. */
sealed class Checkup {
    abstract val id: String
    abstract val patientId: String
    abstract val date: String
    abstract val riskLevel: RiskLevel
}

@Serializable
data class AncCheckup(
    override val id: String,
    override val patientId: String,
    override val date: String,                   // YYYY-MM-DD
    val weightKg: Double,
    val fundalHeightCm: Double,
    val bpSystolic: Int,
    val bpDiastolic: Int,
    val fetalHeartRate: Int? = null,
    val hemoglobin: Double? = null,
    val symptoms: List<String> = emptyList(),
    val notes: String = "",
    override val riskLevel: RiskLevel = RiskLevel.GREEN,
    val serverId: String? = null,
    val dirty: Boolean = true,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null
) : Checkup()

enum class VisitDay(val key: String, val displayDayNumber: Int) {
    DAY_1("day_1", 1),
    DAY_3("day_3", 3),
    DAY_7("day_7", 7),
    DAY_14("day_14", 14),
    DAY_28("day_28", 28),
    WEEK_6("week_6", 42);

    companion object {
        fun from(key: String): VisitDay = entries.firstOrNull { it.key == key } ?: DAY_1
    }
}

@Serializable
data class NewbornVisit(
    override val id: String,
    override val patientId: String,
    override val date: String,               // YYYY-MM-DD
    val visitDay: VisitDay,
    val weightKg: Double,
    val observations: List<String> = emptyList(),
    val otherObservations: String = "",
    val notes: String = "",
    override val riskLevel: RiskLevel = RiskLevel.GREEN,
    val serverId: String? = null,
    val dirty: Boolean = true,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null
) : Checkup()
