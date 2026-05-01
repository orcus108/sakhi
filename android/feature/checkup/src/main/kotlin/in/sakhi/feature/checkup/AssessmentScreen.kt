package `in`.sakhi.feature.checkup

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.repository.AssessmentRepository
import `in`.sakhi.core.domain.repository.PatientRepository
import `in`.sakhi.core.ui.component.DisclaimerText
import `in`.sakhi.core.ui.component.PhcReferralBanner
import `in`.sakhi.core.ui.component.RiskBadge
import `in`.sakhi.core.ui.component.RiskUi
import `in`.sakhi.core.ui.theme.SakhiColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── AssessmentViewModel ────────────────────────────────────────────────────────

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _assessment = MutableStateFlow<AssessmentResult?>(null)
    val assessment: StateFlow<AssessmentResult?> = _assessment.asStateFlow()

    private val _checkup = MutableStateFlow<Checkup?>(null)
    val checkup: StateFlow<Checkup?> = _checkup.asStateFlow()

    fun loadAssessment(assessmentId: String) {
        viewModelScope.launch {
            val result = assessmentRepository.getAssessment(assessmentId)
            _assessment.value = result
            if (result != null) {
                _checkup.value = when (result.patientType) {
                    "anc"     -> patientRepository.getAncCheckup(result.checkupId)
                    "newborn" -> patientRepository.getNewbornVisit(result.checkupId)
                    else      -> null
                }
            }
        }
    }
}

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreen(
    assessmentId: String,
    onAskSakhi: (patientId: String, patientType: String) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: AssessmentViewModel = hiltViewModel()
) {
    val assessment by viewModel.assessment.collectAsState()
    val checkup by viewModel.checkup.collectAsState()

    LaunchedEffect(assessmentId) {
        viewModel.loadAssessment(assessmentId)
    }

    Scaffold(
        containerColor = SakhiColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Assessment", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            assessment?.let { a ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAskSakhi(a.patientId, a.patientType) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, SakhiColors.Primary)
                    ) {
                        Text("Ask Sakhi", color = SakhiColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onNavigateHome,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SakhiColors.Primary)
                    ) {
                        Text(
                            text = "Back to Patients",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val a = assessment
        if (a == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text("Loading assessment…", color = SakhiColors.TextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Checkup saved banner ────────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SakhiColors.GreenBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.GreenBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("✓", color = SakhiColors.GreenText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Checkup recorded",
                            color = SakhiColors.GreenText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Offline notice ──────────────────────────────────────────────
            if (a.isOffline) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SakhiColors.YellowBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.YellowBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Local assessment — AI review pending",
                                color = SakhiColors.YellowText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Sakhi AI will review and update this when connected.",
                                color = SakhiColors.YellowText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Risk banner (border-l-4 left-strip treatment) ───────────────
            item {
                val (bannerBg, bannerAccent, riskUi, riskLabel) = when (a.riskLevel) {
                    RiskLevel.RED    -> AssessmentColors(SakhiColors.RedBackground,    SakhiColors.RedText,    RiskUi.RED,    "High Risk")
                    RiskLevel.YELLOW -> AssessmentColors(SakhiColors.YellowBackground, SakhiColors.YellowText, RiskUi.YELLOW, "Monitor")
                    RiskLevel.GREEN  -> AssessmentColors(SakhiColors.GreenBackground,  SakhiColors.GreenText,  RiskUi.GREEN,  "Normal")
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bannerBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // 4dp left accent strip — matches React border-l-4
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(bannerAccent)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                        ) {
                            RiskBadge(risk = riskUi, label = riskLabel)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = a.riskReason,
                                color = SakhiColors.TextPrimary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // ── PHC referral banner (non-negotiable for red/yellow) ─────────
            if (a.riskLevel == RiskLevel.RED || a.riskLevel == RiskLevel.YELLOW) {
                item { PhcReferralBanner() }
            }

            // ── Today's readings ────────────────────────────────────────────
            checkup?.let { c ->
                item {
                    TodaysReadingsCard(checkup = c)
                }
            }

            // ── What Sakhi noticed ──────────────────────────────────────────
            item {
                AssessmentCard(title = "WHAT SAKHI NOTICED") {
                    a.whatSakhiNoticed.forEachIndexed { index, point ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = if (index < a.whatSakhiNoticed.size - 1) 10.dp else 0.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SakhiColors.PrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = SakhiColors.Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Text(
                                text = point,
                                fontSize = 14.sp,
                                color = SakhiColors.TextPrimary,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Tell the patient ────────────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SakhiColors.PrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (a.patientType == "newborn") "TELL THE MOTHER" else "TELL THE PATIENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = SakhiColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${a.whatToTellPatient}\"",
                            fontSize = 14.sp,
                            color = SakhiColors.TextPrimary,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Next action ─────────────────────────────────────────────────
            item {
                val (cardBg, cardBorder, textColor) = when (a.riskLevel) {
                    RiskLevel.RED    -> Triple(SakhiColors.RedBackground, SakhiColors.RedBorder, SakhiColors.RedText)
                    RiskLevel.YELLOW -> Triple(SakhiColors.YellowBackground, SakhiColors.YellowBorder, SakhiColors.YellowText)
                    RiskLevel.GREEN  -> Triple(SakhiColors.GreenBackground, SakhiColors.GreenBorder, SakhiColors.GreenText)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(2.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "NEXT ACTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = SakhiColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = a.whatToDoNext,
                            fontSize = 15.sp,
                            fontWeight = if (a.riskLevel == RiskLevel.RED) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                        if (a.followUpDate != null) {
                            Text(
                                text = "Follow-up: ${a.followUpDate}",
                                color = SakhiColors.TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // ── Disclaimer (non-negotiable) ─────────────────────────────────
            item {
                DisclaimerText(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private data class AssessmentColors(
    val bg: Color,
    val border: Color,
    val riskUi: RiskUi,
    val label: String
)

@Composable
private fun TodaysReadingsCard(checkup: Checkup) {
    AssessmentCard(title = "TODAY'S READINGS") {
        when (checkup) {
            is AncCheckup -> {
                VitalRow("Blood Pressure", "${checkup.bpSystolic}/${checkup.bpDiastolic} mmHg")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SakhiColors.Divider)
                VitalRow("Weight", "${checkup.weightKg} kg")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SakhiColors.Divider)
                VitalRow("Fundal Height", "${checkup.fundalHeightCm} cm")
                checkup.hemoglobin?.let { hb ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SakhiColors.Divider)
                    VitalRow("Hemoglobin", "$hb g/dL")
                }
                checkup.fetalHeartRate?.let { fhr ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SakhiColors.Divider)
                    VitalRow("Fetal Heart Rate", "$fhr bpm")
                }
            }
            is NewbornVisit -> {
                VitalRow("Weight", "${checkup.weightKg} kg")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SakhiColors.Divider)
                VitalRow("Visit Day", "Day ${checkup.visitDay.displayDayNumber}")
            }
        }
    }
}

@Composable
private fun VitalRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, fontSize = 14.sp, color = SakhiColors.TextSecondary)
        Text(value, fontSize = 14.sp, color = SakhiColors.TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AssessmentCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = SakhiColors.TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
