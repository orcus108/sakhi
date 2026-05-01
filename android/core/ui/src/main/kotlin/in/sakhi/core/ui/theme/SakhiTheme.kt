package `in`.sakhi.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import `in`.sakhi.core.ui.R

// ── Color tokens (matches design.md) ─────────────────────────────────────────

object SakhiColors {
    // Primary — blue-600
    val Primary = Color(0xFF2563EB)
    val PrimaryContainer = Color(0xFFDBEAFE)
    val OnPrimary = Color.White
    val OnPrimaryContainer = Color(0xFF1E40AF)

    // Risk levels
    val RedBackground    = Color(0xFFFEF2F2)  // red-50
    val RedBorder        = Color(0xFFFCA5A5)  // red-300
    val RedText          = Color(0xFFB91C1C)  // red-700
    val YellowBackground = Color(0xFFFFFBEB)  // yellow-50
    val YellowBorder     = Color(0xFFFCD34D)  // yellow-300
    val YellowText       = Color(0xFFB45309)  // yellow-700 / amber-700
    val GreenBackground  = Color(0xFFDCFCE7)  // green-100
    val GreenBorder      = Color(0xFFBBF7D0)  // green-200
    val GreenText        = Color(0xFF15803D)  // green-700

    // Surface / background
    val PageBackground  = Color(0xFFF9FAFB)  // gray-50
    val CardBackground  = Color.White
    val Divider         = Color(0xFFF3F4F6)  // gray-100

    // Text
    val TextPrimary     = Color(0xFF111827)  // gray-900
    val TextSecondary   = Color(0xFF6B7280)  // gray-500
    val TextCaption     = Color(0xFF9CA3AF)  // gray-400
}

// ── Typography ────────────────────────────────────────────────────────────────

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

// Noto Sans Devanagari is used for Hindi content.
// TODO: bundle Noto Sans Devanagari font files from Google Fonts.
val NotoSansDevanagari = FontFamily.Default

val SakhiTypography = androidx.compose.material3.Typography()  // Material 3 defaults

// ── Color scheme ──────────────────────────────────────────────────────────────

private val SakhiColorScheme = lightColorScheme(
    primary           = SakhiColors.Primary,
    onPrimary         = SakhiColors.OnPrimary,
    primaryContainer  = SakhiColors.PrimaryContainer,
    onPrimaryContainer = SakhiColors.OnPrimaryContainer,
    background        = SakhiColors.PageBackground,
    surface           = SakhiColors.CardBackground,
    onBackground      = SakhiColors.TextPrimary,
    onSurface         = SakhiColors.TextPrimary,
    surfaceVariant    = SakhiColors.PageBackground,
    outline           = SakhiColors.Divider,
)

@Composable
fun SakhiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SakhiColorScheme,
        typography = SakhiTypography,
        content = content
    )
}
