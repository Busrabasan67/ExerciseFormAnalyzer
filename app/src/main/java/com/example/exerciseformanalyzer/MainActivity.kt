package com.example.exerciseformanalyzer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.ui.CameraPreviewScreen
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.PermissionScreen
import com.example.exerciseformanalyzer.ui.theme.ExerciseFormAnalyzerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Uygulamanın tek Activity'si — Compose + MVVM mimarisi.
 * AppCompatActivity: per-app locale switching için gerekli.
 *
 * NOT: Dil değişimi artık MainActivity'den değil, MainViewModel.setLanguage()
 * içinden direkt AppCompatDelegate üzerinden yapılıyor. Bu sayede
 * Activity onCreate → collect → setApplicationLocales → recreate döngüsü oluşmuyor.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            ExerciseFormAnalyzerTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.example.exerciseformanalyzer.ui.navigation.AppNavigation(mainViewModel = viewModel)
                }
            }
        }
    }
}
