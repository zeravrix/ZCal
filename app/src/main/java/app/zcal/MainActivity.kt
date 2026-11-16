package app.zcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.zcal.data.repo.ProfileRepository
import app.zcal.domain.model.AppLanguage
import app.zcal.domain.model.ThemePreference
import app.zcal.ui.cache.HistoryCacheScreen
import app.zcal.ui.cache.HistoryCacheViewModel
import app.zcal.ui.camera.CameraCaptureScreen
import app.zcal.ui.components.BottomNavTab
import app.zcal.ui.components.FloatingBottomNav
import app.zcal.ui.home.HomeScreen
import app.zcal.ui.home.HomeViewModel
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.localization.ZCalStrings
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MainActivity with Camera-First Architecture:
 * - Single native system splash screen (holds until profile state is loaded from Room/DataStore)
 * - Auto hardware notch and display cutout support across edge-to-edge windowing
 * - Before starting the app for the first time (`!onboardingCompleted`), launches `"onboarding"`
 * - Once onboarding is complete, the Pre-Built Camera (`BottomNavTab.CAMERA.route`) IS the Main Page
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var computeGoalsUseCase: app.zcal.domain.usecase.ComputeGoalsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val profile by profileRepository.userProfileFlow.collectAsState(initial = null)
            val themePref by profileRepository.themePreferenceFlow.collectAsState(initial = ThemePreference.SYSTEM)
            val dynamicColor by profileRepository.dynamicColorFlow.collectAsState(initial = false)
            val language by profileRepository.languageFlow.collectAsState(initial = AppLanguage.ENGLISH)

            val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            if (profile != null) {
                isReady = true
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalZCalStrings provides ZCalStrings.forLanguage(language)
            ) {
                ZCalTheme(
                    themePreference = themePref,
                    dynamicColorEnabled = dynamicColor
                ) {
                    val currentProfile = profile ?: return@ZCalTheme
                    val colors = LocalZCalColors.current

                    // Long-term health: automatically recalibrate goals if user aged up or changed measurements
                    LaunchedEffect(
                        currentProfile.birthYear,
                        currentProfile.birthMonth,
                        currentProfile.birthDay,
                        currentProfile.weightKg,
                        currentProfile.heightCm,
                        currentProfile.activityLevel,
                        currentProfile.goalType
                    ) {
                        if (currentProfile.onboardingCompleted) {
                            val computed = computeGoalsUseCase.fromProfile(currentProfile)
                            if (computed.dailyCalories != currentProfile.calorieGoal) {
                                profileRepository.saveUserProfile(
                                    currentProfile.copy(
                                        calorieGoal = computed.dailyCalories,
                                        kilojouleGoal = computed.dailyKilojoules,
                                        proteinGoalG = computed.proteinG,
                                        carbsGoalG = computed.carbsG,
                                        fatGoalG = computed.fatG,
                                        claGoalMg = computed.claMg,
                                        fiberGoalG = computed.fiberG
                                    )
                                )
                            }
                        }
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.canvas)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDest,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.canvas),
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { 70 },
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                                ) + fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { -50 },
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                                ) + fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { -50 },
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                                ) + fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { 70 },
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                                ) + fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                            }
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
                                        navController.navigate(BottomNavTab.CACHE.route) {
                                            popUpTo(BottomNavTab.CAMERA.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenSettingsForKey = {
                                        navController.navigate(BottomNavTab.SETTINGS.route) {
                                            popUpTo(BottomNavTab.CAMERA.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
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
                                        if (!navController.popBackStack(BottomNavTab.CAMERA.route, false)) {
                                            navController.navigate(BottomNavTab.CAMERA.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                            }
                                        }
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
                                        sharedReviewViewModel.resetToIdle()
                                        navController.navigate(BottomNavTab.HOME.route) {
                                            popUpTo(BottomNavTab.CAMERA.route) { inclusive = false }
                                            launchSingleTop = true
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
                                        if (!navController.popBackStack(BottomNavTab.CAMERA.route, false)) {
                                            navController.navigate(BottomNavTab.CAMERA.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                            }
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
}
