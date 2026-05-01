package `in`.sakhi.core.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class RiskLevel(val key: String) {
    GREEN("green"),
    YELLOW("yellow"),
    RED("red");

    companion object {
        fun from(key: String): RiskLevel = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

enum class Language(val code: String) {
    EN("en"),
    HI("hi");

    companion object {
        fun from(code: String): Language = entries.firstOrNull { it.code == code } ?: EN
    }
}

/** Union type for passing patients through use cases without knowing patient type. */
sealed class Patient {
    abstract val id: String
    abstract val ashaWorkerId: String
    abstract val name: String
    abstract val nameHi: String
    abstract val village: String
    abstract val villageHi: String
    abstract val riskLevel: RiskLevel
    abstract val abdmId: String?
}

@Serializable
data class AncPatient(
    override val id: String,
    override val ashaWorkerId: String,
    override val name: String,
    override val nameHi: String = "",
    override val village: String,
    override val villageHi: String = "",
    override val riskLevel: RiskLevel = RiskLevel.GREEN,
    override val abdmId: String? = null,
    val age: Int,
    val phone: String = "",
    val lmp: String? = null,                // YYYY-MM-DD
    val gestationalWeeks: Int? = null,
    val gravida: Int? = null,
    val para: Int? = null,
    val checkupHistory: List<AncCheckup> = emptyList(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val serverId: String? = null,
    val dirty: Boolean = true
) : Patient()

@Serializable
data class NewbornPatient(
    override val id: String,
    override val ashaWorkerId: String,
    override val name: String,
    override val nameHi: String = "",
    override val village: String,
    override val villageHi: String = "",
    override val riskLevel: RiskLevel = RiskLevel.GREEN,
    override val abdmId: String? = null,
    val gender: String = "",                // "male" | "female"
    val dateOfBirth: String,               // YYYY-MM-DD
    val motherId: String? = null,
    val motherName: String = "",
    val motherNameHi: String = "",
    val birthWeightKg: Double,
    val currentWeightKg: Double? = null,
    val visitHistory: List<NewbornVisit> = emptyList(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val serverId: String? = null,
    val dirty: Boolean = true
) : Patient()
