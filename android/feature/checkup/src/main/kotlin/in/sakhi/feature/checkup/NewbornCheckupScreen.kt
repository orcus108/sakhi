package `in`.sakhi.feature.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.VisitDay
import `in`.sakhi.core.ui.component.LoadingDots
import `in`.sakhi.core.ui.theme.SakhiColors
import kotlin.math.abs

// ── Observation data (labels MUST stay English — sent to AI) ─────────────────

data class ObsItem(val key: String, val label: String, val isDanger: Boolean)
data class ObsGroup(val title: String, val items: List<ObsItem>)

private val OBSERVATION_GROUPS = listOf(
    ObsGroup("Feeding & Activity", listOf(
        ObsItem("feeding_well",   "Feeding well (breastfeeding established)", isDanger = false),
        ObsItem("baby_alert",     "Baby alert and active",                    isDanger = false),
        ObsItem("baby_lethargic", "Baby lethargic or not responding",         isDanger = true),
    )),
    ObsGroup("Breathing", listOf(
        ObsItem("breathing_normal",  "Breathing normal",          isDanger = false),
        ObsItem("breathing_labored", "Breathing labored or fast", isDanger = true),
    )),
    ObsGroup("Skin, Cord & Eyes", listOf(
        ObsItem("cord_clean",    "Umbilical cord — clean and dry",          isDanger = false),
        ObsItem("cord_infected", "Umbilical cord — red / swollen / smelly", isDanger = true),
        ObsItem("jaundice",      "Jaundice visible (yellow skin / eyes)",    isDanger = true),
        ObsItem("skin_rash",     "Skin rash or pustules",                   isDanger = true),
        ObsItem("eye_discharge", "Eyes — discharge present",                isDanger = true),
    )),
)

private val ALL_OBSERVATIONS = OBSERVATION_GROUPS.flatMap { it.items }

private val VISIT_OPTIONS = listOf(
    VisitDay.DAY_1  to "Day 1",
    VisitDay.DAY_3  to "Day 3",
    VisitDay.DAY_7  to "Day 7",
    VisitDay.DAY_14 to "Day 14",
    VisitDay.DAY_28 to "Day 28",
    VisitDay.WEEK_6 to "6 Weeks",
)

