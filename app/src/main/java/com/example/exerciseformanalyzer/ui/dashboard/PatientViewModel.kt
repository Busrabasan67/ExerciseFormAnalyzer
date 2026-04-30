package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreActivity
import com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class CategorizedTasks(
    val today: List<TaskAssignmentEntity> = emptyList(),
    val inProgress: List<TaskAssignmentEntity> = emptyList(),
    val inactiveToday: List<TaskAssignmentEntity> = emptyList(),
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

    private val _incomingRequests = MutableStateFlow<List<FirestorePatientRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FirestorePatientRequest>> = _incomingRequests.asStateFlow()

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

    val categorizedTasks: StateFlow<CategorizedTasks> = planRepo.observeAllTasks(currentUid)
        .map { allTasks ->
            val todayList = mutableListOf<TaskAssignmentEntity>()
            val progressList = mutableListOf<TaskAssignmentEntity>()
            val inactiveList = mutableListOf<TaskAssignmentEntity>()
            val completedList = mutableListOf<TaskAssignmentEntity>()

            allTasks.forEach { task ->
                val isActiveToday = isTaskActiveToday(task)
                when {
                    task.status == "DONE" || task.status == "COMPLETED" -> completedList.add(task)
                    task.status == "IN_PROGRESS" -> progressList.add(task)
                    isActiveToday -> todayList.add(task)
                    else -> inactiveList.add(task)
                }
            }
            CategorizedTasks(todayList, progressList, inactiveList, completedList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategorizedTasks())

    fun observeMyReports(): Flow<List<WorkoutReportEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return workoutRepo.observePatientHistory(uid)
    }

    fun observePatientStats(): Flow<WorkoutStats> {
        return combine(
            observeMyReports(),
            planRepo.observeAllTasks(currentUid)
        ) { reports, tasks ->
            calculateStats(reports, tasks)
        }
    }

    private fun calculateStats(reports: List<WorkoutReportEntity>, tasks: List<TaskAssignmentEntity>): WorkoutStats {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyMap = mutableMapOf<String, Float>()
        
        for (i in 6 downTo 0) {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }.time
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
        
        val completionStats = mapOf(
            "PENDING" to tasks.count { it.status == "PENDING" },
            "IN_PROGRESS" to tasks.count { it.status == "IN_PROGRESS" },
            "COMPLETED" to tasks.count { it.status == "DONE" || it.status == "COMPLETED" }
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
            
            if (uid.isNotEmpty()) {
                planRepo.syncTasksForPatient(uid)
                loadDynamicSocialData()

                try {
                    val user = userRepo.observeCurrentUser(uid).first()
                    if (user != null) {
                        _incomingRequests.value = userRepo.getPendingRequests(user.uid)
                    }
                } catch (e: Exception) { }
            }
        }
    }

    fun loadDynamicSocialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _activities.value = leaderboardRepo.getRecentActivities()
                _leaderboard.value = leaderboardRepo.getGlobalLeaderboard()
                _userBadges.value = leaderboardRepo.getUserBadges(currentUid)
            } catch (e: Exception) { }
        }
    }

    fun respondToRequest(request: FirestorePatientRequest, status: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            val result = userRepo.respondToConnectionRequest(
                requestId = request.requestId,
                status = status.uppercase(),
                patientUid = uid,
                expertUid = request.doctorId,
                patientName = request.patientName,
                patientEmail = request.patientEmail
            )
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

    fun getPeriodKey(scheduleType: String): String {
        return planRepo.getPeriodKey(scheduleType)
    }

    fun updateExerciseProgress(
        firebaseTaskId: String,
        exerciseType: String,
        scheduleType: String,
        newCompletedSets: Int,
        totalSets: Int,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            planRepo.updateExerciseProgress(
                firebaseTaskId = firebaseTaskId,
                patientUid = currentUid,
                exerciseType = exerciseType,
                periodKey = getPeriodKey(scheduleType),
                completedSets = newCompletedSets,
                totalSets = totalSets
            )
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun isTaskActiveToday(task: TaskAssignmentEntity): Boolean {
        if (task.status == "inactive") return false
        
        val cal = Calendar.getInstance()
        val today = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        return when (task.scheduleType) {
            "DAILY" -> true
            "WEEKLY" -> true 
            "CUSTOM" -> {
                try {
                    val days = org.json.JSONArray(task.daysOfWeekJson)
                    var found = false
                    for (i in 0 until days.length()) {
                        if (days.getInt(i) == today) found = true
                    }
                    found
                } catch (e: Exception) { false }
            }
            else -> true
        }
    }

    fun checkDoctorPatientRelation(doctorId: String, onResult: (Boolean) -> Unit) {
        if (doctorId == "SYSTEM") {
            onResult(true)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val patientId = currentUid
            val isActive = userRepo.isDoctorPatientRelationActive(doctorId, patientId)
            withContext(Dispatchers.Main) {
                onResult(isActive)
            }
        }
    }

    fun observeTaskProgress(taskId: String, scheduleType: String): Flow<TaskProgressEntity?> {
        return planRepo.observeTaskProgress(taskId, getPeriodKey(scheduleType))
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
                    dueDate = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L),
                    exercises = plan.exercises,
                    scheduleType = "DAILY",
                    daysOfWeek = emptyList(),
                    autoRepeat = true,
                    repeatDurationWeeks = 4
                )
            }
        }
    }

    /**
     * Egzersiz başlatılabilir mi kontrolü.
     * Sonuç callback ile döner — UI bunu Snackbar için kullanır.
     */
    fun canStartExercise(
        task: TaskAssignmentEntity,
        exerciseType: String,
        progressJson: String,
        onResult: (Boolean, String) -> Unit
    ) {
        // 1. Görev aktif mi?
        if (task.status == "inactive" || task.status == "removed") {
            onResult(false, "Bu görev artık aktif değil.")
            return
        }
        // 2. Bugün aktif mi?
        if (!isTaskActiveToday(task)) {
            onResult(false, "Bu görev bugün için planlanmamış.")
            return
        }
        // 3. Egzersiz bu periyotta zaten tamamlandı mı?
        try {
            val arr = org.json.JSONArray(progressJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("exerciseType") == exerciseType &&
                    obj.optString("status") == "completed") {
                    onResult(false, "Bu egzersiz bu periyotta zaten tamamlandı.")
                    return
                }
            }
        } catch (_: Exception) {}

        // 4. Doktor-hasta ilişkisi kontrolü (async)
        if (task.expertUid == "SYSTEM") {
            onResult(true, "")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isActive = userRepo.isDoctorPatientRelationActive(task.expertUid, currentUid)
            withContext(Dispatchers.Main) {
                if (isActive) onResult(true, "")
                else onResult(false, "Uzmanla bağlantınız aktif değil.")
            }
        }
    }
}
