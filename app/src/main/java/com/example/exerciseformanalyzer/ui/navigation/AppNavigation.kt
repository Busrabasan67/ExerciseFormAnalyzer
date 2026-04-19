package com.example.exerciseformanalyzer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.auth.AuthViewModel
import com.example.exerciseformanalyzer.ui.auth.LoginScreen
import com.example.exerciseformanalyzer.ui.auth.RegisterScreen
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel
import com.example.exerciseformanalyzer.ui.dashboard.ExpertDashboardScreen
import com.example.exerciseformanalyzer.ui.dashboard.PatientDashboardScreen
import com.example.exerciseformanalyzer.ui.history.HistoryScreen
import com.example.exerciseformanalyzer.ui.profile.ProfileScreen

enum class Route {
    Login,
    Register,
    PatientDashboard,
    ExpertDashboard,
    Profile,
    History,
    MainFlow
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel
) {
    val authViewModel: AuthViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Route.Login.name,
        modifier = modifier
    ) {
        composable(Route.Login.name) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Route.Register.name) },
                onLoginSuccess = { role ->
                    // Role based routing
                    val target = if (role == "EXPERT") Route.ExpertDashboard.name else Route.PatientDashboard.name
                    navController.navigate(target) {
                        popUpTo(Route.Login.name) { inclusive = true } // Clear backstack
                    }
                }
            )
        }

        composable(Route.Register.name) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = { role ->
                    val target = if (role == "EXPERT") Route.ExpertDashboard.name else Route.PatientDashboard.name
                    navController.navigate(target) {
                        popUpTo(Route.Register.name) { inclusive = true } // Clear backstack
                        popUpTo(Route.Login.name) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.PatientDashboard.name) {
            PatientDashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToCamera = { navController.navigate(Route.MainFlow.name) },
                onNavigateToProfile = { navController.navigate(Route.Profile.name) },
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.History.name) {
            HistoryScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // This is the actual Exercise Analyzer
        composable(Route.MainFlow.name) {
            com.example.exerciseformanalyzer.ExerciseAnalyzerApp(viewModel = mainViewModel)
        }
    }
}
