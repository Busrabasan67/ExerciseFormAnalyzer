package com.example.exerciseformanalyzer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.exerciseformanalyzer.domain.model.TaskContext
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.CameraPreviewScreen
import com.example.exerciseformanalyzer.ui.ExerciseSelectionScreen
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.workout.WorkoutViewModel
import com.example.exerciseformanalyzer.ui.auth.AuthViewModel
import com.example.exerciseformanalyzer.ui.auth.LoginScreen
import com.example.exerciseformanalyzer.ui.auth.RegisterScreen
import com.example.exerciseformanalyzer.ui.dashboard.PatientViewModel
import com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel
import com.example.exerciseformanalyzer.ui.dashboard.AdminViewModel
import com.example.exerciseformanalyzer.ui.profile.ProfileViewModel
import com.example.exerciseformanalyzer.ui.dashboard.ExpertDashboardScreen
import com.example.exerciseformanalyzer.ui.dashboard.PatientDashboardScreen
import com.example.exerciseformanalyzer.ui.dashboard.TaskExerciseStartParams
import com.example.exerciseformanalyzer.ui.group.GroupListScreen
import com.example.exerciseformanalyzer.ui.group.GroupViewModel
import com.example.exerciseformanalyzer.ui.history.HistoryScreen
import com.example.exerciseformanalyzer.ui.profile.ProfileScreen
import com.example.exerciseformanalyzer.ui.SplashScreen

