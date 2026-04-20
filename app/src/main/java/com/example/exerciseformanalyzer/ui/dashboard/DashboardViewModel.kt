package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutPlanEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.data.local.entity.TaskStatus

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val workoutRepo = (application as MainApplication).workoutRepository

    val currentUid: String get() = authRepo.currentUid ?: ""

    fun observeCurrentUser(): Flow<UserEntity?> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observeCurrentUser(uid)
    }

    // Patient Specific
    fun observeMyTasks(): Flow<List<TaskAssignmentEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return planRepo.observeAllTasks(uid)
    }

    fun observeMyReports(): Flow<List<WorkoutReportEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return workoutRepo.observePatientHistory(uid)
    }

    // Expert Specific
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

    private val _searchResult = MutableStateFlow<FirestoreUser?>(null)
    val searchResult: StateFlow<FirestoreUser?> = _searchResult.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

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
                    _searchResult.value = null // reset
                } else {
                    _searchError.value = "Başarısız: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun syncExpertData() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                userRepo.syncPatientsForExpert(uid)
            }
        }
    }

// ExpertDashboardScreen için Helper Sınıf
    data class TaskExerciseInput(
        var exerciseType: ExerciseType = ExerciseType.SQUAT,
        var isDurationBased: Boolean = false,
        var targetValue: String = "10"
    )

    fun assignTask(patientUid: String, title: String, note: String, dueDate: Long, exercises: List<TaskExerciseInput>) {
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
                        status = "PENDING"
                    )
                }

                planRepo.createTaskAssignment(
                    expertUid = uid,
                    patientUid = patientUid,
                    title = title,
                    note = note,
                    dueDate = dueDate,
                    exercises = fsExercises
                )
            }
        }
    }

    fun syncPatientData(expertUid: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            expertUid?.let { userRepo.syncExpertProfileLocally(it) }
            val uid = currentUid
            if (uid.isNotEmpty()) {
                planRepo.syncTasksForPatient(uid)
            }
        }
    }
}
