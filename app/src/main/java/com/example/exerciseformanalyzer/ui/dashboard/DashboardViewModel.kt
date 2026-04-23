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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.data.local.entity.TaskStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.AdminSystemStats

enum class AdminPanelType { ADMIN, PATIENT, EXPERT }

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val workoutRepo = (application as MainApplication).workoutRepository
    private val leaderboardRepo = (application as MainApplication).leaderboardRepository
    private val adminRepo = (application as MainApplication).adminRepository

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

    private val _requestStatus = MutableStateFlow<String?>(null) // "SUCCESS", "ERROR", null
    val requestStatus: StateFlow<String?> = _requestStatus.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreConnectionRequest>>>(emptyList())
    val incomingRequests: StateFlow<List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreConnectionRequest>>> = _incomingRequests.asStateFlow()

    // --- FAZ 4: SOSYAL VE OYUNLAŞTIRMA VERİLERİ ---
    private val _activities = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreActivity>>(emptyList())
    val activities: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreActivity>> = _activities.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreUser>>(emptyList())
    val leaderboard: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreUser>> = _leaderboard.asStateFlow()

    private val _userBadges = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress>>(emptyList())
    val userBadges: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress>> = _userBadges.asStateFlow()

    // ADMIN ÖZEL
    private val _selectedAdminPanel = MutableStateFlow(AdminPanelType.ADMIN)
    val selectedAdminPanel: StateFlow<AdminPanelType> = _selectedAdminPanel.asStateFlow()

    private val _adminSystemStats = MutableStateFlow(AdminSystemStats())
    val adminSystemStats: StateFlow<AdminSystemStats> = _adminSystemStats.asStateFlow()

    private val _allUsers = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val allUsers: StateFlow<List<FirestoreUser>> = _allUsers.asStateFlow()

    // --- REAKTİF İSTATİSTİKLER (Lokal Room Verisinden) ---
    val patientStats: StateFlow<WorkoutStats> = combine(
        observeMyReports(),
        observeMyTasks()
    ) { reports, tasks ->
        calculateStats(reports, tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutStats())

    val isEmailVerified = authRepo.isEmailVerified

    private val firestoreService = (application as MainApplication).firestoreService

    fun clearRequestStatus() { _requestStatus.value = null }

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
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
                    _searchResult.value = null // reset
                } else {
                    _searchError.value = "Başarısız: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    /** Faz 1: Bağlantı isteği gönder */
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
        var targetValue: String = "10",
        var sets: Int = 1,
        var restTimeSeconds: Int = 30,
        var difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
        var category: String = "STRENGTH", // REHAB, STRENGTH, CARDIO
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

    fun syncPatientData(expertUid: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            expertUid?.let { userRepo.syncExpertProfileLocally(it) }
            val uid = currentUid
            
            // İstatistikler artık reaktif olarak observeMyReports/observeMyTasks üzerinden otomatik çekiliyor.
            
            val user = userRepo.observeCurrentUser(uid).first()
            if (user != null) {
                _incomingRequests.value = userRepo.getPendingRequests(user.email)
                planRepo.syncTasksForPatient(uid)
                loadDynamicSocialData()
            }
        }
    }

    fun loadDynamicSocialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _activities.value = firestoreService.getRecentActivities()
                _leaderboard.value = firestoreService.getGlobalLeaderboard()
                _userBadges.value = firestoreService.getUserBadges(currentUid)
            } catch (e: Exception) {
                // Hata durumunda boş liste kalır
            }
        }
    }

    fun respondToRequest(requestId: String, status: String, expertUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            val result = userRepo.respondToConnectionRequest(requestId, status, uid, expertUid)
            if (result.isSuccess) {
                syncPatientData(null) // Refresh
            }
        }
    }

    // --- FAZ 3: İSTATİSTİK VE GRAFİK MANTIĞI ---

    fun observePatientStats(uid: String): Flow<WorkoutStats> {
        // Eğer kendi stats'ını istiyorsa mevcut reaktif akışı ver
        if (uid == currentUid) return patientStats
        
        // Başka bir hasta (Uzman görüşü) ise Firestore'dan çekmeye devam et
        return kotlinx.coroutines.flow.flow {
            try {
                emit(leaderboardRepo.getPatientStats(uid))
            } catch (e: Exception) {
                emit(WorkoutStats())
            }
        }
    }

    private fun calculateStats(reports: List<WorkoutReportEntity>, tasks: List<TaskAssignmentEntity>): WorkoutStats {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyMap = mutableMapOf<String, Float>()
        
        // Son 7 günü 0 ile ilklendir
        for (i in 6 downTo 0) {
            val date = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -i) }.time
            dailyMap[sdf.format(date)] = 0f
        }
        
        // Raporları topla
        reports.forEach { report ->
            val dayKey = sdf.format(Date(report.timestamp))
            if (dailyMap.containsKey(dayKey)) {
                dailyMap[dayKey] = (dailyMap[dayKey] ?: 0f) + report.caloriesBurned
            }
        }
        
        val scoreTrend = reports.take(20).reversed().mapIndexed { index, report ->
            index.toFloat() to report.score.toFloat()
        }
        
        val completionStats = tasks.groupBy { it.status }.mapValues { it.value.size }
        
        return WorkoutStats(
            dailyCalories = dailyMap.toList(),
            scoreTrend = scoreTrend,
            completionStats = completionStats
        )
    }

    // =====================================================================
    // ADMIN FONKSİYONLARI
    // =====================================================================

    fun setAdminPanel(panel: AdminPanelType) {
        _selectedAdminPanel.value = panel
    }

    fun fetchAdminStats() {
        viewModelScope.launch {
            _adminSystemStats.value = adminRepo.getSystemStats()
            fetchAllUsers()
        }
    }

    fun fetchAllUsers() {
        viewModelScope.launch {
            _allUsers.value = adminRepo.getAllUsers()
        }
    }
}
