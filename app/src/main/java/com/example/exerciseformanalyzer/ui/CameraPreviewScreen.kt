package com.example.exerciseformanalyzer.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.exerciseformanalyzer.camera.CameraManager
import com.example.exerciseformanalyzer.model.ExerciseType

/**
 * Sadece kamera + overlay içerir.
 * Hareket seçimi bu ekrandan önce ExerciseSelectionScreen'de yapılır.
 * [exerciseType]: Seçilen hareket türü (null = otomatik algıla)
 * [onNavigateBack]: Egzersiz bitince dashboard'a geri dön
 */
@Composable
fun CameraPreviewScreen(
    viewModel: MainViewModel,
    exerciseType: ExerciseType? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val poseFrame by viewModel.currentPoseFrame.collectAsState()
    val sessionDurationSec by viewModel.sessionDurationSec.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()

    // Egzersizi başlat (ekran ilk oluştuğunda bir kez)
    LaunchedEffect(exerciseType) {
        viewModel.setTargetExercise(exerciseType)
    }

    // Özet hazır olduğunda göster
    if (workoutSummary != null) {
        WorkoutSummaryScreen(
            summary = workoutSummary!!,
            onRestart = {
                viewModel.resetSession()
                onNavigateBack()
            }
        )
        return
    }

    var cameraManager: CameraManager? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView ->
                    val manager = CameraManager(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        onFrameAvailable = { bitmap, ts -> viewModel.onFrameAvailable(bitmap, ts) },
                        onError = { _ -> }
                    )
                    manager.startCamera(previewView)
                    cameraManager = manager
                    viewModel.onCameraReady()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        ExerciseOverlay(
            uiState = uiState,
            poseFrame = poseFrame,
            sessionDurationSec = sessionDurationSec,
            isPaused = isPaused,
            onPauseToggle = { viewModel.togglePause() },
            onEndWorkout = { viewModel.endWorkout() },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager?.shutdown() }
    }
}
