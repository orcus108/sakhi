package `in`.sakhi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.model.VisitDay
import `in`.sakhi.core.domain.repository.PatientRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** A unified row shown in the patient list — wraps ANC or newborn record. */
data class PatientListItem(
    val id: String,
    val patientType: String,        // "anc" | "newborn"
    val displayName: String,        // localised per current language
    val subtitle: String,           // e.g. "32 weeks" or "7 days old · Mother: Priya"
    val village: String,
    val riskLevel: RiskLevel,
    val nextDueDate: LocalDate?,    // null if no history or terminal milestone
    val daysUntilDue: Int?,         // negative = overdue, 0 = today, positive = future
)

enum class RiskFilter { ALL, RED, YELLOW, GREEN }

data class HomeUiState(
    val workerName: String = "",
    val ashaId: String = "",
    val language: String = "en",
    val totalPatients: Int = 0,
    val primaryVillage: String = "",

    // section data (only populated when riskFilter == ALL)
    val overdue: List<PatientListItem> = emptyList(),
    val dueToday: List<PatientListItem> = emptyList(),
    val urgent: List<PatientListItem> = emptyList(),       // red, not in overdue or dueToday

    // filter mode data (only populated when riskFilter != ALL)
    val filtered: List<PatientListItem> = emptyList(),

    val riskFilter: RiskFilter = RiskFilter.ALL,
    val search: String = "",
    val villages: List<String> = emptyList(),
    val villageFilter: String = "all",
)

// ── Next-due date helpers (mirrors Home.jsx) ──────────────────────────────────

private val ANC_INTERVAL_DAYS = mapOf(
    RiskLevel.RED to 7,
    RiskLevel.YELLOW to 14,
    RiskLevel.GREEN to 28,
)

/** Days from last visit day to next milestone. null = terminal (day_28 → week_6 handled separately). */
private val NEWBORN_NEXT_DAYS = mapOf(
    VisitDay.DAY_1  to 2,
    VisitDay.DAY_3  to 4,
    VisitDay.DAY_7  to 7,
    VisitDay.DAY_14 to 14,
    VisitDay.DAY_28 to null,   // no automatic next from 28-day visit (week_6 is the last milestone)
    VisitDay.WEEK_6 to null,   // terminal
)

private fun ancNextDue(patient: AncPatient): LocalDate? {
    val lastCheckup = patient.checkupHistory.maxByOrNull { it.date } ?: return null
    val interval = ANC_INTERVAL_DAYS[patient.riskLevel] ?: 28
    return LocalDate.parse(lastCheckup.date).plusDays(interval.toLong())
}

private fun newbornNextDue(patient: NewbornPatient): LocalDate? {
    val lastVisit = patient.visitHistory.maxByOrNull { it.date } ?: return null
    val days = NEWBORN_NEXT_DAYS[lastVisit.visitDay] ?: return null
    return LocalDate.parse(lastVisit.date).plusDays(days.toLong())
}

private fun daysUntil(date: LocalDate?): Int? {
    if (date == null) return null
    return ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()
}

