package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.repository.CommunityRepository
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest
import java.util.Date

enum class TaskFilter { ALL, PENDING, IN_PROGRESS, COMPLETED, INACTIVE }

// ============================================================
// UZMAN TAKİP — UI State
// ============================================================
data class DoctorTrackingUiState(
    val allTasks: List<TaskAssignmentEntity> = emptyList(),
    val filteredTasks: List<TaskAssignmentEntity> = emptyList(),
    val selectedFilter: TaskFilter = TaskFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ExpertViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val leaderboardRepo = (application as MainApplication).leaderboardRepository
    private val communityRepo = CommunityRepository((application as MainApplication).communityFirestoreService)
    private val firestoreService = com.example.exerciseformanalyzer.data.remote.FirestoreService()

    val currentUid: String get() = authRepo.currentUid ?: ""

    private val _searchResults = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val searchResults: StateFlow<List<FirestoreUser>> = _searchResults.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<FirestorePatientRequest>>(emptyList())
    val sentRequests: StateFlow<List<FirestorePatientRequest>> = _sentRequests.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _requestStatus = MutableStateFlow<String?>(null)
    val requestStatus: StateFlow<String?> = _requestStatus.asStateFlow()

    private var searchJob: Job? = null

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    private val _hasCommunityNotifications = MutableStateFlow(false)
    val hasCommunityNotifications: StateFlow<Boolean> = _hasCommunityNotifications.asStateFlow()

    private val _relationshipNotifications =
        MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreRelationshipNotification>>(emptyList())
    val relationshipNotifications: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreRelationshipNotification>> =
        _relationshipNotifications.asStateFlow()

    private val _unreadChatPartnerIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadChatPartnerIds: StateFlow<Set<String>> = _unreadChatPartnerIds.asStateFlow()

    private var relationshipNotificationsJob: Job? = null
    private var unreadChatJob: Job? = null
    private var sentRequestsJob: Job? = null
    private var tasksJob: Job? = null

    private val _isEmailVerified = MutableStateFlow(authRepo.isEmailVerified)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    fun sendVerificationEmail() {
        viewModelScope.launch {
            authRepo.sendEmailVerification()
        }
    }

    fun reloadUser(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            authRepo.reloadUser()
            val verified = authRepo.isEmailVerified
            _isEmailVerified.value = verified
            android.util.Log.d("Verification", "User reloaded. isEmailVerified: $verified")
            onComplete(verified)
        }
    }

    // ── Uzman Takip Filtresi — PatientViewModel'den TAMAMEN BAĞIMSIZ ──
    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    /**
     * UZMAN TAKİP EKRANI — kendi atadığı tüm görevler.
     * Flow, UID'nin hazır olduğu anda (abone olunduğunda) başlar.
     * PatientViewModel ile hiçbir state paylaşılmaz.
     */
    private fun doctorTasksFlow(): Flow<List<TaskAssignmentEntity>> = flow {
        val uid = currentUid
        android.util.Log.d("DoctorTracking", "doctorTasksFlow started: currentDoctorId=$uid")
        if (uid.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        planRepo.observeTasksForDoctorTracking(uid).collect { tasks ->
            android.util.Log.d("DoctorTracking", "loaded task count=${tasks.size}, doctorId=$uid")
            tasks.forEach { task ->
                android.util.Log.d(
                    "DoctorTracking",
                    "  taskId=${task.id}, doctorId=${task.expertUid}, " +
                    "patientId=${task.patientUid}, status=${task.status}"
                )
            }
            emit(tasks.map { if (it.status.isEmpty()) it.copy(status = "PENDING") else it })
        }
    }

    val filteredTasks: StateFlow<List<TaskAssignmentEntity>> = combine(
        doctorTasksFlow(),
        _selectedFilter
    ) { tasks, filter ->
        val sortedTasks = tasks.sortedByDescending { it.createdAt }
        android.util.Log.d("DoctorTracking", "filteredTasks: all=${tasks.size}, filter=$filter")
        when (filter) {
            TaskFilter.ALL -> sortedTasks
            TaskFilter.PENDING -> sortedTasks.filter {
                it.status.lowercase() in listOf("pending", "active")
            }
            TaskFilter.IN_PROGRESS -> sortedTasks.filter {
                it.status.lowercase() == "in_progress"
            }
            TaskFilter.COMPLETED -> sortedTasks.filter {
                it.status.lowercase() in listOf("completed", "done")
            }
            TaskFilter.INACTIVE -> sortedTasks.filter {
                it.status.lowercase() in listOf("inactive", "removed")
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Eski yöntem — geriye dönük uyumluluk için korundu
    fun observeTasksByExpert(): Flow<List<TaskAssignmentEntity>> = doctorTasksFlow()

    fun syncExpertData() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                startRealtimeObservers(uid)
                userRepo.syncPatientsForExpert(uid)
                
                tasksJob?.cancel()
                tasksJob = viewModelScope.launch(Dispatchers.IO) {
                    planRepo.observeAndSyncTasksForExpert(uid).collect {}
                }
                
                loadSentRequests()
                loadCommunityNotifications(uid)
            }
        }
    }

    private fun startRealtimeObservers(uid: String) {
        relationshipNotificationsJob?.cancel()
        relationshipNotificationsJob = viewModelScope.launch(Dispatchers.IO) {
            userRepo.observeRelationshipNotifications(uid).collect { notifications ->
                _relationshipNotifications.value = notifications
            }
        }

        unreadChatJob?.cancel()
        unreadChatJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                firestoreService.observeUnreadChatPartnerIds(uid).collect { partnerIds ->
                    _unreadChatPartnerIds.value = partnerIds
                }
            }.onFailure { e ->
                android.util.Log.w("ExpertViewModel", "unreadChatJob hatası (Firestore index gerekiyor olabilir): ${e.message}")
                _unreadChatPartnerIds.value = emptySet()
            }
        }
    }

    fun refreshCommunityNotifications() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            loadCommunityNotifications(uid)
        }
    }

    private suspend fun loadCommunityNotifications(uid: String) {
        _hasCommunityNotifications.value = runCatching {
            communityRepo.hasCommunityNotifications(uid)
        }.getOrDefault(false)
    }

    private fun loadSentRequests() {
        sentRequestsJob?.cancel()
        sentRequestsJob = viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                userRepo.observeSentRequestsByDoctor(uid).collect { reqs ->
                    _sentRequests.value = reqs
                }
            }
        }
    }

    fun searchPatients(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            _searchError.value = null
            val cleanQuery = query.trim().lowercase()
            try {
                val connectedPatients = observeMyPatients().first().map { it.uid }
                val results = userRepo.searchPatientsByEmail(cleanQuery)
                    .filter { it.role == "PATIENT" && it.uid != currentUid && it.uid !in connectedPatients }
                _searchResults.value = results
            } catch (e: Exception) {
                _searchError.value = e.message
            }
        }
    }

    fun sendConnectionRequest(patient: FirestoreUser) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isEmpty()) return@launch

            val doctorProfile = userRepo.observeCurrentUser(uid).first()
            if (doctorProfile != null) {
                val result = userRepo.sendConnectionRequest(patient, doctorProfile)
                if (result.isSuccess) {
                    _requestStatus.value = getApplication<MainApplication>().getString(R.string.ui_request_sent)
                    loadSentRequests()
                } else {
                    val message = result.exceptionOrNull()?.message ?: getApplication<MainApplication>().getString(R.string.unknown_error)
                    _searchError.value = getApplication<MainApplication>().getString(R.string.ui_request_send_failed, message)
                }
            }
        }
    }

    fun cancelConnectionRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.cancelConnectionRequest(requestId)
            if (result.isSuccess) {
                _requestStatus.value = getApplication<MainApplication>().getString(R.string.ui_request_cancelled)
                loadSentRequests()
            } else {
                val message = result.exceptionOrNull()?.message ?: getApplication<MainApplication>().getString(R.string.unknown_error)
                _searchError.value = getApplication<MainApplication>().getString(R.string.ui_request_cancel_failed, message)
            }
        }
    }

    fun removePatient(patientId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isEmpty()) return@launch

            val result = userRepo.removePatientFromExpert(patientId, uid)
            if (result.isSuccess) {
                planRepo.deactivateDoctorTasks(uid, patientId)
                _requestStatus.value = "REMOVED"
            } else {
                val message = result.exceptionOrNull()?.message ?: getApplication<MainApplication>().getString(R.string.unknown_error)
                _searchError.value = getApplication<MainApplication>().getString(R.string.ui_patient_remove_failed, message)
            }
        }
    }

    fun clearRequestStatus() { _requestStatus.value = null }

    fun dismissRelationshipNotification(notificationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.dismissRelationshipNotification(notificationId)
        }
    }

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
    }

    private val _expertNotes = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreExpertNote>>(emptyList())
    val expertNotes: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreExpertNote>> = _expertNotes.asStateFlow()

    fun loadExpertNotes(patientId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _expertNotes.value = firestoreService.getExpertNotes(patientId, currentUid)
            } catch (e: Exception) {
                android.util.Log.e("ExpertViewModel", "Notlar yüklenirken hata: ${e.message}")
            }
        }
    }

    fun addExpertNote(patientId: String, noteText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val note = com.example.exerciseformanalyzer.model.firestore.FirestoreExpertNote(
                    expertId = currentUid,
                    patientId = patientId,
                    note = noteText,
                    createdAt = java.util.Date()
                )
                firestoreService.addExpertNote(note)
                loadExpertNotes(patientId) // Yeniden yükle
            } catch (e: Exception) {
                android.util.Log.e("ExpertViewModel", "Not eklenirken hata: ${e.message}")
            }
        }
    }

    fun observePatientStats(uid: String, startDate: Date? = null, endDate: Date? = null): Flow<WorkoutStats> {
        return flow {
            try {
                emit(leaderboardRepo.getPatientStats(uid, startDate, endDate))
            } catch (e: Exception) {
                emit(WorkoutStats())
            }
        }
    }

    private val _detailedAnalysis = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val detailedAnalysis: StateFlow<Map<String, Any?>> = _detailedAnalysis.asStateFlow()

    fun loadDetailedAnalysis(patientId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _detailedAnalysis.value = firestoreService.getPatientDetailedAnalysis(patientId)
            } catch (e: Exception) {
                android.util.Log.e("ExpertViewModel", "Detaylı analiz hatası: ${e.message}")
            }
        }
    }

    fun updateTaskNote(taskId: String, status: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestoreService.updateTaskStatusWithNote(taskId, status, note)
                // Görevleri yenilemek için sync tetiklenebilir
            } catch (e: Exception) {
                android.util.Log.e("ExpertViewModel", "Görev notu güncelleme hatası: ${e.message}")
            }
        }
    }

    data class TaskExerciseInput(
        var exerciseType: ExerciseType = ExerciseType.SQUAT,
        var isDurationBased: Boolean = false,
        var targetValue: String = "10",
        var sets: String = "1",
        var restTimeSeconds: String = "",
        var difficulty: String = "MEDIUM",
        var category: String = "STRENGTH",
        var videoUrl: String? = null
    )

    fun assignTask(
        patientUid: String,
        patientName: String,
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
                    val value = input.targetValue.toIntOrNull() ?: 1
                    val s = input.sets.toIntOrNull() ?: 1
                    val rt = input.restTimeSeconds.toIntOrNull()
                    
                    FirestoreExerciseItem(
                        exerciseType = input.exerciseType.name,
                        targetType = if (input.isDurationBased) "DURATION" else "REPS",
                        targetReps = if (!input.isDurationBased) value else null,
                        targetDurationSeconds = if (input.isDurationBased) value else null,
                        sets = s,
                        restTimeSeconds = rt,
                        difficulty = input.difficulty,
                        category = input.category,
                        videoUrl = input.videoUrl,
                        status = "PENDING"
                    )
                }

                planRepo.createTaskAssignment(
                    expertUid = uid,
                    patientUid = patientUid,
                    patientName = patientName,
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

    fun deleteTask(taskId: Int, firebaseDocId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            planRepo.deleteTaskAssignment(taskId, firebaseDocId)
        }
    }

    fun updateTask(
        taskId: Int,
        firebaseDocId: String?,
        patientUid: String,
        patientName: String,
        title: String,
        note: String,
        dueDate: Long,
        exercises: List<TaskExerciseInput>,
        scheduleType: String,
        daysOfWeek: List<Int>,
        autoRepeat: Boolean,
        repeatWeeks: Int?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                val fsExercises = exercises.map { input ->
                    val value = input.targetValue.toIntOrNull() ?: 1
                    val s = input.sets.toIntOrNull() ?: 1
                    val rt = input.restTimeSeconds.toIntOrNull()
                    
                    FirestoreExerciseItem(
                        exerciseType = input.exerciseType.name,
                        targetType = if (input.isDurationBased) "DURATION" else "REPS",
                        targetReps = if (!input.isDurationBased) value else null,
                        targetDurationSeconds = if (input.isDurationBased) value else null,
                        sets = s,
                        restTimeSeconds = rt,
                        difficulty = input.difficulty,
                        category = input.category,
                        videoUrl = input.videoUrl,
                        status = "PENDING"
                    )
                }

                planRepo.updateTaskAssignment(
                    taskId = taskId,
                    firebaseDocId = firebaseDocId,
                    expertUid = uid,
                    patientUid = patientUid,
                    patientName = patientName,
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
