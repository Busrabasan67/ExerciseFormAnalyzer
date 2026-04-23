package com.example.exerciseformanalyzer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.CameraPreviewScreen
import com.example.exerciseformanalyzer.ui.ExerciseSelectionScreen
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.auth.AuthViewModel
import com.example.exerciseformanalyzer.ui.auth.LoginScreen
import com.example.exerciseformanalyzer.ui.auth.RegisterScreen
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel
import com.example.exerciseformanalyzer.ui.dashboard.ExpertDashboardScreen
import com.example.exerciseformanalyzer.ui.dashboard.PatientDashboardScreen
import com.example.exerciseformanalyzer.ui.dashboard.TaskExerciseStartParams
import com.example.exerciseformanalyzer.ui.group.GroupListScreen
import com.example.exerciseformanalyzer.ui.group.GroupViewModel
import com.example.exerciseformanalyzer.ui.history.HistoryScreen
import com.example.exerciseformanalyzer.ui.profile.ProfileScreen
import com.example.exerciseformanalyzer.ui.SplashScreen

enum class Route {
    Splash, Login, Register,
    PatientDashboard, ExpertDashboard,
    Profile, History, Groups, GroupDetail,
    ExerciseSelect, Camera, PatientDetail,
    AdminDashboard, SocialFeed, Leaderboard
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel
) {
    val authViewModel: AuthViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()
    val groupViewModel: GroupViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Route.Splash.name,
        modifier = modifier
    ) {
        composable(Route.Splash.name) {
            SplashScreen(
                onTimeout = {
                    val currentUid = authViewModel.currentUid
                    if (currentUid != null) {
                        // Kullanıcı zaten logun, direkt dashboard'a (Veya profil yüklenene kadar bekle)
                        // Şimdilik default bir yönlendirme yapalım
                        navController.navigate(Route.PatientDashboard.name) {
                            popUpTo(Route.Splash.name) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Route.Login.name) {
                            popUpTo(Route.Splash.name) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Route.Login.name) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Route.Register.name) },
                onLanguageChange = { lang -> mainViewModel.setLanguage(lang) },
                onLoginSuccess = { role ->
                    val target = when (role.uppercase()) {
                        "ADMIN" -> Route.AdminDashboard.name
                        "EXPERT" -> Route.ExpertDashboard.name
                        else -> Route.PatientDashboard.name
                    }
                    navController.navigate(target) {
                        popUpTo(Route.Login.name) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Register.name) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() },
                onLanguageChange = { lang -> mainViewModel.setLanguage(lang) },
                onRegisterSuccess = { role ->
                    val target = if (role == "EXPERT") Route.ExpertDashboard.name else Route.PatientDashboard.name
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.PatientDashboard.name) {
            PatientDashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToCamera = { exerciseType ->
                    // Serbest egzersiz (FAB butonu)
                    if (exerciseType != null) {
                        mainViewModel.setTargetExercise(exerciseType, taskContext = null)
                        navController.navigate(Route.Camera.name)
                    } else {
                        navController.navigate(Route.ExerciseSelect.name)
                    }
                },
                onNavigateToTaskExercise = { params ->
                    // Görev bağlamlı egzersiz — taskId + index ile kesin eşleşme garantisi
                    val ctx = MainViewModel.TaskContext(
                        taskId = params.taskId,
                        exerciseIndex = params.exerciseIndex,
                        targetType = params.targetType,
                        targetReps = params.targetReps,
                        targetDurationSeconds = params.targetDurationSeconds
                    )
                    mainViewModel.setTargetExercise(params.exerciseType, taskContext = ctx)
                    navController.navigate(Route.Camera.name)
                },
                onNavigateToProfile = { navController.navigate(Route.Profile.name) },
                onNavigateToGroups = { navController.navigate(Route.Groups.name) },
                onNavigateToSocial = { navController.navigate(Route.SocialFeed.name) },
                onNavigateToLeaderboard = { navController.navigate(Route.Leaderboard.name) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.name) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.ExpertDashboard.name) {
            ExpertDashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToProfile = { navController.navigate(Route.Profile.name) },
                onNavigateToPatientDetail = { uid -> navController.navigate("${Route.PatientDetail.name}/$uid") },
                onNavigateToSocial = { navController.navigate(Route.SocialFeed.name) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.name) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("${Route.PatientDetail.name}/{patientUid}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("patientUid") ?: ""
            com.example.exerciseformanalyzer.ui.dashboard.PatientDetailScreen(
                viewModel = dashboardViewModel,
                patientUid = uid,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.AdminDashboard.name) {
            com.example.exerciseformanalyzer.ui.admin.AdminDashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToCamera = { exerciseType ->
                    if (exerciseType != null) {
                        mainViewModel.setTargetExercise(exerciseType, taskContext = null)
                        navController.navigate(Route.Camera.name)
                    } else {
                        navController.navigate(Route.ExerciseSelect.name)
                    }
                },
                onNavigateToTaskExercise = { params ->
                    val ctx = MainViewModel.TaskContext(
                        taskId = params.taskId,
                        exerciseIndex = params.exerciseIndex,
                        targetType = params.targetType,
                        targetReps = params.targetReps,
                        targetDurationSeconds = params.targetDurationSeconds
                    )
                    mainViewModel.setTargetExercise(params.exerciseType, taskContext = ctx)
                    navController.navigate(Route.Camera.name)
                },
                onNavigateToProfile = { navController.navigate(Route.Profile.name) },
                onNavigateToGroups = { navController.navigate(Route.Groups.name) },
                onNavigateToSocial = { navController.navigate(Route.SocialFeed.name) },
                onNavigateToLeaderboard = { navController.navigate(Route.Leaderboard.name) },
                onNavigateToPatientDetail = { uid -> navController.navigate("${Route.PatientDetail.name}/$uid") },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.name) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.SocialFeed.name) {
            com.example.exerciseformanalyzer.ui.social.SocialFeedScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Leaderboard.name) {
            com.example.exerciseformanalyzer.ui.social.LeaderboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Profile.name) {
            ProfileScreen(
                viewModel = dashboardViewModel,
                mainViewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.History.name) {
            HistoryScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Groups.name) {
            GroupListScreen(
                viewModel = groupViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { docId, name, desc, creatorId ->
                    navController.navigate("${Route.GroupDetail.name}/$docId/$name/$desc/$creatorId")
                }
            )
        }

        composable("${Route.GroupDetail.name}/{docId}/{name}/{desc}/{creatorId}") { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val desc = backStackEntry.arguments?.getString("desc") ?: ""
            val creatorId = backStackEntry.arguments?.getString("creatorId") ?: ""
            com.example.exerciseformanalyzer.ui.group.GroupDetailScreen(
                groupDocId = docId,
                groupName = name,
                groupDescription = desc,
                creatorId = creatorId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Hareket seçim ekranı — kamera açılmadan önce
        composable(Route.ExerciseSelect.name) {
            ExerciseSelectionScreen(
                onExerciseSelected = { exerciseType ->
                    // ExerciseType'ı route argümanı olarak taşımak yerine ViewModel'e set et
                    mainViewModel.setTargetExercise(exerciseType)
                    navController.navigate(Route.Camera.name) {
                        popUpTo(Route.ExerciseSelect.name) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Kamera ekranı — setTargetExercise zaten çağrıldı
        composable(Route.Camera.name) {
            CameraPreviewScreen(
                viewModel = mainViewModel,
                onNavigateBack = {
                    mainViewModel.resetSession()
                    navController.popBackStack()
                }
            )
        }
    }
}
