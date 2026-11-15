package app.zcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.zcal.data.repo.ProfileRepository
import app.zcal.ui.cache.HistoryCacheScreen
import app.zcal.ui.cache.HistoryCacheViewModel
import app.zcal.ui.camera.CameraCaptureScreen
import app.zcal.ui.components.BottomNavTab
import app.zcal.ui.components.FloatingBottomNav
import app.zcal.ui.home.HomeScreen
import app.zcal.ui.home.HomeViewModel
import app.zcal.ui.onboarding.OnboardingScreen
import app.zcal.ui.onboarding.OnboardingViewModel
import app.zcal.ui.progress.ProgressScreen
import app.zcal.ui.progress.ProgressViewModel
import app.zcal.ui.review.ReviewMealScreen
import app.zcal.ui.review.ReviewViewModel
import app.zcal.ui.settings.SettingsScreen
import app.zcal.ui.settings.SettingsViewModel
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalTheme
import app.zcal.ui.theme.ZCalTypeScale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MainActivity with Camera-First Architecture:
 * - Before starting the app for the first time (`!onboardingCompleted`), launches `"onboarding"`
 *   where the user sets Weight, Height (Tall), Goal, and API Key to generate Day/Week/Month targets.
 * - Once onboarding is complete, the Pre-Built Camera (`BottomNavTab.CAMERA.route`) IS the Main Page!
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var profileRepository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val profile by profileRepository.userProfileFlow.collectAsState(initial = null)
            val themePref by profileRepository.themePreferenceFlow.collectAsState(initial = app.zcal.domain.model.ThemePreference.SYSTEM)
            val dynamicColor by profileRepository.dynamicColorFlow.collectAsState(initial = false)

            ZCalTheme(
                themePreference = themePref,
                dynamicColorEnabled = dynamicColor
            ) {
                val currentProfile = profile
                if (currentProfile == null) {
                    ZCalBootScreen()
                    return@ZCalTheme
                }
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                val sharedReviewViewModel: ReviewViewModel = hiltViewModel()
                val sharedHomeViewModel: HomeViewModel = hiltViewModel()

                // Pre-built Camera is the Main Page after first-time onboarding!
                val startDest = if (currentProfile.onboardingCompleted) {
                    BottomNavTab.CAMERA.route
                } else {
                    "onboarding"
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(220)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                        popExitTransition = { fadeOut(animationSpec = tween(220)) }
                    ) {
                        composable("onboarding") {
                            val onboardingVm: OnboardingViewModel = hiltViewModel()
                            OnboardingScreen(
                                viewModel = onboardingVm,
                                onFinished = {
                                    navController.navigate(BottomNavTab.CAMERA.route) {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(BottomNavTab.CAMERA.route) {
                            CameraCaptureScreen(
                                reviewViewModel = sharedReviewViewModel,
                                homeViewModel = sharedHomeViewModel,
                                onAnalysisReadyForReview = {
                                    navController.navigate("review")
                                },
                                onOpenHistoryCache = {
                                    navController.navigate(BottomNavTab.CACHE.route)
                                },
                                onOpenSettingsForKey = {
                                    navController.navigate(BottomNavTab.SETTINGS.route)
                                }
                            )
                        }

                        composable(BottomNavTab.HOME.route) {
                            HomeScreen(
                                viewModel = sharedHomeViewModel,
                                onOpenMealForEdit = { mealId ->
                                    sharedReviewViewModel.loadExistingMealForEdit(mealId)
                                    navController.navigate("review")
                                },
                                onLaunchCamera = {
                                    sharedReviewViewModel.resetToIdle()
                                    navController.navigate(BottomNavTab.CAMERA.route)
                                }
                            )
                        }

                        composable(BottomNavTab.CACHE.route) {
                            val cacheVm: HistoryCacheViewModel = hiltViewModel()
                            HistoryCacheScreen(viewModel = cacheVm)
                        }

                        composable(BottomNavTab.PROGRESS.route) {
                            val progressVm: ProgressViewModel = hiltViewModel()
                            ProgressScreen(viewModel = progressVm)
                        }

                        composable(BottomNavTab.SETTINGS.route) {
                            val settingsVm: SettingsViewModel = hiltViewModel()
                            SettingsScreen(viewModel = settingsVm)
                        }

                        composable("review") {
                            ReviewMealScreen(
                                viewModel = sharedReviewViewModel,
                                onSaved = {
                                    navController.navigate(BottomNavTab.HOME.route) {
                                        popUpTo(BottomNavTab.CAMERA.route) { inclusive = false }
                                    }
                                },
                                onBack = {
                                    sharedReviewViewModel.resetToIdle()
                                    navController.popBackStack()
                                }
                            )
                        }
                    }

                    // Show floating bottom nav on Camera, Dashboard, History Cache, Progress, and Settings
                    if (currentRoute in listOf(
                            BottomNavTab.CAMERA.route,
                            BottomNavTab.HOME.route,
                            BottomNavTab.CACHE.route,
                            BottomNavTab.PROGRESS.route,
                            BottomNavTab.SETTINGS.route
                        )
                    ) {
                        FloatingBottomNav(
                            currentRoute = currentRoute,
                            onTabSelected = { tab ->
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(BottomNavTab.CAMERA.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            onScanClicked = {
                                sharedReviewViewModel.resetToIdle()
                                if (currentRoute != BottomNavTab.CAMERA.route) {
                                    navController.navigate(BottomNavTab.CAMERA.route) {
                                        popUpTo(BottomNavTab.CAMERA.route) { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium Boot & Loading Screen:
 * Uses the transparent logo with gentle breathing pulse animation,
 * styled against the #23212C theme while data finishes initializing.
 */
@Composable
fun ZCalBootScreen(modifier: Modifier = Modifier) {
    val colors = LocalZCalColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "bootPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bootLogoScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_transparent),
                contentDescription = "ZCal Boot Logo",
                modifier = Modifier
                    .size(112.dp)
                    .scale(scale)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "ZCal",
                style = ZCalTypeScale.Headline,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Open-Source AI Nutrition",
                style = ZCalTypeScale.BodySmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.primary,
                strokeWidth = 2.5.dp
            )
        }
    }
}
