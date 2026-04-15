package com.example.exerciseformanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.exerciseformanalyzer.ui.CameraPreviewScreen
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.PermissionScreen
import com.example.exerciseformanalyzer.ui.theme.ExerciseFormAnalyzerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Uygulamanın tek Activity'si — Compose + MVVM mimarisi.
 *
 * Sorumlulukları:
 * 1. Edge-to-edge tam ekran ayarı
 * 2. Kamera izin akışı (Accompanist Permissions)
 * 3. ViewModel bağlantısı
 * 4. Compose içerik ayarı
 */
class MainActivity : ComponentActivity() {

    // ViewModel Activity yaşam döngüsüne bağlı
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ExerciseFormAnalyzerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExerciseAnalyzerApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ExerciseAnalyzerApp(viewModel: MainViewModel) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        // İzin verildi — kamera ekranına geç
        CameraPreviewScreen(viewModel = viewModel)
    } else {
        // İzin isteme ekranı
        PermissionScreen(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
        )
    }
}
