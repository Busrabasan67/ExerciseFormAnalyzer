package com.example.exerciseformanalyzer.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 *
 * Sorumluluklar:
 * 1. PoseLandmarkerHelper'ı başlatır ve yaşam döngüsünü yönetir
 * 2. CameraX'ten gelen frame'leri MediaPipe'a yönlendirir
 * 3. Pose sonuçlarını AnalysisPipeline'a iletir
 * 4. UI durumunu StateFlow ile yayınlar
 *
 * AndroidViewModel kullanılır çünkü MediaPipe için Context gereklidir.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ── UI Durumu ────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<ExerciseUiState>(ExerciseUiState.Loading)
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    // Landmark overlay için kullanılacak PoseFrame
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
    
    // Summary Stats
    private var totalAnalysisSamples = 0
    private var correctAnalysisSamples = 0
    private val errorFrequency = mutableMapOf<String, Int>()

    // ── Pipeline ─────────────────────────────────────────────────────────────

    private val analysisPipeline = AnalysisPipeline()

    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null

    // Son kareye ait timestamp — backpressure için kontrol
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

    /**
     * CameraX'ten gelen her frame buradan geçer.
     */
    fun onFrameAvailable(bitmap: Bitmap, timestampMs: Long) {
        if (_isPaused.value || _workoutSummary.value != null || timestampMs <= lastProcessedTimestamp) return
        lastProcessedTimestamp = timestampMs
        poseLandmarkerHelper?.detectAsync(bitmap, timestampMs)
    }

    /**
     * MediaPipe sonucu geldiğinde çağrılır (MediaPipe callback thread'i).
     * Coroutine ile Default dispatcher'a geçerek analiz yapılır.
     */
    private fun handlePoseResult(poseFrame: PoseFrame) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (_isPaused.value || _workoutSummary.value != null) return@launch
                
                _currentPoseFrame.value = poseFrame
                val result = analysisPipeline.process(poseFrame)
                _uiState.value = ExerciseUiState.Analyzing(result)
                
                // İstatistikleri topla (Kişi ekrandaysa ve model aktif analiz yapıyorsa)
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

    /** PoseLandmarker hazır olduğunda kameraya izin verildiğini bildirir. */
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
            analysisPipeline.targetExercise = type 
            _uiState.value = ExerciseUiState.Ready
            
            // Session'ı başlat
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
        
        // Özeti hesapla
        val finalResult = analysisPipeline.lastAnalysisResult
        val exercise = analysisPipeline.targetExercise ?: finalResult?.exerciseType ?: ExerciseType.UNKNOWN
        val reps = finalResult?.repetitionState?.count ?: 0
        val accuracy = if (totalAnalysisSamples > 0) ((correctAnalysisSamples.toFloat() / totalAnalysisSamples) * 100).toInt() else 0
        val commonErr = errorFrequency.maxByOrNull { it.value }?.key
        
        _workoutSummary.value = WorkoutSummary(
            exercise = exercise,
            totalReps = reps,
            durationSeconds = _sessionDurationSec.value,
            accuracyPercentage = accuracy,
            mostCommonError = commonErr
        )
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

/**
 * UI'ın tüm olası durumları — sealed class ile tip güvenli state yönetimi.
 */
sealed class ExerciseUiState {
    /** Uygulama başlıyor veya model yükleniyor */
    object Loading : ExerciseUiState()

    /** Kamera ve model hazır, egzersiz bekleniyor */
    object Ready : ExerciseUiState()

    /** Aktif analiz yapılıyor */
    data class Analyzing(val result: AnalysisResult) : ExerciseUiState()

    /** Hata durumu */
    data class Error(val message: String) : ExerciseUiState()
}
