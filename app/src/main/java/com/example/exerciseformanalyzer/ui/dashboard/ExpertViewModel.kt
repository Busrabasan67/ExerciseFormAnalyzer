package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class ExpertViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val leaderboardRepo = (application as MainApplication).leaderboardRepository

    val currentUid: String get() = authRepo.currentUid ?: ""

    private val _searchResult = MutableStateFlow<FirestoreUser?>(null)
    val searchResult: StateFlow<FirestoreUser?> = _searchResult.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _requestStatus = MutableStateFlow<String?>(null)
    val requestStatus: StateFlow<String?> = _requestStatus.asStateFlow()

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    fun observeCurrentUser(): Flow<UserEntity?> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observeCurrentUser(uid)
    }

    fun observeMyPatients(): Flow<List<UserEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observePatients(uid)
    }

    fun observeTasksByExpert(): Flow<List<TaskAssignmentEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return planRepo.observeTasksByExpert(uid)
    }

    fun syncExpertData() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                userRepo.syncPatientsForExpert(uid)
            }
        }
    }

    fun searchPatient(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _searchError.value = null
            _searchResult.value = null
            try {
                val user = userRepo.findPatientByEmail(email)
                if (user != null) {
                    _searchResult.value = user
                } else {
                    _searchError.value = "Hasta bulunamadı"
                }
            } catch (e: Exception) {
                _searchError.value = e.message
            }
        }
    }

    fun linkPatient(patientUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                val result = userRepo.linkPatientToExpert(patientUid, uid)
                if (result.isSuccess) {
                    _searchResult.value = null
                } else {
                    _searchError.value = "Başarısız: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun sendRequest(patientEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _requestStatus.value = null
            val expertProfile = userRepo.observeCurrentUser(currentUid).first()
            if (expertProfile != null) {
                val result = userRepo.sendConnectionRequest(patientEmail, expertProfile)
                if (result.isSuccess) {
                    _requestStatus.value = "SUCCESS"
                    _searchResult.value = null
                } else {
                    _searchError.value = "İstek gönderilemedi: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun clearRequestStatus() { _requestStatus.value = null }

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
    }

    fun observePatientStats(uid: String): Flow<WorkoutStats> {
        return flow {
            try {
                emit(leaderboardRepo.getPatientStats(uid))
            } catch (e: Exception) {
                emit(WorkoutStats())
            }
        }
    }

    data class TaskExerciseInput(
        var exerciseType: ExerciseType = ExerciseType.SQUAT,
        var isDurationBased: Boolean = false,
        var targetValue: String = "10",
        var sets: Int = 1,
        var restTimeSeconds: Int = 30,
        var difficulty: String = "MEDIUM",
        var category: String = "STRENGTH",
        var videoUrl: String? = null
    )

    fun assignTask(
        patientUid: String, 
        title: String, 
        note: String, 
        dueDate: Long, 
        exercises: List<TaskExerciseInput>,
        scheduleType: String = "DAILY",
        daysOfWeek: List<Int> = emptyList(),
        autoRepeat: Boolean = false,
        repeatWeeks: Int? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                val fsExercises = exercises.map { input ->
                    val value = input.targetValue.toIntOrNull() ?: 10
                    FirestoreExerciseItem(
                        exerciseType = input.exerciseType.name,
                        targetType = if (input.isDurationBased) "DURATION" else "REPS",
                        targetReps = if (!input.isDurationBased) value else null,
                        targetDurationSeconds = if (input.isDurationBased) value else null,
                        sets = input.sets,
                        restTimeSeconds = input.restTimeSeconds,
                        difficulty = input.difficulty,
                        category = input.category,
                        videoUrl = input.videoUrl,
                        status = "PENDING"
                    )
                }

                planRepo.createTaskAssignment(
                    expertUid = uid,
                    patientUid = patientUid,
                    title = title,
                    note = note,
                    dueDate = dueDate,
                    exercises = fsExercises,
                    scheduleType = scheduleType,
                    daysOfWeek = daysOfWeek,
                    autoRepeat = autoRepeat,
                    repeatDurationWeeks = repeatWeeks
                )
            }
        }
    }
}
