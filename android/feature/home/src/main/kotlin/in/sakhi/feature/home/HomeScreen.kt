package `in`.sakhi.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.ui.component.RiskBadge
import `in`.sakhi.core.ui.component.RiskUi
import `in`.sakhi.core.ui.theme.SakhiColors

@Composable
fun HomeScreen(
    onPatientClick: (patientId: String, patientType: String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SakhiColors.PageBackground,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            item {
                HomeHeader(
                    workerName = state.workerName,
                    ashaId = state.ashaId,
                    language = state.language,
                    onLanguageChange = viewModel::onLanguageChange,
                )
            }

            // ── Village + count strip ───────────────────────────────────────────
            if (state.primaryVillage.isNotBlank()) {
                item {
                    VillageStrip(
                        village = state.primaryVillage,
                        totalPatients = state.totalPatients
                    )
                }
            }

            // ── Search ──────────────────────────────────────────────────────────
            item {
                SearchBar(
                    query = state.search,
                    onQueryChange = viewModel::onSearchChange
                )
            }

            // ── Risk filter chips ───────────────────────────────────────────────
            item {
                RiskFilterRow(
                    selected = state.riskFilter,
                    onSelect = viewModel::onRiskFilterChange
                )
            }

            // ── Village chips (only if multiple villages) ───────────────────────
            if (state.villages.size > 1) {
                item {
                    VillageFilterRow(
                        villages = state.villages,
                        selected = state.villageFilter,
                        onSelect = viewModel::onVillageFilterChange
                    )
                }
            }

            // ── Lists ───────────────────────────────────────────────────────────
            if (state.riskFilter != RiskFilter.ALL) {
                // Risk-filter mode: flat section
                if (state.filtered.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = when (state.riskFilter) {
                                RiskFilter.RED -> "High Risk"
                                RiskFilter.YELLOW -> "Monitor"
                                RiskFilter.GREEN -> "Normal"
                                RiskFilter.ALL -> ""
                            },
                            count = state.filtered.size,
                            urgent = state.riskFilter == RiskFilter.RED
                        )
                    }
                    items(state.filtered, key = { it.id }) { item ->
                        PatientRow(
                            item = item,
                            onClick = { onPatientClick(item.id, item.patientType) }
                        )
                    }
                }
            } else {
                // Default mode: three sections
                if (state.overdue.isNotEmpty()) {
                    item { SectionHeader(title = "Overdue", count = state.overdue.size, urgent = true) }
                    items(state.overdue, key = { it.id }) { item ->
                        PatientRow(item = item, onClick = { onPatientClick(item.id, item.patientType) })
                    }
                }
                if (state.dueToday.isNotEmpty()) {
                    item { SectionHeader(title = "Due Today", count = state.dueToday.size, urgent = false) }
                    items(state.dueToday, key = { it.id }) { item ->
                        PatientRow(item = item, onClick = { onPatientClick(item.id, item.patientType) })
                    }
                }
                if (state.urgent.isNotEmpty()) {
                    item { SectionHeader(title = "High Risk", count = state.urgent.size, urgent = true) }
                    items(state.urgent, key = { it.id }) { item ->
                        PatientRow(item = item, onClick = { onPatientClick(item.id, item.patientType) })
                    }
                }
            }

            // ── Empty state ─────────────────────────────────────────────────────
            val isEmpty = if (state.riskFilter == RiskFilter.ALL) {
                state.overdue.isEmpty() && state.dueToday.isEmpty() && state.urgent.isEmpty()
            } else {
                state.filtered.isEmpty()
            }
            if (isEmpty) {
                item {
                    EmptyState(
                        hasFilters = state.search.isNotBlank() ||
                            state.villageFilter != "all" ||
                            state.riskFilter != RiskFilter.ALL
                    )
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    workerName: String,
    ashaId: String,
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SakhiColors.Primary)
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 16.dp)
    ) {
        // Left: welcome text, name, ASHA ID
        Column {
            Text(
                text = "Welcome back,",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp
            )
            Text(
                text = workerName.ifBlank { "ASHA Worker" },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            if (ashaId.isNotBlank()) {
                Text(
                    text = ashaId,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        // Right: language toggle pill only
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Row {
                listOf("en" to "EN", "hi" to "हिं").forEach { (code, label) ->
                    Surface(
                        onClick = { onLanguageChange(code) },
                        color = if (language == code) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (language == code) SakhiColors.Primary else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Village strip ──────────────────────────────────────────────────────────────

@Composable
private fun VillageStrip(village: String, totalPatients: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SakhiColors.PrimaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = SakhiColors.Primary,
            modifier = Modifier.size(14.dp)
        )
        Text(text = village, color = SakhiColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(text = "·  $totalPatients patients", color = SakhiColors.Primary.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

// ── Search bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search by name...", color = SakhiColors.TextSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = SakhiColors.TextSecondary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SakhiColors.Divider,
                focusedBorderColor = SakhiColors.Primary,
                unfocusedContainerColor = SakhiColors.PageBackground,
                focusedContainerColor = SakhiColors.PageBackground,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )
    }
}

// ── Risk filter row ────────────────────────────────────────────────────────────

// Colors matching React riskFilterActive
private val FILTER_BG = mapOf(
    RiskFilter.ALL    to Color(0xFF2563EB),  // blue-600
    RiskFilter.RED    to Color(0xFFEF4444),  // red-500
    RiskFilter.YELLOW to Color(0xFFFACC15),  // yellow-400
    RiskFilter.GREEN  to Color(0xFF16A34A),  // green-600
)
private val FILTER_TEXT = mapOf(
    RiskFilter.ALL    to Color.White,
    RiskFilter.RED    to Color.White,
    RiskFilter.YELLOW to Color(0xFF111827),  // gray-900 on yellow
    RiskFilter.GREEN  to Color.White,
)
// Unselected dot colors (React: bg-red-500 / bg-yellow-400 / bg-green-500)
private val FILTER_DOT = mapOf(
    RiskFilter.RED    to Color(0xFFEF4444),  // red-500
    RiskFilter.YELLOW to Color(0xFFFACC15),  // yellow-400
    RiskFilter.GREEN  to Color(0xFF22C55E),  // green-500
)

private data class RiskChip(val filter: RiskFilter, val label: String)

@Composable
private fun RiskFilterRow(selected: RiskFilter, onSelect: (RiskFilter) -> Unit) {
    val chips = listOf(
        RiskChip(RiskFilter.ALL, "All"),
        RiskChip(RiskFilter.RED, "High Risk"),
        RiskChip(RiskFilter.YELLOW, "Monitor"),
        RiskChip(RiskFilter.GREEN, "Normal"),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 6.dp)
    ) {
        items(chips) { chip ->
            val isSelected = chip.filter == selected
            val bg  = if (isSelected) (FILTER_BG[chip.filter] ?: SakhiColors.Primary) else Color.White
            val txt = if (isSelected) (FILTER_TEXT[chip.filter] ?: Color.White) else Color(0xFF4B5563) // gray-600
            val borderColor = if (isSelected) bg else Color(0xFFE5E7EB) // gray-200
            val dot = FILTER_DOT[chip.filter]
            Surface(
                onClick = { onSelect(chip.filter) },
                shape = RoundedCornerShape(50.dp),
                color = bg,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (dot != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.8f) else dot)
                        )
                    }
                    Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = txt)
                }
            }
        }
    }
}

// ── Village filter row ─────────────────────────────────────────────────────────

@Composable
private fun VillagePill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = if (isSelected) SakhiColors.Primary else Color.White,
        border = BorderStroke(1.dp, if (isSelected) SakhiColors.Primary else Color(0xFFE5E7EB)),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else Color(0xFF4B5563),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VillageFilterRow(
    villages: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 4.dp)
    ) {
        item {
            VillagePill(label = "All villages", isSelected = selected == "all", onClick = { onSelect("all") })
        }
        items(villages) { v ->
            VillagePill(label = v.split(",").first().trim(), isSelected = selected == v, onClick = { onSelect(v) })
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, urgent: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = if (urgent) SakhiColors.RedText else SakhiColors.TextSecondary
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (urgent) SakhiColors.RedBackground else SakhiColors.PageBackground
        ) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (urgent) SakhiColors.RedText else SakhiColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Patient row ────────────────────────────────────────────────────────────────

@Composable
private fun PatientRow(
    item: PatientListItem,
    onClick: () -> Unit
) {
    val riskUi = when (item.riskLevel) {
        RiskLevel.RED -> RiskUi.RED
        RiskLevel.YELLOW -> RiskUi.YELLOW
        RiskLevel.GREEN -> RiskUi.GREEN
    }
    val riskLabel = when (item.riskLevel) {
        RiskLevel.RED -> "High Risk"
        RiskLevel.YELLOW -> "Monitor"
        RiskLevel.GREEN -> "Normal"
    }
    val accentColor = when (item.riskLevel) {
        RiskLevel.RED    -> Color(0xFFEF4444)  // red-500 (React: bg-red-500)
        RiskLevel.YELLOW -> Color(0xFFFACC15)  // yellow-400 (React: bg-yellow-400)
        RiskLevel.GREEN  -> Color(0xFF16A34A)  // green-600 (React: bg-green-600)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Risk accent bar — stretches to match card height
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(accentColor)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Left: name + subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SakhiColors.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 13.sp,
                        color = SakhiColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: due label + risk badge + chevron
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val days = item.daysUntilDue
                    if (days != null) {
                        val (label, color) = when {
                            days < 0  -> {
                                val n = -days
                                "$n ${if (n == 1) "day" else "days"} overdue" to SakhiColors.RedText
                            }
                            days == 0 -> "Due today" to Color(0xFFF97316) // orange-500
                            days == 1 -> "Due tomorrow" to SakhiColors.TextSecondary
                            days < 8  -> "In $days ${if (days == 1) "day" else "days"}" to SakhiColors.TextSecondary
                            else      -> item.nextDueDate?.let {
                                "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }}"
                            } to SakhiColors.TextSecondary
                        }
                        if (label != null) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = color ?: SakhiColors.TextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        RiskBadge(risk = riskUi, label = riskLabel)
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SakhiColors.TextCaption,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasFilters: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 32.dp)
    ) {
        if (hasFilters) {
            Text(
                text = "No patients match your filters",
                fontSize = 16.sp,
                color = SakhiColors.TextSecondary
            )
        } else {
            Text(
                text = "All caught up!",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = SakhiColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No follow-ups due — check back later.",
                fontSize = 14.sp,
                color = SakhiColors.TextSecondary
            )
        }
    }
}