sealed class Route(val route: String) {
    object Splash : Route("splash")
    object Login : Route("login")
    object Register : Route("register")
    object PatientDashboard : Route("patient_dashboard")
    object ExpertDashboard : Route("expert_dashboard")
    object AdminDashboard : Route("admin_dashboard")
    object Profile : Route("profile")
    object History : Route("history")
    object Groups : Route("groups")
    object GroupDetail : Route("group_detail")
    object ExerciseSelect : Route("exercise_select")
    object Camera : Route("camera")
    object PatientDetail : Route("patient_detail/{patientUid}") {
        fun createRoute(uid: String) = "patient_detail/$uid"
    }
    object SocialFeed : Route("social_feed")
    object Leaderboard : Route("leaderboard")
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel
) {
    val authViewModel: AuthViewModel = viewModel()
    val patientViewModel: PatientViewModel = viewModel()
    val expertViewModel: ExpertViewModel = viewModel()
    val adminViewModel: AdminViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val groupViewModel: GroupViewModel = viewModel()
    val workoutViewModel: WorkoutViewModel = viewModel()

    // Dil senkronizasyonu
    val currentLanguage by mainViewModel.currentLanguage.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(currentLanguage) {
        workoutViewModel.initTextToSpeech(currentLanguage)
    }

    NavHost(
        navController = navController,
        startDestination = Route.Splash.route,
        modifier = modifier
    ) {
        composable(Route.Splash.route) {
            // checkAutoLogin'i başlat — SplashScreen animasyonu gösterirken arka planda koşar
            androidx.compose.runtime.LaunchedEffect(Unit) {
                authViewModel.checkAutoLogin()
            }

            // authViewModel.uiState değişince yönlendir
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(authState) {
                when (val s = authState) {
                    is com.example.exerciseformanalyzer.ui.auth.AuthUiState.Success -> {
                        val target = when (s.role.uppercase()) {
                            "ADMIN" -> Route.AdminDashboard.route
                            "EXPERT" -> Route.ExpertDashboard.route
                            else -> Route.PatientDashboard.route
                        }
                        navController.navigate(target) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    }
                    is com.example.exerciseformanalyzer.ui.auth.AuthUiState.Idle -> {
                        // Timeout sonrası hâlâ Idle ise giriş ekranına git
                    }
                    else -> { /* Loading durumunda bekle */ }
                }
            }

            SplashScreen(
                onTimeout = {
                    // authState hâlâ Idle ise (auto-login yok) Login'e yönlendir
                    if (authViewModel.uiState.value is com.example.exerciseformanalyzer.ui.auth.AuthUiState.Idle ||
                        authViewModel.uiState.value is com.example.exerciseformanalyzer.ui.auth.AuthUiState.Loading) {
                        navController.navigate(Route.Login.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    }
                    // Success ise LaunchedEffect zaten yönlendirdi
                }
            )
        }
        composable(Route.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Route.Register.route) },
                onLanguageChange = { lang -> mainViewModel.setLanguage(lang) },
                onLoginSuccess = { role ->
                    val target = when (role.uppercase()) {
                        "ADMIN" -> Route.AdminDashboard.route
                        "EXPERT" -> Route.ExpertDashboard.route
                        else -> Route.PatientDashboard.route
                    }
                    navController.navigate(target) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() },
                onLanguageChange = { lang -> mainViewModel.setLanguage(lang) },
                onRegisterSuccess = { role ->
                    val target = if (role == "EXPERT") Route.ExpertDashboard.route else Route.PatientDashboard.route
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.PatientDashboard.route) {
            PatientDashboardScreen(
                viewModel = patientViewModel,
                onNavigateToCamera = { exerciseType ->
                    // Serbest egzersiz (FAB butonu)
                    if (exerciseType != null) {
                        workoutViewModel.setTargetExercise(exerciseType, taskContext = null)
                        navController.navigate(Route.Camera.route)
                    } else {
                        navController.navigate(Route.ExerciseSelect.route)
                    }
                },
                onNavigateToTaskExercise = { params ->
                    // Görev bağlamlı egzersiz — taskId + index ile kesin eşleşme garantisi
                    val ctx = TaskContext(
                        taskId = params.taskId,
                        exerciseIndex = params.exerciseIndex,
                        targetType = params.targetType,
                        targetReps = params.targetReps,
                        targetDurationSeconds = params.targetDurationSeconds,
                        targetSets = params.targetSets,
                        completedSets = params.completedSets
                    )
                    workoutViewModel.setTargetExercise(params.exerciseType, taskContext = ctx)
                    navController.navigate(Route.Camera.route)
                },
                onNavigateToProfile = { navController.navigate(Route.Profile.route) },
                onNavigateToGroups = { navController.navigate(Route.Groups.route) },
                onNavigateToSocial = { navController.navigate(Route.SocialFeed.route) },
                onNavigateToLeaderboard = { navController.navigate(Route.Leaderboard.route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.ExpertDashboard.route) {
            ExpertDashboardScreen(
                viewModel = expertViewModel,
                onNavigateToProfile = { navController.navigate(Route.Profile.route) },
                onNavigateToPatientDetail = { uid -> navController.navigate(Route.PatientDetail.createRoute(uid)) },
                onNavigateToSocial = { navController.navigate(Route.SocialFeed.route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.PatientDetail.route) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("patientUid") ?: ""
            com.example.exerciseformanalyzer.ui.dashboard.PatientDetailScreen(
                viewModel = expertViewModel,
                patientUid = uid,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.AdminDashboard.route) {
            com.example.exerciseformanalyzer.ui.admin.AdminDashboardScreen(
                viewModel = adminViewModel,
                patientViewModel = patientViewModel,
                expertViewModel = expertViewModel,
                onNavigateToCamera = { exerciseType ->
                    if (exerciseType != null) {
                        workoutViewModel.setTargetExercise(exerciseType, taskContext = null)
                        navController.navigate(Route.Camera.route)
                    } else {
                        navController.navigate(Route.ExerciseSelect.route)
                    }
                },
                onNavigateToTaskExercise = { params ->
                    val ctx = TaskContext(
                        taskId = params.taskId,
                        exerciseIndex = params.exerciseIndex,
                        targetType = params.targetType,
                        targetReps = params.targetReps,
                        targetDurationSeconds = params.targetDurationSeconds,
                        targetSets = params.targetSets,
                        completedSets = params.completedSets
                    )
                    workoutViewModel.setTargetExercise(params.exerciseType, taskContext = ctx)
                    navController.navigate(Route.Camera.route)
                },
                onNavigateToProfile = { navController.navigate(Route.Profile.route) },
                onNavigateToGroups = { navController.navigate(Route.Groups.route) },
                onNavigateToSocial = { navController.navigate(Route.SocialFeed.route) },
                onNavigateToLeaderboard = { navController.navigate(Route.Leaderboard.route) },
                onNavigateToPatientDetail = { uid -> navController.navigate(Route.PatientDetail.createRoute(uid)) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.SocialFeed.route) {
            com.example.exerciseformanalyzer.ui.social.SocialFeedScreen(
                viewModel = patientViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Leaderboard.route) {
            com.example.exerciseformanalyzer.ui.social.LeaderboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Profile.route) {
            ProfileScreen(
                viewModel = profileViewModel,
                mainViewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.History.route) {
            HistoryScreen(
                viewModel = patientViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Groups.route) {
            GroupListScreen(
                viewModel = groupViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { docId, name, desc, creatorId ->
                    groupViewModel.setSelectedGroup(docId, name, desc, creatorId)
                    navController.navigate(Route.GroupDetail.route)
                }
            )
        }

        composable(Route.GroupDetail.route) {
            com.example.exerciseformanalyzer.ui.group.GroupDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Hareket seçim ekranı — kamera açılmadan önce
        composable(Route.ExerciseSelect.route) {
            ExerciseSelectionScreen(
                onExerciseSelected = { exerciseType ->
                    // ExerciseType'ı route argümanı olarak taşımak yerine ViewModel'e set et
                    workoutViewModel.setTargetExercise(exerciseType)
                    navController.navigate(Route.Camera.route) {
                        popUpTo(Route.ExerciseSelect.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Camera.route) {
            CameraPreviewScreen(
                viewModel = workoutViewModel,
                onNavigateBack = {
                    workoutViewModel.resetSession()
                    navController.popBackStack()
                }
            )
        }
    }
}
