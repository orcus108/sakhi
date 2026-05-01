package `in`.sakhi.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope
import `in`.sakhi.core.ui.theme.SakhiColors

// ── Risk badge ────────────────────────────────────────────────────────────────

enum class RiskUi {
    GREEN, YELLOW, RED;

    companion object {
        fun from(key: String): RiskUi = when (key) {
            "red" -> RED
            "yellow" -> YELLOW
            else -> GREEN
        }
    }
}

data class RiskStyle(
    val background: Color,
    val border: Color,
    val text: Color,
    val labelKey: String      // string resource key suffix, e.g. "normal" "monitor" "high"
)

fun riskStyleFor(risk: RiskUi) = when (risk) {
    RiskUi.RED    -> RiskStyle(SakhiColors.RedBackground, SakhiColors.RedBorder, SakhiColors.RedText, "high")
    RiskUi.YELLOW -> RiskStyle(SakhiColors.YellowBackground, SakhiColors.YellowBorder, SakhiColors.YellowText, "monitor")
    RiskUi.GREEN  -> RiskStyle(SakhiColors.GreenBackground, SakhiColors.GreenBorder, SakhiColors.GreenText, "normal")
}

@Composable
fun RiskBadge(
    risk: RiskUi,
    label: String,
    modifier: Modifier = Modifier
) {
    val style = riskStyleFor(risk)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = style.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, style.border),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = style.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Risk left-border card indicator ──────────────────────────────────────────

@Composable
fun RiskAccentBar(risk: RiskUi, height: Dp = 48.dp, modifier: Modifier = Modifier) {
    val color = when (risk) {
        RiskUi.RED    -> SakhiColors.RedText
        RiskUi.YELLOW -> SakhiColors.YellowText
        RiskUi.GREEN  -> SakhiColors.Primary
    }
    Box(
        modifier = modifier
            .size(4.dp, height)
            .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
            .background(color)
    )
}

// ── Disclaimer text (non-negotiable clinical safety copy) ────────────────────

@Composable
fun DisclaimerText(modifier: Modifier = Modifier) {
    Text(
        text = "Sakhi supports your judgment — always refer when unsure.",
        color = SakhiColors.TextSecondary,
        fontSize = 12.sp,
        modifier = modifier
    )
}

// ── PHC referral banner ───────────────────────────────────────────────────────

@Composable
fun PhcReferralBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SakhiColors.RedBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.RedBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Refer to PHC today — do not delay",
            color = SakhiColors.RedText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ── Offline banner ────────────────────────────────────────────────────────────

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        color = SakhiColors.YellowBackground,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "You're offline — using local assessment",
            color = SakhiColors.YellowText,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ── Loading dots (3 pulsing blue dots — matches design.md) ───────────────────

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val delays = listOf(0, 150, 300)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        delays.forEach { delayMs ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        1f at 300
                        0.3f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(delayMs)
                ),
                label = "dot_$delayMs"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SakhiColors.Primary.copy(alpha = alpha))
            )
        }
    }
}

// ── Top app bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakhiTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        navigationIcon = {
            if (onBack != null) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Home, // placeholder — use Back arrow in real impl
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = SakhiColors.TextPrimary
        )
    )
}

// ── Bottom navigation ─────────────────────────────────────────────────────────

enum class BottomNavDestination { HOME, CHAT, SCHEDULE }

@Composable
fun SakhiBottomNav(
    selected: BottomNavDestination,
    onNavigateToHome: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = Color.White,
        modifier = modifier
    ) {
        BottomNavItem(
            icon = Icons.Default.People,
            label = "Patients",
            selected = selected == BottomNavDestination.HOME,
            onClick = onNavigateToHome
        )
        BottomNavItem(
            icon = Icons.Default.AutoAwesome,
            label = "Ask Sakhi",
            selected = selected == BottomNavDestination.CHAT,
            onClick = onNavigateToChat
        )
        BottomNavItem(
            icon = Icons.Default.DateRange,
            label = "Schedule",
            selected = selected == BottomNavDestination.SCHEDULE,
            onClick = onNavigateToSchedule
        )
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        icon = { Icon(imageVector = icon, contentDescription = label) },
        label = { Text(text = label, fontSize = 12.sp) },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = SakhiColors.Primary,
            selectedTextColor = SakhiColors.Primary,
            indicatorColor = SakhiColors.PrimaryContainer
        )
    )
}
