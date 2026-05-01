package `in`.sakhi.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.sakhi.core.ui.theme.DmSans
import `in`.sakhi.core.ui.theme.SakhiColors

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val name by viewModel.workerName.collectAsState()
    val ashaId by viewModel.ashaWorkerId.collectAsState()
    val language by viewModel.language.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 32.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero ─────────────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .background(SakhiColors.Primary, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.onboarding_app_name),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DmSans,
            color = SakhiColors.Primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.onboarding_tagline),
            fontSize = 15.sp,
            fontFamily = DmSans,
            color = SakhiColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Language selector ─────────────────────────────────────────────
        Text(
            text = stringResource(R.string.onboarding_language_label),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = DmSans,
            letterSpacing = 1.sp,
            color = SakhiColors.TextCaption,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LanguageChip(
                label = stringResource(R.string.onboarding_language_en),
                selected = language == "en",
                onClick = { viewModel.onLanguageChange("en") },
                modifier = Modifier.weight(1f)
            )
            LanguageChip(
                label = stringResource(R.string.onboarding_language_hi),
                selected = language == "hi",
                onClick = { viewModel.onLanguageChange("hi") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── ASHA Worker ID ────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.onboarding_asha_id_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = DmSans,
            color = SakhiColors.TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = ashaId,
            onValueChange = viewModel::onAshaWorkerIdChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.onboarding_asha_id_hint),
                    fontFamily = DmSans,
                    color = SakhiColors.TextCaption
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SakhiColors.Primary,
                unfocusedBorderColor = Color(0xFFE5E7EB),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = DmSans,
                fontSize = 15.sp,
                color = SakhiColors.TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Your name ────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.onboarding_name_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = DmSans,
            color = SakhiColors.TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = viewModel::onNameChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.onboarding_name_hint),
                    fontFamily = DmSans,
                    color = SakhiColors.TextCaption
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SakhiColors.Primary,
                unfocusedBorderColor = Color(0xFFE5E7EB),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = DmSans,
                fontSize = 15.sp,
                color = SakhiColors.TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        // ── Start button ──────────────────────────────────────────────────
        Button(
            onClick = onContinue,
            enabled = viewModel.isOnboardingValid(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = SakhiColors.Primary,
                disabledContainerColor = Color(0xFFBFDBFE)
            )
        ) {
            Text(
                text = stringResource(R.string.onboarding_start),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = DmSans
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_demo_hint),
            fontSize = 12.sp,
            fontFamily = DmSans,
            color = SakhiColors.TextCaption,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SakhiColors.Primary else Color.White,
        border = BorderStroke(
            1.5.dp,
            if (selected) SakhiColors.Primary else Color(0xFFE5E7EB)
        ),
        modifier = modifier.height(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) Color.White else SakhiColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = DmSans
            )
        }
    }
}
