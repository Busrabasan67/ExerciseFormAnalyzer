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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.exerciseformanalyzer.R

/**
 * Ana ekranın ViewModel'i — MVVM mimarisinin merkezi.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ── VERİTABANI BAĞLANTISI ────────────────────────────────────────────────
    private val workoutRepository = (application as MainApplication).workoutRepository

    // ── AKTİF GÖREV BAĞLAMI — sadece görev kaynaklı seanslarda dolu ─────────
    // patientDashboardScreen'den "Başla" butonuyla gelinen egzersizlerde set edilir.
    // Serbest egzersizde (FAB) null kalır.
    data class TaskContext(
        val taskId: Int,
        val exerciseIndex: Int, // exercisesJson array içindeki konum (0-tabanlı)
        val targetType: String, // "REPS" | "DURATION"
        val targetReps: Int,
        val targetDurationSeconds: Int
    )
    private var activeTaskContext: TaskContext? = null
    
    // ── TEMA VE DİL PREFERENCES ──────────────────────────────────────────────────
    private val preferencesRepository = (application as MainApplication).userPreferencesRepository

    val isDarkMode: StateFlow<Boolean> = preferencesRepository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentLanguage: StateFlow<String> = preferencesRepository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "tr")

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setDarkMode(enabled)
        }
    }

    fun setLanguage(langCode: String) {
        // 1. DataStore'a kaydet (tercih kalıcı olsun)
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setLanguage(langCode)
        }
        // 2. Locale'i hemen uygula (Main thread'de — Activity recreation tetikler)
        //    setApplicationLocales sisteme de kaydeder; uygulama sonraki açılışta da bu diyle açılır.
        val newLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(newLocale)
    }
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

    // Tek-ışçi frame kuyruğu — en fazla 8 kare bekletilir, dolunca en eskisi düşürülür
    private val frameChannel = Channel<PoseFrame>(capacity = 8, onUndeliveredElement = null)
    @Volatile private var lastProcessedTimestamp = -1L
    @Volatile private var lastChannelTimestamp = -1L
    // Tekrar sayıcı monotoni garanti—bu değerin altına hiçbir zaman düşülemez
    @Volatile private var maxRepCount = 0

    init {
        initPoseLandmarker()
        startAnalysisProcessor()
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

    /**
     * Tek işçi coroutine: frameChannel'ı FIFO sırasıyla tüketir.
     * Hiçbir eski frame evaluator'ın FSM durumunu bozamaz.
     */
    private fun startAnalysisProcessor() {
        viewModelScope.launch(Dispatchers.Default) {
            frameChannel.consumeEach { poseFrame ->
                try {
                    if (_isPaused.value || _workoutSummary.value != null) return@consumeEach
                    // Kuyrukta beklerken daha yeni bir frame geldiyse bu kareyi atla
                    if (poseFrame.timestampMs < lastProcessedTimestamp) return@consumeEach
                    lastProcessedTimestamp = poseFrame.timestampMs

                    val result = analysisPipeline.process(poseFrame)

                    // Monotoni garantiı: sayaç hiçbir zaman azalmaz
                    val rawCount = result.repetitionState.count
                    val safeCount = maxOf(maxRepCount, rawCount)
                    maxRepCount = safeCount
                    val safeResult = if (safeCount != rawCount) {
                        result.copy(
                            repetitionState = result.repetitionState.copy(count = safeCount)
                        )
                    } else result

                    _currentPoseFrame.value = poseFrame
                    _uiState.value = ExerciseUiState.Analyzing(safeResult)

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
    }

    private fun handlePoseResult(poseFrame: PoseFrame) {
        // Zaten daha yeni bir frame kanalda işlemekteyse bunu gönderme
        if (poseFrame.timestampMs <= lastChannelTimestamp) return
        lastChannelTimestamp = poseFrame.timestampMs
        frameChannel.trySend(poseFrame) // doluysa sessizce atılır (DROP)
    }

    fun onCameraReady() {
        if (_uiState.value is ExerciseUiState.Loading) {
            _uiState.value = ExerciseUiState.Ready
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setTargetExercise(type: ExerciseType?, taskContext: TaskContext? = null) {
        activeTaskContext = taskContext
        analysisPipeline.targetExercise = type
        if (type != null) {
            analysisPipeline.reset()
            maxRepCount = 0  // Yeni seans: sayaç sıfırlanır
            lastProcessedTimestamp = -1L
            lastChannelTimestamp = -1L
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
        val reps = maxRepCount
        val accuracy = if (totalAnalysisSamples > 0) ((correctAnalysisSamples.toFloat() / totalAnalysisSamples) * 100).toInt() else 0
        val commonErr = errorFrequency.maxByOrNull { it.value }?.key

        // ── YENİ ÖZELLİK: VERİTABANI + FİREBASE'E KAYDET ──────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Firebase UID — giriş yoksa boş string (offline kullanım için güvenli)
                val firebaseUid = (getApplication<MainApplication>())
                    .authRepository.currentUid ?: ""
                    
                val userDao = (getApplication<MainApplication>()).database.userDao()
                val user = if (firebaseUid.isNotEmpty()) userDao.getUserByUid(firebaseUid) else null
                
                val localUserId = user?.id ?: 1
                val weightKg = user?.weightKg ?: 70f
                
                // Kalori hesapla
                val calories = com.example.exerciseformanalyzer.domain.CalorieCalculator.calculateForSession(
                    exerciseType = exercise,
                    weightKg = weightKg,
                    durationSeconds = _sessionDurationSec.value
                )

                // UI İçin Özeti Güncelle (Kalori ile)
                val summary = WorkoutSummary(
                    exercise = exercise,
                    totalReps = reps,
                    durationSeconds = _sessionDurationSec.value,
                    accuracyPercentage = accuracy,
                    mostCommonError = commonErr,
                    caloriesBurned = calories
                )
                _workoutSummary.value = summary

                workoutRepository.saveWorkoutResult(
                    userUid = firebaseUid,
                    localUserId = localUserId,
                    exerciseType = exercise,
                    exerciseId = 1,
                    score = accuracy,
                    reps = reps,
                    durationSeconds = _sessionDurationSec.value,
                    feedback = commonErr ?: getApplication<Application>().getString(R.string.perfect_form),
                    taskContext = activeTaskContext
                )
                Log.d(TAG, "Antrenman Room ve Firebase'e kaydedildi (uid=$firebaseUid).")
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı kayıt hatası: ${e.message}")
                // Hata olsa bile basic summary göster
                val summary = WorkoutSummary(
                    exercise = exercise,
                    totalReps = reps,
                    durationSeconds = _sessionDurationSec.value,
                    accuracyPercentage = accuracy,
                    mostCommonError = commonErr,
                    caloriesBurned = 0f
                )
                _workoutSummary.value = summary
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
        activeTaskContext = null
        maxRepCount = 0
        lastProcessedTimestamp = -1L
        lastChannelTimestamp = -1L
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