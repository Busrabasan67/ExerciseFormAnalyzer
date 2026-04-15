package com.example.exerciseformanalyzer.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication // EKLENDİ
import com.example.exerciseformanalyzer.analysis.AnalysisPipeline
import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.pose.PoseLandmarkerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ana ekranın ViewModel'i — MVVM mimarisinin merkezi.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ── VERİTABANI BAĞLANTISI (YENİ ÖZELLİK) ──────────────────────────────────
    private val workoutRepository = (application as MainApplication).workoutRepository
    // ────────────────────────────────────────────────────────────────────────────

    // ── UI Durumu ────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<ExerciseUiState>(ExerciseUiState.Loading)
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    private val _currentPoseFrame = MutableStateFlow<PoseFrame?>(null)
    val currentPoseFrame: StateFlow<PoseFrame?> = _currentPoseFrame.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Session State ────────────────────────────────────────────────────────
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _sessionDurationSec = MutableStateFlow(0L)
    val sessionDurationSec: StateFlow<Long> = _sessionDurationSec.asStateFlow()

    private val _workoutSummary = MutableStateFlow<WorkoutSummary?>(null)
    val workoutSummary: StateFlow<WorkoutSummary?> = _workoutSummary.asStateFlow()

    private var sessionTimerJob: kotlinx.coroutines.Job? = null

    private var totalAnalysisSamples = 0
    private var correctAnalysisSamples = 0
    private val errorFrequency = mutableMapOf<String, Int>()

    // ── Pipeline ─────────────────────────────────────────────────────────────
    private val analysisPipeline = AnalysisPipeline()
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null

    @Volatile private var lastProcessedTimestamp = -1L

    init {
        initPoseLandmarker()
    }

    private fun initPoseLandmarker() {
        _uiState.value = ExerciseUiState.Loading
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = getApplication(),
            onResult = { poseFrame -> handlePoseResult(poseFrame) },
            onError = { errorMsg ->
                Log.e(TAG, "PoseLandmarker hatası: $errorMsg")
                _error.value = errorMsg
                _uiState.value = ExerciseUiState.Error(errorMsg)
            }
        )
        poseLandmarkerHelper?.setup()
        if (poseLandmarkerHelper?.isReady == true) {
            _uiState.value = ExerciseUiState.Ready
        }
    }

    fun onFrameAvailable(bitmap: Bitmap, timestampMs: Long) {
        if (_isPaused.value || _workoutSummary.value != null || timestampMs <= lastProcessedTimestamp) return
        lastProcessedTimestamp = timestampMs
        poseLandmarkerHelper?.detectAsync(bitmap, timestampMs)
    }

    private fun handlePoseResult(poseFrame: PoseFrame) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (_isPaused.value || _workoutSummary.value != null) return@launch

                _currentPoseFrame.value = poseFrame
                val result = analysisPipeline.process(poseFrame)
                _uiState.value = ExerciseUiState.Analyzing(result)

                if (result.isPersonVisible && result.trackingQuality != TrackingQuality.LOST) {
                    totalAnalysisSamples++
                    if (result.formFeedback.isCorrect) {
                        correctAnalysisSamples++
                    } else if (result.formFeedback.primaryError != null) {
                        val err = result.formFeedback.primaryError
                        errorFrequency[err] = errorFrequency.getOrDefault(err, 0) + 1
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analiz hatası: ${e.message}", e)
            }
        }
    }

    fun onCameraReady() {
        if (_uiState.value is ExerciseUiState.Loading) {
            _uiState.value = ExerciseUiState.Ready
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setTargetExercise(type: ExerciseType?) {
        analysisPipeline.targetExercise = type
        if (type != null) {
            analysisPipeline.reset()
            _uiState.value = ExerciseUiState.Ready

            _isSessionActive.value = true
            _isPaused.value = false
            _sessionDurationSec.value = 0L
            _workoutSummary.value = null
            totalAnalysisSamples = 0
            correctAnalysisSamples = 0
            errorFrequency.clear()

            startTimer()
        }
    }

    private fun startTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (_isSessionActive.value) {
                kotlinx.coroutines.delay(1000)
                if (!_isPaused.value) {
                    _sessionDurationSec.value += 1
                }
            }
        }
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun endWorkout() {
        _isSessionActive.value = false
        sessionTimerJob?.cancel()

        val finalResult = analysisPipeline.lastAnalysisResult
        val exercise = analysisPipeline.targetExercise ?: finalResult?.exerciseType ?: ExerciseType.UNKNOWN
        val reps = finalResult?.repetitionState?.count ?: 0
        val accuracy = if (totalAnalysisSamples > 0) ((correctAnalysisSamples.toFloat() / totalAnalysisSamples) * 100).toInt() else 0
        val commonErr = errorFrequency.maxByOrNull { it.value }?.key

        val summary = WorkoutSummary(
            exercise = exercise,
            totalReps = reps,
            durationSeconds = _sessionDurationSec.value,
            accuracyPercentage = accuracy,
            mostCommonError = commonErr
        )
        _workoutSummary.value = summary

        // ── YENİ ÖZELLİK: VERİTABANINA KAYDET ────────────────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            try {
                workoutRepository.saveWorkoutResult(
                    userId = 1,
                    exerciseId = 1,
                    score = accuracy,
                    reps = reps,
                    time = _sessionDurationSec.value.toInt(), // 'durationSeconds' yerine 'time' yazdık
                    feedback = commonErr ?: "Mükemmel Form"
                )
                Log.d(TAG, "Antrenman başarıyla veritabanına kaydedildi.")
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı kayıt hatası: ${e.message}")
            }
        }
        // ────────────────────────────────────────────────────────────────────
    }

    fun resetSession() {
        analysisPipeline.reset()
        _workoutSummary.value = null
        _isSessionActive.value = false
        _isPaused.value = false
        _uiState.value = ExerciseUiState.Ready
    }

    override fun onCleared() {
        super.onCleared()
        poseLandmarkerHelper?.close()
        poseLandmarkerHelper = null
        Log.d(TAG, "ViewModel temizlendi.")
    }
}

sealed class ExerciseUiState {
    object Loading : ExerciseUiState()
    object Ready : ExerciseUiState()
    data class Analyzing(val result: AnalysisResult) : ExerciseUiState()
    data class Error(val message: String) : ExerciseUiState()
}