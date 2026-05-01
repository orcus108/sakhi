package `in`.sakhi.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.sakhi.feature.auth.R
import `in`.sakhi.core.ui.theme.SakhiColors

@Composable
fun PhoneOtpScreen(
    onVerified: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val countdown by viewModel.resendCountdown.collectAsState()

    var otp by remember { mutableStateOf("") }

    // Navigate when verified
    LaunchedEffect(authState) {
        if (authState is AuthState.Verified) onVerified()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (authState is AuthState.OtpSent) "Enter OTP" else "Your phone number",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SakhiColors.TextPrimary
            )
            if (authState is AuthState.OtpSent) {
                Text(
                    text = stringResource(R.string.auth_otp_sent, phone),
                    fontSize = 14.sp,
                    color = SakhiColors.TextSecondary
                )
            }
        }

        if (authState !is AuthState.OtpSent) {
            // Phone entry
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auth_phone_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SakhiColors.TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+91",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SakhiColors.TextPrimary
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = viewModel::onPhoneChange,
                        placeholder = { Text(stringResource(R.string.auth_phone_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }
            }
        } else {
            // OTP entry
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auth_otp_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SakhiColors.TextSecondary
                )
                OutlinedTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otp = it },
                    placeholder = { Text("• • • • • •") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }

        // Error message
        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = SakhiColors.RedText,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action button
        if (authState is AuthState.Loading) {
            CircularProgressIndicator(
                color = SakhiColors.Primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else if (authState !is AuthState.OtpSent) {
            Button(
                onClick = viewModel::sendOtp,
                enabled = phone.length == 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SakhiColors.Primary)
            ) {
                Text(
                    text = stringResource(R.string.auth_send_otp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Verify + resend
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.verifyOtp(otp) },
                    enabled = otp.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SakhiColors.Primary)
                ) {
                    Text(
                        text = stringResource(R.string.auth_verify),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = viewModel::resendOtp,
                    enabled = countdown == 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (countdown > 0)
                            stringResource(R.string.auth_resend_in, countdown)
                        else
                            stringResource(R.string.auth_resend),
                        color = if (countdown > 0) SakhiColors.TextSecondary else SakhiColors.Primary
                    )
                }
            }
        }
    }
}
