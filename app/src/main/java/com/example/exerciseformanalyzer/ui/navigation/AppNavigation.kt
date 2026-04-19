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
import com.example.exerciseformanalyzer.ui.group.GroupListScreen
import com.example.exerciseformanalyzer.ui.group.GroupViewModel
import com.example.exerciseformanalyzer.ui.history.HistoryScreen
import com.example.exerciseformanalyzer.ui.profile.ProfileScreen

enum class Route {
    Login, Register,
    PatientDashboard, ExpertDashboard,
    Profile, History, Groups,
    ExerciseSelect, Camera
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
        startDestination = Route.Login.name,
        modifier = modifier
    ) {
        composable(Route.Login.name) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Route.Register.name) },
                onLanguageChange = { lang -> mainViewModel.setLanguage(lang) },
                onLoginSuccess = { role ->
                    val target = if (role == "EXPERT") Route.ExpertDashboard.name else Route.PatientDashboard.name
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
                onNavigateToCamera = { navController.navigate(Route.ExerciseSelect.name) },
                onNavigateToProfile = { navController.navigate(Route.Profile.name) },
                onNavigateToGroups = { navController.navigate(Route.Groups.name) },
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
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Login.name) {
                        popUpTo(0) { inclusive = true }
                    }
                }
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
