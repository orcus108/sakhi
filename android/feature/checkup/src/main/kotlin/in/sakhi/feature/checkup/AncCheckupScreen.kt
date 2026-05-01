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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import `in`.sakhi.core.ui.component.LoadingDots
import `in`.sakhi.core.ui.theme.SakhiColors

// API values must remain English regardless of display language
private val SYMPTOM_OPTIONS = listOf(
    "headache",
    "blurred vision",
    "swelling in feet",
    "swelling in hands or face",
    "upper abdominal pain",
    "reduced fetal movement",
    "difficulty breathing",
    "nausea or vomiting",
    "dizziness",
    "fatigue",
    "fever",
    "vaginal bleeding",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncCheckupScreen(
    patientId: String,
    onAssessmentReady: (assessmentId: String) -> Unit,
    onNavigateUp: () -> Unit,
    onAskSakhi: () -> Unit,
    viewModel: CheckupViewModel = hiltViewModel()
) {
    LaunchedEffect(patientId) { viewModel.loadAncPatient(patientId) }
    val patient by viewModel.ancPatient.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val resolvedPatient = patient ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SakhiColors.Primary)
        }
        return
    }

    var step by rememberSaveable { mutableStateOf(0) }

    // Step 0 state
    var bpSystolic by rememberSaveable { mutableStateOf("") }
    var bpDiastolic by rememberSaveable { mutableStateOf("") }
    var weightKg by rememberSaveable { mutableStateOf("") }
    var fundalHeight by rememberSaveable { mutableStateOf("") }
    var fetalHr by rememberSaveable { mutableStateOf("") }
    var hemoglobin by rememberSaveable { mutableStateOf("") }

    // Step 1 state
    var selectedSymptoms by rememberSaveable { mutableStateOf(setOf<String>()) }
    var otherSymptom by rememberSaveable { mutableStateOf("") }

    var validationError by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is CheckupUiState.Success) {
            onAssessmentReady((uiState as CheckupUiState.Success).assessmentId)
            viewModel.resetState()
        }
    }

    val bpElevated = bpSystolic.toIntOrNull()?.let { it >= 140 } == true ||
        bpDiastolic.toIntOrNull()?.let { it >= 90 } == true

    Scaffold(
        containerColor = SakhiColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Checkup — ${resolvedPatient.name}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
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
                            Text(
                                text = "Sakhi is thinking…",
                                color = SakhiColors.TextSecondary,
                                fontSize = 14.sp
                            )
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
                                val err = validateStep0(bpSystolic, bpDiastolic, weightKg, fundalHeight)
                                if (err.isNotEmpty()) { validationError = err; return@Button }
                                validationError = ""
                                step = 1
                            } else {
                                val allSymptoms = selectedSymptoms.toMutableList().also {
                                    if (otherSymptom.isNotBlank()) it.add(otherSymptom.trim())
                                }
                                val checkup = CheckupViewModel.buildAncCheckup(
                                    patientId = patientId,
                                    bpSystolic = bpSystolic,
                                    bpDiastolic = bpDiastolic,
                                    weightKg = weightKg,
                                    fundalHeightCm = fundalHeight,
                                    fetalHeartRate = fetalHr,
                                    hemoglobin = hemoglobin,
                                    symptoms = allSymptoms
                                )
                                viewModel.submitAncCheckup(resolvedPatient, checkup)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SakhiColors.Primary)
                    ) {
                        Text(
                            text = if (step == 0) "Next: Symptoms" else "Get Assessment",
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
            // Step indicator
            item {
                StepIndicator(currentStep = step, steps = listOf("Vitals", "Symptoms"))
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Patient context
            item {
                Text(
                    text = buildString {
                        append(resolvedPatient.name)
                        resolvedPatient.gestationalWeeks?.let { append(" · $it weeks pregnant") }
                        if (resolvedPatient.gravida != null && resolvedPatient.para != null) {
                            append(" · G${resolvedPatient.gravida}P${resolvedPatient.para}")
                        }
                    },
                    fontSize = 13.sp,
                    color = SakhiColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (step == 0) {
                // ── Vitals step ──────────────────────────────────────────────
                item {
                    BpRow(
                        systolic = bpSystolic,
                        diastolic = bpDiastolic,
                        onSystolicChange = { bpSystolic = it.filter { c -> c.isDigit() } },
                        onDiastolicChange = { bpDiastolic = it.filter { c -> c.isDigit() } },
                        elevated = bpElevated
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    VitalField(
                        label = "Weight (kg) *",
                        hint = "Use the weighing scale",
                        value = weightKg,
                        onChange = { weightKg = it },
                        keyboard = KeyboardType.Decimal
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    VitalField(
                        label = "Fundal Height (cm) *",
                        hint = "Measure with tape from pubic bone to top of uterus",
                        value = fundalHeight,
                        onChange = { fundalHeight = it },
                        keyboard = KeyboardType.Number
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    VitalField(
                        label = "Fetal Heart Rate (bpm)",
                        hint = "Optional — record if measured",
                        value = fetalHr,
                        onChange = { fetalHr = it },
                        keyboard = KeyboardType.Number
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    VitalField(
                        label = "Haemoglobin (g/dL)",
                        hint = "Optional — if tested today",
                        value = hemoglobin,
                        onChange = { hemoglobin = it },
                        keyboard = KeyboardType.Decimal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // ── Symptoms step ────────────────────────────────────────────
                item {
                    Text(
                        text = "Is the patient experiencing any of these?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SakhiColors.TextPrimary
                    )
                    Text(
                        text = "Select all that apply",
                        fontSize = 13.sp,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                }
                // Symptom tiles — 2 per row via chunked items
                items(SYMPTOM_OPTIONS.chunked(2)) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        row.forEach { symptom ->
                            val selected = symptom in selectedSymptoms
                            SymptomTile(
                                label = symptom.replaceFirstChar { it.uppercase() },
                                selected = selected,
                                onClick = {
                                    selectedSymptoms = if (selected)
                                        selectedSymptoms - symptom
                                    else
                                        selectedSymptoms + symptom
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill last row if odd number of symptoms
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                // Other symptoms free text
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Any other symptom?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = SakhiColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = otherSymptom,
                        onValueChange = { otherSymptom = it },
                        placeholder = { Text("Type here...", color = SakhiColors.TextSecondary) },
                        singleLine = false,
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Validation error
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

            // API error
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

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
internal fun StepIndicator(currentStep: Int, steps: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        steps.forEachIndexed { index, label ->
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { if (index <= currentStep) 1f else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = SakhiColors.Primary,
                    trackColor = SakhiColors.Divider
                )
                Text(
                    text = "${index + 1}. $label",
                    fontSize = 11.sp,
                    fontWeight = if (index == currentStep) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == currentStep) SakhiColors.Primary else SakhiColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BpRow(
    systolic: String,
    diastolic: String,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    elevated: Boolean
) {
    Column {
        Text(
            text = "Blood Pressure *",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = SakhiColors.TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Systolic (top)", fontSize = 11.sp, color = SakhiColors.TextSecondary)
                OutlinedTextField(
                    value = systolic,
                    onValueChange = onSystolicChange,
                    placeholder = { Text("e.g. 120") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            }
            Text("/", fontSize = 24.sp, color = SakhiColors.TextSecondary, fontWeight = FontWeight.Light)
            Column(modifier = Modifier.weight(1f)) {
                Text("Diastolic (bottom)", fontSize = 11.sp, color = SakhiColors.TextSecondary)
                OutlinedTextField(
                    value = diastolic,
                    onValueChange = onDiastolicChange,
                    placeholder = { Text("e.g. 80") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            }
        }
        if (elevated) {
            Text(
                text = "BP is elevated — refer to PHC if above 140/90",
                color = SakhiColors.RedText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun VitalField(
    label: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Decimal
) {
    Column {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = SakhiColors.TextPrimary
        )
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                fontSize = 12.sp,
                color = SakhiColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SakhiColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        )
    }
}

@Composable
private fun SymptomTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SakhiColors.PrimaryContainer else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (selected) SakhiColors.Primary else SakhiColors.Divider
        ),
        modifier = modifier.height(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = if (selected) "✓ $label" else label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) SakhiColors.Primary else SakhiColors.TextSecondary,
                maxLines = 2,
                lineHeight = 16.sp
            )
        }
    }
}

// ── Validation ─────────────────────────────────────────────────────────────────

private fun validateStep0(
    bpSystolic: String,
    bpDiastolic: String,
    weightKg: String,
    fundalHeight: String
): String {
    if (bpSystolic.toIntOrNull() == null || bpDiastolic.toIntOrNull() == null)
        return "Please enter a valid blood pressure reading"
    if (weightKg.toDoubleOrNull() == null)
        return "Please enter the patient's current weight"
    if (fundalHeight.toDoubleOrNull() == null)
        return "Please enter the fundal height"
    return ""
}