private fun recommendedVisit(patient: NewbornPatient): VisitDay {
    val done = patient.visitHistory.map { it.visitDay }.toSet()
    return VISIT_OPTIONS.firstOrNull { (key, _) -> key !in done }?.first ?: VisitDay.DAY_1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewbornCheckupScreen(
    patientId: String,
    onAssessmentReady: (assessmentId: String) -> Unit,
    onNavigateUp: () -> Unit,
    onAskSakhi: () -> Unit,
    viewModel: CheckupViewModel = hiltViewModel()
) {
    LaunchedEffect(patientId) { viewModel.loadNewbornPatient(patientId) }
    val patient by viewModel.newbornPatient.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val resolvedPatient = patient ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SakhiColors.Primary)
        }
        return
    }

    var step by rememberSaveable { mutableStateOf(0) }
    var visitDay by rememberSaveable { mutableStateOf(recommendedVisit(resolvedPatient)) }
    var weightKg by rememberSaveable { mutableStateOf("") }
    var selectedKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
    var otherObs by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf("") }

    val recommended = recommendedVisit(resolvedPatient)
    val activeDangerItems = ALL_OBSERVATIONS.filter { it.isDanger && it.key in selectedKeys }

    LaunchedEffect(uiState) {
        if (uiState is CheckupUiState.Success) {
            onAssessmentReady((uiState as CheckupUiState.Success).assessmentId)
            viewModel.resetState()
        }
    }

    Scaffold(
        containerColor = SakhiColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Visit — ${resolvedPatient.name}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (step > 0) step-- else onNavigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState is CheckupUiState.Loading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LoadingDots()
                            Text("Sakhi is thinking…", color = SakhiColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onAskSakhi,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, SakhiColors.Primary)
                    ) {
                        Text("Ask Sakhi", color = SakhiColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            if (step == 0) {
                                val w = weightKg.trim().toDoubleOrNull()
                                if (w == null || w <= 0) {
                                    validationError = "Please enter a valid weight"
                                    return@Button
                                }
                                validationError = ""
                                step = 1
                            } else {
                                val selectedLabels = ALL_OBSERVATIONS
                                    .filter { it.key in selectedKeys }
                                    .map { it.label }
                                val visit = CheckupViewModel.buildNewbornVisit(
                                    patientId = patientId,
                                    visitDay = visitDay,
                                    weightKg = weightKg,
                                    observations = selectedLabels,
                                    otherObservations = otherObs
                                )
                                viewModel.submitNewbornVisit(resolvedPatient, visit)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SakhiColors.Primary)
                    ) {
                        Text(
                            text = if (step == 0) "Next: Observations" else "Get Assessment",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding()
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                StepIndicator(currentStep = step, steps = listOf("Measurements", "Observations"))
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Text(
                    text = "${resolvedPatient.name} · ${resolvedPatient.motherName}'s baby · Birth: ${resolvedPatient.birthWeightKg} kg",
                    fontSize = 13.sp,
                    color = SakhiColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (step == 0) {
                // ── Visit day selector ───────────────────────────────────────
                item {
                    Text(
                        text = "Visit Day *",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SakhiColors.TextPrimary
                    )
                    Text(
                        text = "Select the visit milestone",
                        fontSize = 12.sp,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                    // 3-column grid of visit day buttons
                    VISIT_OPTIONS.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            row.forEach { (day, label) ->
                                val isSelected = visitDay == day
                                val isRecommended = day == recommended && !isSelected
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        onClick = { visitDay = day },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) SakhiColors.Primary else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            if (isSelected) SakhiColors.Primary else SakhiColors.Divider
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else SakhiColors.TextPrimary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    // Blue dot indicator for recommended visit
                                    if (isRecommended) {
                                        Surface(
                                            shape = CircleShape,
                                            color = SakhiColors.Primary,
                                            modifier = Modifier
                                                .size(10.dp)
                                                .align(Alignment.TopEnd)
                                                .padding(end = 2.dp, top = 2.dp)
                                        ) {}
                                    }
                                }
                            }
                            // Fill last row if fewer than 3
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Current weight ───────────────────────────────────────────
                item {
                    Text(
                        text = "Current Weight (kg) *",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SakhiColors.TextPrimary
                    )
                    Text(
                        text = "Weigh the baby now",
                        fontSize = 12.sp,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it; validationError = "" },
                        placeholder = { Text("e.g. 2.8", color = SakhiColors.TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = SakhiColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    )
                    // Weight delta vs birth weight
                    val w = weightKg.toDoubleOrNull()
                    if (w != null && w > 0) {
                        val delta = w - resolvedPatient.birthWeightKg
                        val pctLoss = if (delta < 0) (abs(delta) / resolvedPatient.birthWeightKg) * 100 else 0.0
                        val deltaColor = when {
                            delta >= 0 -> SakhiColors.GreenText
                            pctLoss > 10 -> SakhiColors.RedText
                            else -> SakhiColors.YellowText
                        }
                        Text(
                            text = if (delta >= 0)
                                "+%.2f kg from birth weight".format(delta)
                            else
                                "%.2f kg from birth weight (%.1f%% loss)".format(delta, pctLoss),
                            color = deltaColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // ── Observations step ────────────────────────────────────────
                item {
                    Text(
                        text = "What did you observe?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SakhiColors.TextPrimary
                    )
                    Text(
                        text = "Visit: ${VISIT_OPTIONS.first { it.first == visitDay }.second}  ·  Weight: ${weightKg} kg",
                        fontSize = 13.sp,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                }

                items(OBSERVATION_GROUPS) { group ->
                    Text(
                        text = group.title.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )
                    group.items.forEach { obs ->
                        val isSelected = obs.key in selectedKeys
                        ObsTile(
                            label = obs.label,
                            isDanger = obs.isDanger,
                            isSelected = isSelected,
                            onClick = {
                                selectedKeys = if (isSelected) selectedKeys - obs.key
                                else selectedKeys + obs.key
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Danger sign warning
                if (activeDangerItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SakhiColors.RedBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.RedBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Danger signs detected",
                                    color = SakhiColors.RedText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                activeDangerItems.forEach { obs ->
                                    Text(
                                        text = "· ${obs.label}",
                                        color = SakhiColors.RedText,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Consider PHC referral immediately.",
                                    color = SakhiColors.RedText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Other observations free text
                item {
                    Text(
                        text = "Other observations",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = otherObs,
                        onValueChange = { otherObs = it },
                        placeholder = { Text("Any other observations…", color = SakhiColors.TextSecondary) },
                        singleLine = false,
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (validationError.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SakhiColors.RedBackground
                    ) {
                        Text(
                            text = validationError,
                            color = SakhiColors.RedText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (uiState is CheckupUiState.Error) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SakhiColors.RedBackground
                    ) {
                        Text(
                            text = (uiState as CheckupUiState.Error).message,
                            color = SakhiColors.RedText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Observation tile ───────────────────────────────────────────────────────────

@Composable
private fun ObsTile(
    label: String,
    isDanger: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (bgColor, borderColor, textColor) = when {
        isSelected && isDanger  -> Triple(SakhiColors.RedBackground, SakhiColors.RedBorder, SakhiColors.RedText)
        isSelected && !isDanger -> Triple(SakhiColors.GreenBackground, SakhiColors.GreenBorder, SakhiColors.GreenText)
        else                    -> Triple(Color.White, SakhiColors.Divider, SakhiColors.TextSecondary)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Checkmark circle
            Surface(
                shape = CircleShape,
                color = if (isSelected) borderColor else SakhiColors.Divider,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = label,
                fontSize = 14.sp,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
