package `in`.sakhi.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import `in`.sakhi.app.download.DownloadScreen
import `in`.sakhi.app.startup.StartDestination
import `in`.sakhi.app.startup.StartupViewModel
import `in`.sakhi.core.ui.component.BottomNavDestination
import `in`.sakhi.core.ui.component.SakhiBottomNav
import `in`.sakhi.core.ui.theme.SakhiColors
import `in`.sakhi.feature.auth.AuthViewModel
import `in`.sakhi.feature.auth.OnboardingScreen
import `in`.sakhi.feature.chat.AskSakhiScreen
import `in`.sakhi.feature.checkup.AncCheckupScreen
import `in`.sakhi.feature.checkup.AssessmentScreen
import `in`.sakhi.feature.checkup.NewbornCheckupScreen
import `in`.sakhi.feature.home.HomeScreen
import `in`.sakhi.feature.home.SettingsScreen
import kotlinx.serialization.Serializable

// Type-safe navigation routes
@Serializable object Onboarding
@Serializable object Home
@Serializable object Download
@Serializable object Settings
@Serializable data class AncCheckup(val patientId: String)
@Serializable data class NewbornCheckup(val patientId: String)
@Serializable data class Assessment(val assessmentId: String)
@Serializable object AskSakhi
@Serializable object Schedule

private val bottomNavRoutes = listOf(Home, AskSakhi, Schedule)

@Composable
fun SakhiNavHost(
    startupViewModel: StartupViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val startDestination by startupViewModel.startDestination.collectAsState()
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()

    val currentDest = currentBackStack?.destination
    val showBottomNav = currentDest?.let { dest ->
        bottomNavRoutes.any { dest.hasRoute(it::class) }
    } ?: false
    val bottomNavSelected = when {
        currentDest?.hasRoute(AskSakhi::class) == true -> BottomNavDestination.CHAT
        currentDest?.hasRoute(Schedule::class) == true -> BottomNavDestination.SCHEDULE
        else -> BottomNavDestination.HOME
    }

    // Map StartDestination → nav route. The NavHost re-composes once on the
    // first resolved value; subsequent navigations are imperative.
    val initialRoute: Any = when (startDestination) {
        StartDestination.Onboarding -> Onboarding
        StartDestination.Download -> Download
        StartDestination.Home -> Home
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                SakhiBottomNav(
                    selected = bottomNavSelected,
                    onNavigateToHome = {
                        navController.navigate(Home) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToChat = {
                        navController.navigate(AskSakhi) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSchedule = {
                        navController.navigate(Schedule) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Onboarding> {
                OnboardingScreen(
                    onContinue = {
                        authViewModel.completeWithoutOtp()
                        startupViewModel.onLoginComplete()
                        navController.navigate(Home) {
                            popUpTo(Onboarding) { inclusive = true }
                        }
                    }
                )
            }

            composable<Download> {
                DownloadScreen(
                    onDownloadComplete = { modelPath ->
                        startupViewModel.onModelDownloaded(modelPath)
                        navController.navigate(Home) {
                            popUpTo(Download) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Home) {
                            popUpTo(Download) { inclusive = true }
                        }
                    }
                )
            }

            composable<Home> {
                HomeScreen(
                    onPatientClick = { patientId, patientType ->
                        if (patientType == "newborn") navController.navigate(NewbornCheckup(patientId))
                        else navController.navigate(AncCheckup(patientId))
                    },
                    onSettingsClick = { navController.navigate(Settings) }
                )
            }

            composable<Settings> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onAccountDeleted = {
                        startupViewModel.onAccountDeleted()
                        navController.navigate(Onboarding) {
                            popUpTo(0) { inclusive = true }  // clear entire back stack
                        }
                    }
                )
            }

            composable<AncCheckup> { backStack ->
                val route = backStack.toRoute<AncCheckup>()
                AncCheckupScreen(
                    patientId = route.patientId,
                    onAssessmentReady = { assessmentId ->
                        navController.navigate(Assessment(assessmentId)) {
                            popUpTo(Home)
                        }
                    },
                    onNavigateUp = { navController.popBackStack() },
                    onAskSakhi = { navController.navigate(AskSakhi) }
                )
            }

            composable<NewbornCheckup> { backStack ->
                val route = backStack.toRoute<NewbornCheckup>()
                NewbornCheckupScreen(
                    patientId = route.patientId,
                    onAssessmentReady = { assessmentId ->
                        navController.navigate(Assessment(assessmentId)) {
                            popUpTo(Home)
                        }
                    },
                    onNavigateUp = { navController.popBackStack() },
                    onAskSakhi = { navController.navigate(AskSakhi) }
                )
            }

            composable<Assessment> { backStack ->
                val route = backStack.toRoute<Assessment>()
                AssessmentScreen(
                    assessmentId = route.assessmentId,
                    onAskSakhi = { _, _ -> navController.navigate(AskSakhi) },
                    onNavigateHome = {
                        navController.navigate(Home) { popUpTo(Home) { inclusive = true } }
                    },
                    onNavigateUp = { navController.popBackStack() }
                )
            }

            composable<AskSakhi> {
                AskSakhiScreen()
            }

            composable<Schedule> {
                SchedulePlaceholderScreen()
            }
        }
    }
}

@Composable
private fun SchedulePlaceholderScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SakhiColors.PageBackground)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = SakhiColors.TextSecondary
            )
            Text(
                text = "Schedule",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = SakhiColors.TextPrimary
            )
            Text(
                text = "Coming soon",
                fontSize = 14.sp,
                color = SakhiColors.TextSecondary
            )
        }
    }
}
