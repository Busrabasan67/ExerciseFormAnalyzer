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

/**
 * Kamera önizlemesi + pose overlay'i birleştiren Compose composable.
 * AndroidView ile PreviewView (CameraX) embed edilir,
 * üzerine ExerciseOverlay Compose katmanı yerleştirilir.
 *
 * [viewModel]: ViewModel referansı — frame'leri ve state'i buradan alır.
 */
@Composable
fun CameraPreviewScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val poseFrame by viewModel.currentPoseFrame.collectAsState()

    // CameraManager'ın yaşam döngüsünü Compose ile hizala
    var cameraManager: CameraManager? by remember { mutableStateOf(null) }

    var showSelectionDialog by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf<com.example.exerciseformanalyzer.model.ExerciseType?>(null) }
    
    val sessionDurationSec by viewModel.sessionDurationSec.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()

    if (workoutSummary != null) {
        WorkoutSummaryScreen(
            summary = workoutSummary!!,
            onRestart = { viewModel.resetSession(); showSelectionDialog = true }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Kamera önizlemesi — CameraX PreviewView
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
                        onFrameAvailable = { bitmap, ts ->
                            viewModel.onFrameAvailable(bitmap, ts)
                        },
                        onError = { _ -> /* ViewModel'deki error state'i zaten güncelleniyor */ }
                    )
                    manager.startCamera(previewView)
                    cameraManager = manager
                    viewModel.onCameraReady()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay katmanı — skeleton + feedback
        ExerciseOverlay(
            uiState = uiState,
            poseFrame = poseFrame,
            sessionDurationSec = sessionDurationSec,
            isPaused = isPaused,
            onPauseToggle = { viewModel.togglePause() },
            onEndWorkout = { viewModel.endWorkout() },
            modifier = Modifier.fillMaxSize()
        )

        // Hangi hareket yapılacağını soran karşılama modalleri
        if (showSelectionDialog) {
            ExerciseSelectionDialog(
                onExerciseSelected = { exercise ->
                    selectedTarget = exercise
                    showSelectionDialog = false
                    showInfoDialog = true
                },
                onAutoDetectSelected = {
                    viewModel.setTargetExercise(null)
                    showSelectionDialog = false
                }
            )
        }

        if (showInfoDialog && selectedTarget != null) {
            ExerciseInfoDialog(
                exercise = selectedTarget!!,
                onStart = {
                    viewModel.setTargetExercise(selectedTarget)
                    showInfoDialog = false
                },
                onCancel = {
                    selectedTarget = null
                    showInfoDialog = false
                    showSelectionDialog = true
                }
            )
        }
    }

    // Composer ayrıldığında kamera kaynaklarını serbest bırak
    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.shutdown()
        }
    }
}