// Carries all reactive filter values through the combine pipeline
private data class FilterState(
    val search: String,
    val riskFilter: RiskFilter,
    val villageFilter: String,
    val language: String,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PatientRepository,
    private val authPrefs: AuthPreferences
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _riskFilter = MutableStateFlow(RiskFilter.ALL)
    private val _villageFilter = MutableStateFlow("all")
    private val _language = MutableStateFlow(authPrefs.getLanguage())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val workerId = authPrefs.getUserId() ?: ""
        val workerName = authPrefs.getWorkerName() ?: ""
        val ashaId = authPrefs.getAshaId()

        val filtersFlow = combine(
            _search.debounce(300),
            _riskFilter,
            _villageFilter,
            _language,
        ) { s, r, v, l -> FilterState(s, r, v, l) }

        combine(
            repository.observeAncPatients(workerId),
            repository.observeNewbornPatients(workerId),
            filtersFlow,
        ) { ancList, newbornList, filters ->
            buildUiState(
                ancList = ancList,
                newbornList = newbornList,
                search = filters.search,
                riskFilter = filters.riskFilter,
                villageFilter = filters.villageFilter,
                workerName = workerName,
                ashaId = ashaId,
                language = filters.language,
            )
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun onSearchChange(q: String) { _search.value = q }
    fun onRiskFilterChange(f: RiskFilter) { _riskFilter.value = f }
    fun onVillageFilterChange(v: String) { _villageFilter.value = v }
    fun onLanguageChange(code: String) {
        authPrefs.setLanguage(code)
        _language.value = code
    }

    private fun buildUiState(
        ancList: List<AncPatient>,
        newbornList: List<NewbornPatient>,
        search: String,
        riskFilter: RiskFilter,
        villageFilter: String,
        workerName: String,
        ashaId: String,
        language: String,
    ): HomeUiState {
        val today = LocalDate.now()
        val q = search.trim().lowercase()

        // Build unified items
        val allItems: List<PatientListItem> = buildList {
            ancList.forEach { p ->
                val next = ancNextDue(p)
                add(PatientListItem(
                    id = p.id,
                    patientType = "anc",
                    displayName = if (language == "hi" && p.nameHi.isNotBlank()) p.nameHi else p.name,
                    subtitle = if (p.gestationalWeeks != null) "${p.gestationalWeeks} weeks pregnant" else "ANC",
                    village = p.village,
                    riskLevel = p.riskLevel,
                    nextDueDate = next,
                    daysUntilDue = daysUntil(next),
                ))
            }
            newbornList.forEach { n ->
                val next = newbornNextDue(n)
                val agedays = ChronoUnit.DAYS.between(LocalDate.parse(n.dateOfBirth), today).toInt()
                val motherLabel = if (language == "hi" && n.motherNameHi.isNotBlank()) n.motherNameHi else n.motherName
                add(PatientListItem(
                    id = n.id,
                    patientType = "newborn",
                    displayName = if (language == "hi" && n.nameHi.isNotBlank()) n.nameHi else n.name,
                    subtitle = "${agedays}d old" + if (motherLabel.isNotBlank()) " · $motherLabel" else "",
                    village = n.village,
                    riskLevel = n.riskLevel,
                    nextDueDate = next,
                    daysUntilDue = daysUntil(next),
                ))
            }
        }

        // Search filter
        val searchFiltered = if (q.isEmpty()) allItems else allItems.filter { item ->
            item.displayName.lowercase().contains(q) ||
            // Also try searching in the other language name from original records
            ancList.find { it.id == item.id }?.let {
                it.name.lowercase().contains(q) || it.nameHi.lowercase().contains(q)
            } == true ||
            newbornList.find { it.id == item.id }?.let {
                it.name.lowercase().contains(q) || it.nameHi.lowercase().contains(q) ||
                it.motherName.lowercase().contains(q) || it.motherNameHi.lowercase().contains(q)
            } == true
        }

        // Village filter
        val villageFiltered = if (villageFilter == "all") searchFiltered
        else searchFiltered.filter { it.village == villageFilter }

        val villages = (ancList.map { it.village } + newbornList.map { it.village })
            .distinct().sorted()

        val primaryVillage = (ancList.firstOrNull()?.village ?: newbornList.firstOrNull()?.village)
            ?.split(",")?.firstOrNull()?.trim() ?: ""

        // Risk filter mode: flat list
        if (riskFilter != RiskFilter.ALL) {
            val targetLevel = when (riskFilter) {
                RiskFilter.RED -> RiskLevel.RED
                RiskFilter.YELLOW -> RiskLevel.YELLOW
                RiskFilter.GREEN -> RiskLevel.GREEN
                RiskFilter.ALL -> error("unreachable")
            }
            val filtered = villageFiltered
                .filter { it.riskLevel == targetLevel }
                .sortedWith(compareBy(nullsLast()) { it.nextDueDate })
            return HomeUiState(
                workerName = workerName,
                ashaId = ashaId,
                language = language,
                totalPatients = ancList.size + newbornList.size,
                primaryVillage = primaryVillage,
                filtered = filtered,
                riskFilter = riskFilter,
                search = search,
                villages = villages,
                villageFilter = villageFilter,
            )
        }

        // Default "all" mode — three sections
        val overdue = villageFiltered
            .filter { (it.daysUntilDue ?: 1) < 0 }
            .sortedBy { it.nextDueDate }

        val dueToday = villageFiltered
            .filter { it.daysUntilDue == 0 }

        val shownIds = (overdue + dueToday).map { it.id }.toHashSet()
        val urgent = villageFiltered
            .filter { it.riskLevel == RiskLevel.RED && it.id !in shownIds }
            .sortedWith(compareBy(nullsLast()) { it.nextDueDate })

        return HomeUiState(
            workerName = workerName,
            ashaId = ashaId,
            language = language,
            totalPatients = ancList.size + newbornList.size,
            primaryVillage = primaryVillage,
            overdue = overdue,
            dueToday = dueToday,
            urgent = urgent,
            riskFilter = RiskFilter.ALL,
            search = search,
            villages = villages,
            villageFilter = villageFilter,
        )
    }
}
