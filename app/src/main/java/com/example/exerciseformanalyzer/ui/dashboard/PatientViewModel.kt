package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.data.repository.CommunityRepository
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

// ============================================================
// HASTA EKRANI — Kategorize görev state'i (ExpertViewModel'den bağımsız)
// ============================================================
data class CategorizedTasks(
    val today: List<TaskAssignmentEntity> = emptyList(),
    val inProgress: List<TaskAssignmentEntity> = emptyList(),
    val inactiveToday: List<TaskAssignmentEntity> = emptyList(),
    val completed: List<TaskAssignmentEntity> = emptyList()
)

// ============================================================
// HASTA UI STATE (DoctorTrackingUiState'den tamamen ayrı)
// ============================================================
data class PatientTasksUiState(
    val todayTasks: List<TaskAssignmentEntity> = emptyList(),
    val inProgressTasks: List<TaskAssignmentEntity> = emptyList(),
    val inactiveTodayTasks: List<TaskAssignmentEntity> = emptyList(),
    val completedTasks: List<TaskAssignmentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class PatientViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val workoutRepo = (application as MainApplication).workoutRepository
    private val leaderboardRepo = (application as MainApplication).leaderboardRepository
    private val communityRepo = CommunityRepository((application as MainApplication).communityFirestoreService)

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

    private val _hasCommunityNotifications = MutableStateFlow(false)
    val hasCommunityNotifications: StateFlow<Boolean> = _hasCommunityNotifications.asStateFlow()

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

    fun observeCurrentUser(): Flow<UserEntity?> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observeCurrentUser(uid)
    }

    /**
     * HASTA ANA EKRANI — observeTasksForPatientHome kullanır.
     * doctorId ile ASLA filtrelemez. ExpertViewModel ile state paylaşılmaz.
     *
     * Kategorilendirme mantığı:
     *  - inactive/removed    → inactiveList (hiç gösterilmez)
     *  - period.status == in_progress → progressList
     *  - period.status == completed   → completedList (o periyod için)
     *  - isActiveToday && status aktif → todayList
     *  - diğer              → inactiveList (bugün yok)
     */
    val categorizedTasks: StateFlow<CategorizedTasks> = flow {
        val uid = currentUid
        android.util.Log.d("PatientTasks", "categorizedTasks started: currentPatientId=$uid")
        if (uid.isEmpty()) {
            emit(CategorizedTasks())
            return@flow
        }
        // SADECE patientId ile sorgula — doctorId ile değil
        planRepo.observeTasksForPatientHome(uid).collect { allTasks ->
            android.util.Log.d("PatientTasks", "loaded task count=${allTasks.size}, patientId=$uid")

            val todayList = mutableListOf<TaskAssignmentEntity>()
            val progressList = mutableListOf<TaskAssignmentEntity>()
            val inactiveList = mutableListOf<TaskAssignmentEntity>()
            val completedList = mutableListOf<TaskAssignmentEntity>()

            for (task in allTasks) {
                val status = task.status.orEmpty().lowercase()
                val pKey = getPeriodKey(task.scheduleType)
                val taskIdForProgress = task.firebaseDocId ?: task.id.toString()

                android.util.Log.d(
                    "PatientTasks",
                    "  taskId=${task.id}, doctorId=${task.expertUid}, " +
                    "patientId=${task.patientUid}, status=$status, " +
                    "frequency=${task.scheduleType}, periodKey=$pKey"
                )

                // 1. Pasif/silindi → direkt gösterme
                if (status in listOf("inactive", "removed")) {
                    continue // inactiveList'e bile ekleme
                }

                // 2. Bugün aktif mi?
                val isActiveToday = isTaskActiveToday(task)
                android.util.Log.d("PatientTasks", "    todayActive=$isActiveToday")

                // 3. Periyot ilerlemesini oku (suspend değil, blocking Flow.first)
                val progress = planRepo.getTaskProgress(taskIdForProgress, pKey, uid)
                val progStatus = progress.status.orEmpty().lowercase()

                // EKSTRA GÜVENLİK: progress.status hatalıysa bile set sayılarına bakarak doğrula
                val taskExercises = try { org.json.JSONArray(task.exercisesJson) } catch(e: Exception) { org.json.JSONArray() }
                val progExercises = try { org.json.JSONArray(progress.progressJson) } catch(e: Exception) { org.json.JSONArray() }
                
                var completedExercisesInProg = 0
                for(i in 0 until progExercises.length()) {
                    if(progExercises.getJSONObject(i).optString("status") == "COMPLETED") {
                        completedExercisesInProg++
                    }
                }
                val isReallyCompleted = completedExercisesInProg >= taskExercises.length() && taskExercises.length() > 0

                when {
                    // Gerçekten bittiyse (veya status completed ise)
                    progStatus == "completed" || isReallyCompleted -> {
                        if (isReallyCompleted) completedList.add(task)
                        else progressList.add(task) // Status completed ama aslında bitmemişse in_progress'e çek
                    }

                    // Periyot devam ediyor veya en az bir egzersiz başlanmış
                    progStatus == "in_progress" || progExercises.length() > 0 -> progressList.add(task)

                    // Bugün aktif ve henüz başlanmamış
                    isActiveToday -> todayList.add(task)

                    // Aktif ama bugün günü değil
                    else -> inactiveList.add(task)
                }
            }

            emit(CategorizedTasks(todayList, progressList, inactiveList, completedList))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategorizedTasks())

    fun observeMyReports(): Flow<List<WorkoutReportEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return workoutRepo.observePatientHistory(uid)
    }

    fun observePatientStats(): Flow<WorkoutStats> {
        return combine(
            observeMyReports(),
            planRepo.observeTasksForPatientHome(currentUid)
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
            "PENDING" to tasks.count { it.status.lowercase() in listOf("pending", "active") },
            "IN_PROGRESS" to tasks.count { it.status.lowercase() == "in_progress" },
            "COMPLETED" to tasks.count { it.status.lowercase() in listOf("done", "completed") }
        )

        return WorkoutStats(
            dailyCalories = dailyMap.toList(),
            scoreTrend = scoreTrend,
            completionStats = completionStats
        )
    }

    private val _expertNotes = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreExpertNote>>(emptyList())
    val expertNotes: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreExpertNote>> = _expertNotes.asStateFlow()

    fun syncPatientData(expertUid: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            expertUid?.let { 
                userRepo.syncExpertProfileLocally(it) 
                
                // Uzman notlarını çek
                try {
                    val firestoreService = com.example.exerciseformanalyzer.data.remote.FirestoreService()
                    _expertNotes.value = firestoreService.getExpertNotes(patientId = currentUid, expertId = it)
                } catch (e: Exception) {
                    android.util.Log.e("PatientViewModel", "Notlar çekilirken hata: ${e.message}")
                }
            }
            val uid = currentUid

            if (uid.isNotEmpty()) {
                planRepo.syncTasksForPatient(uid)
                loadDynamicSocialData()
                loadCommunityNotifications(uid)

                try {
                    val user = userRepo.observeCurrentUser(uid).first()
                    if (user != null) {
                        _incomingRequests.value = userRepo.getPendingRequests(user.uid)
                    }
                } catch (e: Exception) { }
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

    fun removeGroupProgramTask(task: TaskAssignmentEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!task.expertUid.startsWith("GROUP:")) {
            onResult(false, "Sadece grup programları silinebilir.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUid
            val taskDocId = task.firebaseDocId.orEmpty()
            val result = if (uid.isNotBlank() && taskDocId.isNotBlank()) {
                communityRepo.removeAppliedProgramForUser(taskDocId, uid)
            } else {
                Result.success(Unit)
            }

            if (result.isSuccess) {
                planRepo.removeTaskFromHome(task.id, task.firebaseDocId)
                syncPatientData(null)
                withContext(Dispatchers.Main) {
                    onResult(true, "Program silindi.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, result.exceptionOrNull()?.message ?: "Program silinemedi.")
                }
            }
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
        taskExerciseCount: Int,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            planRepo.updateExerciseProgress(
                firebaseTaskId = firebaseTaskId,
                patientUid = currentUid,
                exerciseType = exerciseType,
                periodKey = getPeriodKey(scheduleType),
                completedSets = newCompletedSets,
                totalSets = totalSets,
                taskExerciseCount = taskExerciseCount
            )
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /**
     * Görev bugün aktif mi? — DAILY, WEEKLY, CUSTOM kontrolü.
     * Status inactive/removed olan görevler aktif sayılmaz.
     */
    fun isTaskActiveToday(task: TaskAssignmentEntity): Boolean {
        val status = task.status.orEmpty().lowercase()
        if (status in listOf("inactive", "removed")) return false

        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_WEEK)

        return when (task.scheduleType) {
            "DAILY"  -> true
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
     * Hasta tarafında SADECE patientId == currentUid olan görevler için çalışır.
     */
    fun canStartExercise(
        task: TaskAssignmentEntity,
        exerciseType: String,
        progressJson: String,
        onResult: (Boolean, String) -> Unit
    ) {
        // 1. Görev aktif mi?
        val taskStatus = task.status.orEmpty().lowercase()
        if (taskStatus in listOf("inactive", "removed")) {
            onResult(false, "Bu görev artık aktif değil.")
            return
        }
        // 2. Hasta bu görevin sahibi mi?
        if (task.patientUid != currentUid) {
            onResult(false, "Bu görev size ait değil.")
            return
        }
        // 3. Bugün aktif mi?
        if (!isTaskActiveToday(task)) {
            onResult(false, "Bu görev bugün için planlanmamış.")
            return
        }
        // 4. Egzersiz bu periyotta zaten tamamlandı mı?
        try {
            val arr = org.json.JSONArray(progressJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("exerciseType") == exerciseType &&
                    obj.optString("status").lowercase() == "completed") {
                    onResult(false, "Bu egzersiz bu periyotta zaten tamamlandı.")
                    return
                }
            }
        } catch (_: Exception) {}

        // 5. Doktor-hasta ilişkisi kontrolü (async)
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
