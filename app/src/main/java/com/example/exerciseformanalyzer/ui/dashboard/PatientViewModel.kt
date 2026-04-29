package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreActivity
import com.example.exerciseformanalyzer.model.firestore.FirestoreConnectionRequest
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CategorizedTasks(
    val pending: List<TaskAssignmentEntity> = emptyList(),
    val ongoing: List<TaskAssignmentEntity> = emptyList(),
    val completed: List<TaskAssignmentEntity> = emptyList()
)

class PatientViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val workoutRepo = (application as MainApplication).workoutRepository
    private val leaderboardRepo = (application as MainApplication).leaderboardRepository

    val currentUid: String get() = authRepo.currentUid ?: ""

    private val _requestStatus = MutableStateFlow<String?>(null)
    val requestStatus: StateFlow<String?> = _requestStatus.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<Pair<String, FirestoreConnectionRequest>>>(emptyList())
    val incomingRequests: StateFlow<List<Pair<String, FirestoreConnectionRequest>>> = _incomingRequests.asStateFlow()

    private val _activities = MutableStateFlow<List<FirestoreActivity>>(emptyList())
    val activities: StateFlow<List<FirestoreActivity>> = _activities.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val leaderboard: StateFlow<List<FirestoreUser>> = _leaderboard.asStateFlow()

    private val _userBadges = MutableStateFlow<List<FirestoreUserBadgeProgress>>(emptyList())
    val userBadges: StateFlow<List<FirestoreUserBadgeProgress>> = _userBadges.asStateFlow()

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    val isEmailVerified = authRepo.isEmailVerified

    fun observeCurrentUser(): Flow<UserEntity?> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observeCurrentUser(uid)
    }

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

    val patientStats: StateFlow<WorkoutStats> = combine(
        observeMyReports(),
        observeMyTasks()
    ) { reports, tasks ->
        calculateStats(reports, tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutStats())

    val categorizedTasks: StateFlow<CategorizedTasks> = observeMyTasks()
        .combine(MutableStateFlow(Unit)) { tasks, _ ->
            categorizeTasks(tasks)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategorizedTasks())

    private fun categorizeTasks(tasks: List<TaskAssignmentEntity>): CategorizedTasks {
        val pending = mutableListOf<TaskAssignmentEntity>()
        val ongoing = mutableListOf<TaskAssignmentEntity>()
        val completed = mutableListOf<TaskAssignmentEntity>()

        tasks.forEach { task ->
            try {
                val arr = org.json.JSONArray(task.exercisesJson)
                var anyProgress = false
                var allDone = true

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val c = obj.optInt("completedSets", 0)
                    val t = obj.optInt("sets", 1)
                    if (c > 0) anyProgress = true
                    if (c < t) allDone = false
                }

                when {
                    allDone || task.status == "COMPLETED" -> completed.add(task)
                    anyProgress -> ongoing.add(task)
                    else -> pending.add(task)
                }
            } catch (e: Exception) {
                when (task.status) {
                    "COMPLETED" -> completed.add(task)
                    "IN_PROGRESS" -> ongoing.add(task)
                    else -> pending.add(task)
                }
            }
        }
        return CategorizedTasks(pending, ongoing, completed)
    }

    private fun calculateStats(reports: List<WorkoutReportEntity>, tasks: List<TaskAssignmentEntity>): WorkoutStats {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyMap = mutableMapOf<String, Float>()
        
        for (i in 6 downTo 0) {
            val date = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -i) }.time
            dailyMap[sdf.format(date)] = 0f
        }
        
        reports.forEach { report ->
            val dayKey = sdf.format(Date(report.timestamp))
            if (dailyMap.containsKey(dayKey)) {
                dailyMap[dayKey] = (dailyMap[dayKey] ?: 0f) + report.caloriesBurned
            }
        }
        
        val scoreTrend = reports.take(20).reversed().mapIndexed { index, report ->
            index.toFloat() to report.score.toFloat()
        }
        
        val cat = categorizeTasks(tasks)
        val completionStats = mapOf(
            "PENDING" to cat.pending.size,
            "IN_PROGRESS" to cat.ongoing.size,
            "COMPLETED" to cat.completed.size
        )
        
        return WorkoutStats(
            dailyCalories = dailyMap.toList(),
            scoreTrend = scoreTrend,
            completionStats = completionStats
        )
    }

    fun syncPatientData(expertUid: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            expertUid?.let { userRepo.syncExpertProfileLocally(it) }
            val uid = currentUid
            
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
                _activities.value = leaderboardRepo.getRecentActivities()
                _leaderboard.value = leaderboardRepo.getGlobalLeaderboard()
                _userBadges.value = leaderboardRepo.getUserBadges(currentUid)
            } catch (e: Exception) {
            }
        }
    }

    fun respondToRequest(requestId: String, status: String, expertUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            val result = userRepo.respondToConnectionRequest(requestId, status, uid, expertUid)
            if (result.isSuccess) {
                syncPatientData(null)
            }
        }
    }

    fun clearRequestStatus() { _requestStatus.value = null }

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
    }

    fun updateProfile(updatedUser: UserEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.updateProfile(updatedUser.uid, updatedUser)
            onResult(result.isSuccess)
        }
    }

    fun applyRecommendedPlan(plan: com.example.exerciseformanalyzer.domain.RecommendedPlan) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                planRepo.createTaskAssignment(
                    expertUid = "SYSTEM",
                    patientUid = uid,
                    title = plan.title,
                    note = plan.note,
                    dueDate = System.currentTimeMillis() + 86400000,
                    exercises = plan.exercises,
                    scheduleType = "DAILY",
                    daysOfWeek = emptyList(),
                    autoRepeat = false,
                    repeatDurationWeeks = null
                )
            }
        }
    }
}
