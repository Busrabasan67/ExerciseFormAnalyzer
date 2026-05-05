package com.example.exerciseformanalyzer.data.repository

// PlanRepository — Uzmanların hastalara atadığı antrenman planları ve görevleri yönetir.
//
// Offline-first yaklaşımı:
//   - Uzman görev atarken önce lokal db (Room) -> sonra Firestore
//   - Hasta Dashboard'unda okurken direkt Room (Flow üzerinden dinlenir)
//   - Görev tamamlandığında status güncellenir

import android.util.Log
import com.example.exerciseformanalyzer.data.local.dao.TaskAssignmentDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutPlanDao
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.TaskStatus
import com.example.exerciseformanalyzer.data.local.entity.WorkoutPlanEntity
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.domain.repository.IPlanRepository
import com.example.exerciseformanalyzer.model.firestore.FirestoreTaskAssignment
import kotlinx.coroutines.flow.Flow

class PlanRepository(
    private val planDao: WorkoutPlanDao,
    private val taskDao: TaskAssignmentDao,
    private val progressDao: com.example.exerciseformanalyzer.data.local.dao.TaskProgressDao,
    private val firestoreService: FirestoreService
) : IPlanRepository {

    private val TAG = "PlanRepository"

    /**
     * Hastanın aktif olan bekleyen görevlerini UI için reaktif olarak izler.
     */
    override fun observePendingTasks(patientUid: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observePendingTasksForPatient(patientUid)
    }

    /**
     * Hastanın tüm görev geçmişini izler (tamamlananlar ve kaçırılanlar dahil).
     */
    override fun observeAllTasks(patientUid: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observeAllTasksForPatient(patientUid)
    }

    override fun observeTasksByExpert(expertUid: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observeTasksByExpert(expertUid)
    }

    /** UZMAN TAKİP EKRANI — doctorId eşleşen tüm görevleri döner, filtre yok. */
    override fun observeTasksForDoctorTracking(doctorId: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observeTasksByExpert(doctorId)
    }

    /** HASTA ANA EKRANI — patientId eşleşen tüm görevleri döner. doctorId kontrolü yok. */
    override fun observeTasksForPatientHome(patientId: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observeAllTasksForPatient(patientId)
    }

    /**
     * Uzman tarafından yeni bir antrenman planı oluşturulur.
     * İçerisinde birden fazla görev (Task) olabilir.
     * Önce Room'a yazılır, internet varsa anında Firestore'a itilir.
     */
    override suspend fun createTaskAssignment(
        expertUid: String,
        patientUid: String,
        title: String,
        note: String,
        dueDate: Long,
        exercises: List<com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem>,
        scheduleType: String,
        daysOfWeek: List<Int>,
        autoRepeat: Boolean,
        repeatDurationWeeks: Int?
    ): Result<Unit> {
        return try {
            // JSON stringine çevir
            val jsonArray = org.json.JSONArray()
            for (ex in exercises) {
                val obj = org.json.JSONObject()
                obj.put("exerciseType", ex.exerciseType)
                obj.put("targetType", ex.targetType)
                if (ex.targetReps != null) obj.put("targetReps", ex.targetReps)
                if (ex.targetDurationSeconds != null) obj.put("targetDurationSeconds", ex.targetDurationSeconds)
                if (ex.actualReps != null) obj.put("actualReps", ex.actualReps)
                if (ex.actualDurationSeconds != null) obj.put("actualDurationSeconds", ex.actualDurationSeconds)
                obj.put("sets", ex.sets)
                obj.put("completedSets", ex.completedSets)
                obj.put("restTimeSeconds", ex.restTimeSeconds)
                obj.put("difficulty", ex.difficulty)
                obj.put("category", ex.category)
                if (ex.videoUrl != null) obj.put("videoUrl", ex.videoUrl)
                obj.put("status", ex.status)
                jsonArray.put(obj)
            }
            val exercisesJson = jsonArray.toString()
            val daysOfWeekJson = org.json.JSONArray(daysOfWeek).toString()

            val now = System.currentTimeMillis()
            val localTask = TaskAssignmentEntity(
                patientUid = patientUid,
                expertUid = expertUid,
                title = title,
                note = note,
                dueDate = dueDate,
                scheduleType = scheduleType,
                daysOfWeekJson = daysOfWeekJson,
                autoRepeat = autoRepeat,
                repeatDurationWeeks = repeatDurationWeeks,
                status = TaskStatus.PENDING.name,
                exercisesJson = exercisesJson,
                isSynced = false,
                createdAt = now,
                updatedAt = now
            )
            val taskId = taskDao.insertTask(localTask)

            try {
                val fsTask = FirestoreTaskAssignment(
                    patientId = patientUid,
                    expertId = expertUid,
                    title = title,
                    note = note,
                    dueDate = dueDate,
                    scheduleType = scheduleType,
                    daysOfWeek = daysOfWeek,
                    autoRepeat = autoRepeat,
                    repeatDurationWeeks = repeatDurationWeeks,
                    status = TaskStatus.PENDING.name,
                    exercises = exercises,
                    updatedAt = now
                )
                val taskDocId = firestoreService.createTask(fsTask)
                taskDao.markTaskAsSynced(taskId.toInt(), taskDocId)
                Log.d(TAG, "Görev anında senkronize edildi: $taskDocId")
            } catch (e: Exception) {
                Log.w(TAG, "Anında senkronizasyon başarısız, SyncWorker halledecek: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Görev oluşturma hatası: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateTaskAssignment(
        taskId: Int,
        firebaseDocId: String?,
        expertUid: String,
        patientUid: String,
        title: String,
        note: String,
        dueDate: Long,
        exercises: List<com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem>,
        scheduleType: String,
        daysOfWeek: List<Int>,
        autoRepeat: Boolean,
        repeatDurationWeeks: Int?
    ): Result<Unit> {
        return try {
            val jsonArray = org.json.JSONArray()
            for (ex in exercises) {
                val obj = org.json.JSONObject()
                obj.put("exerciseType", ex.exerciseType)
                obj.put("targetType", ex.targetType)
                if (ex.targetReps != null) obj.put("targetReps", ex.targetReps)
                if (ex.targetDurationSeconds != null) obj.put("targetDurationSeconds", ex.targetDurationSeconds)
                obj.put("sets", ex.sets)
                obj.put("completedSets", ex.completedSets)
                obj.put("restTimeSeconds", ex.restTimeSeconds)
                obj.put("difficulty", ex.difficulty)
                obj.put("category", ex.category)
                if (ex.videoUrl != null) obj.put("videoUrl", ex.videoUrl)
                obj.put("status", ex.status)
                jsonArray.put(obj)
            }
            val exercisesJson = jsonArray.toString()
            val daysOfWeekJson = org.json.JSONArray(daysOfWeek).toString()

            val existing = taskDao.getTaskById(taskId)
            val now = System.currentTimeMillis()
            if (existing != null) {
                val updated = existing.copy(
                    title = title,
                    note = note,
                    dueDate = dueDate,
                    scheduleType = scheduleType,
                    daysOfWeekJson = daysOfWeekJson,
                    autoRepeat = autoRepeat,
                    repeatDurationWeeks = repeatDurationWeeks,
                    exercisesJson = exercisesJson,
                    isSynced = false,
                    updatedAt = now
                )
                taskDao.updateTask(updated)
            }

            if (!firebaseDocId.isNullOrBlank()) {
                val fsTask = FirestoreTaskAssignment(
                    patientId = patientUid,
                    expertId = expertUid,
                    title = title,
                    note = note,
                    dueDate = dueDate,
                    scheduleType = scheduleType,
                    daysOfWeek = daysOfWeek,
                    autoRepeat = autoRepeat,
                    repeatDurationWeeks = repeatDurationWeeks,
                    status = TaskStatus.PENDING.name,
                    exercises = exercises,
                    updatedAt = now
                )
                firestoreService.updateTask(firebaseDocId, fsTask)
                taskDao.markTaskAsSynced(taskId, firebaseDocId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Görev güncelleme hatası: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteTaskAssignment(taskId: Int, firebaseDocId: String?): Result<Unit> {
        return try {
            taskDao.deleteTask(taskId)
            if (!firebaseDocId.isNullOrBlank()) {
                firestoreService.deleteTask(firebaseDocId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Görev silme hatası: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Egzersiz tamamlandığında görevi DONE olarak işaretler.
     */
    override suspend fun completeTask(taskId: Int, firebaseDocId: String?, reportId: Int) {
        val completedAt = System.currentTimeMillis()
        
        // 1. Room'u güncelle
        taskDao.markTaskAsDone(taskId, completedAt, reportId)

        // 2. Firestore'u anında güncelle (Zaten SyncWorker tam desteklemediği için anlık atıyoruz)
        if (firebaseDocId != null && firebaseDocId.isNotEmpty()) {
            try {
                firestoreService.updateTaskStatus(
                    taskDocId = firebaseDocId,
                    status = TaskStatus.COMPLETED.name,
                    completedAt = completedAt
                )
            } catch (e: Exception) {
                Log.w(TAG, "Görev tamamlama Firestore'a aktarılamadı: ${e.message}")
            }
        }
    }

    override suspend fun deactivateDoctorTasks(doctorId: String, patientId: String): Result<Unit> {
        return try {
            // 1. Firestore tarafında deaktif et
            firestoreService.deactivateTasksByDoctor(doctorId, patientId)
            // 2. Lokal Room DB tarafında deaktif et
            taskDao.deactivateTasksByDoctor(doctorId, patientId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTaskFromHome(taskId: Int, firebaseDocId: String?): Result<Unit> {
        return try {
            taskDao.markTaskAsRemoved(taskId)
            if (!firebaseDocId.isNullOrBlank()) {
                runCatching {
                    firestoreService.updateTaskStatus(firebaseDocId, "removed")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Firestore'dan hastaya atanmış görevleri çeker ve Room'a eşler.
     */
    override suspend fun syncTasksForPatient(patientUid: String) {
        try {
            val fsTasks = firestoreService.getTasksForPatient(patientUid)
            for ((docId, fsTask) in fsTasks) {
                val existingTask = taskDao.getTaskByFirebaseDocId(docId)
                
                val jsonArray = org.json.JSONArray()
                for (ex in fsTask.exercises) {
                    val obj = org.json.JSONObject()
                    obj.put("exerciseType", ex.exerciseType)
                    obj.put("targetType", ex.targetType)
                    if (ex.targetReps != null) obj.put("targetReps", ex.targetReps)
                    if (ex.targetDurationSeconds != null) obj.put("targetDurationSeconds", ex.targetDurationSeconds)
                    if (ex.actualReps != null) obj.put("actualReps", ex.actualReps)
                    if (ex.actualDurationSeconds != null) obj.put("actualDurationSeconds", ex.actualDurationSeconds)
                    obj.put("sets", ex.sets)
                    obj.put("completedSets", ex.completedSets)
                    obj.put("restTimeSeconds", ex.restTimeSeconds)
                    obj.put("difficulty", ex.difficulty)
                    obj.put("category", ex.category)
                    if (ex.videoUrl != null) obj.put("videoUrl", ex.videoUrl)
                    obj.put("status", ex.status)
                    jsonArray.put(obj)
                }
                val exJson = jsonArray.toString()
                val daysOfWeekJson = org.json.JSONArray(fsTask.daysOfWeek).toString()

                if (existingTask != null) {
                    if (!existingTask.isSynced) {
                        // Lokalde güncellenmiş ve henüz buluta gitmemiş; üzerine yazma!
                        continue
                    }
                    // Güncelle
                    val updated = existingTask.copy(
                        title = fsTask.title,
                        note = fsTask.note,
                        status = fsTask.status,
                        dueDate = fsTask.dueDate,
                        scheduleType = fsTask.scheduleType,
                        daysOfWeekJson = daysOfWeekJson,
                        autoRepeat = fsTask.autoRepeat,
                        repeatDurationWeeks = fsTask.repeatDurationWeeks,
                        exercisesJson = exJson,
                        createdAt = fsTask.createdAt?.time ?: existingTask.createdAt,
                        updatedAt = fsTask.updatedAt ?: existingTask.updatedAt
                    )
                    taskDao.updateTask(updated)
                } else {
                    // Ekle
                    val newTask = TaskAssignmentEntity(
                        firebaseDocId = docId,
                        patientUid = fsTask.patientId,
                        expertUid = fsTask.expertId,
                        title = fsTask.title,
                        note = fsTask.note,
                        status = fsTask.status,
                        dueDate = fsTask.dueDate,
                        scheduleType = fsTask.scheduleType,
                        daysOfWeekJson = daysOfWeekJson,
                        autoRepeat = fsTask.autoRepeat,
                        repeatDurationWeeks = fsTask.repeatDurationWeeks,
                        exercisesJson = exJson,
                        isSynced = true,
                        createdAt = fsTask.createdAt?.time ?: System.currentTimeMillis(),
                        updatedAt = fsTask.updatedAt ?: System.currentTimeMillis()
                    )
                    taskDao.insertTask(newTask)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync tasks for patient failed", e)
        }
    }

    override suspend fun syncTasksForExpert(expertUid: String) {
        try {
            val fsTasks = firestoreService.getTasksForExpert(expertUid)
            for ((docId, fsTask) in fsTasks) {
                val existingTask = taskDao.getTaskByFirebaseDocId(docId)
                
                val jsonArray = org.json.JSONArray()
                for (ex in fsTask.exercises) {
                    val obj = org.json.JSONObject()
                    obj.put("exerciseType", ex.exerciseType)
                    obj.put("targetType", ex.targetType)
                    if (ex.targetReps != null) obj.put("targetReps", ex.targetReps)
                    if (ex.targetDurationSeconds != null) obj.put("targetDurationSeconds", ex.targetDurationSeconds)
                    if (ex.actualReps != null) obj.put("actualReps", ex.actualReps)
                    if (ex.actualDurationSeconds != null) obj.put("actualDurationSeconds", ex.actualDurationSeconds)
                    obj.put("sets", ex.sets)
                    obj.put("completedSets", ex.completedSets)
                    obj.put("restTimeSeconds", ex.restTimeSeconds)
                    obj.put("difficulty", ex.difficulty)
                    obj.put("category", ex.category)
                    if (ex.videoUrl != null) obj.put("videoUrl", ex.videoUrl)
                    obj.put("status", ex.status)
                    jsonArray.put(obj)
                }
                val exJson = jsonArray.toString()
                val daysOfWeekJson = org.json.JSONArray(fsTask.daysOfWeek).toString()

                if (existingTask != null) {
                    if (!existingTask.isSynced) continue
                    
                    val updated = existingTask.copy(
                        title = fsTask.title,
                        note = fsTask.note,
                        status = fsTask.status,
                        dueDate = fsTask.dueDate,
                        scheduleType = fsTask.scheduleType,
                        daysOfWeekJson = daysOfWeekJson,
                        autoRepeat = fsTask.autoRepeat,
                        repeatDurationWeeks = fsTask.repeatDurationWeeks,
                        exercisesJson = exJson,
                        createdAt = fsTask.createdAt?.time ?: existingTask.createdAt,
                        updatedAt = fsTask.updatedAt ?: existingTask.updatedAt
                    )
                    taskDao.updateTask(updated)
                } else {
                    val newTask = TaskAssignmentEntity(
                        firebaseDocId = docId,
                        patientUid = fsTask.patientId,
                        expertUid = fsTask.expertId,
                        title = fsTask.title,
                        note = fsTask.note,
                        status = fsTask.status,
                        dueDate = fsTask.dueDate,
                        scheduleType = fsTask.scheduleType,
                        daysOfWeekJson = daysOfWeekJson,
                        autoRepeat = fsTask.autoRepeat,
                        repeatDurationWeeks = fsTask.repeatDurationWeeks,
                        exercisesJson = exJson,
                        isSynced = true,
                        createdAt = fsTask.createdAt?.time ?: System.currentTimeMillis(),
                        updatedAt = fsTask.updatedAt ?: System.currentTimeMillis()
                    )
                    taskDao.insertTask(newTask)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync tasks for expert failed", e)
        }
    }
    override fun getPeriodKey(scheduleType: String): String {
        val cal = java.util.Calendar.getInstance()
        return when (scheduleType) {
            "WEEKLY" -> {
                val year = cal.get(java.util.Calendar.YEAR)
                val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                "$year-W$week"
            }
            else -> { // DAILY or CUSTOM
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                sdf.format(cal.time)
            }
        }
    }

    override suspend fun getTaskProgress(taskId: String, periodKey: String, patientUid: String): com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity {
        // 1. Lokalde ara
        val local = progressDao.getProgress(taskId, periodKey)
        if (local != null) return local

        // 2. Firestore'da ara
        val fsProgress = firestoreService.getTaskProgress(taskId, periodKey)
        if (fsProgress != null) {
            val entity = com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity(
                taskId = fsProgress.taskId,
                patientUid = fsProgress.patientId,
                periodKey = fsProgress.periodKey,
                progressJson = org.json.JSONArray(fsProgress.exercises.map { ex ->
                    org.json.JSONObject().apply {
                        put("exerciseType", ex.exerciseType)
                        put("completedSets", ex.completedSets)
                        put("status", ex.status)
                    }
                }).toString(),
                status = fsProgress.status.lowercase(),
                lastUpdatedAt = fsProgress.updatedAt,
                isSynced = true
            )
            progressDao.insertProgress(entity)
            return entity
        }

        // 3. Hiç yoksa yeni oluştur
        val newProgress = com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity(
            taskId = taskId,
            patientUid = patientUid,
            periodKey = periodKey,
            progressJson = "[]",
            status = "pending",
            isSynced = false
        )
        val id = progressDao.insertProgress(newProgress)
        return newProgress.copy(id = id.toInt())
    }

    override suspend fun updateTaskProgress(progress: com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity) {
        progressDao.updateProgress(progress)
        
        // Sync to Firestore
        try {
            val exercisesList = mutableListOf<com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem>()
            val arr = org.json.JSONArray(progress.progressJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                exercisesList.add(com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem(
                    exerciseType = obj.getString("exerciseType"),
                    completedSets = obj.getInt("completedSets"),
                    status = obj.getString("status")
                ))
            }

            val fs = com.example.exerciseformanalyzer.model.firestore.FirestoreTaskProgress(
                taskId = progress.taskId,
                patientId = progress.patientUid,
                periodKey = progress.periodKey,
                exercises = exercisesList,
                status = progress.status.uppercase(),
                updatedAt = System.currentTimeMillis()
            )
            firestoreService.updateTaskProgress(fs)
            progressDao.markAsSynced(progress.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress to Firestore", e)
        }
    }

    override fun observeTaskProgress(taskId: String, periodKey: String): Flow<com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity?> {
        return progressDao.observeProgress(taskId, periodKey)
    }

    override suspend fun updateExerciseProgress(
        firebaseTaskId: String,
        patientUid: String,
        exerciseType: String,
        periodKey: String,
        completedSets: Int,
        totalSets: Int,
        taskExerciseCount: Int
    ) {
        val existing = getTaskProgress(firebaseTaskId, periodKey, patientUid)
        
        // Parse current progress
        val progressArr = try {
            org.json.JSONArray(existing.progressJson)
        } catch (e: Exception) { org.json.JSONArray() }

        // Find and update this exercise
        var found = false
        for (i in 0 until progressArr.length()) {
            val obj = progressArr.getJSONObject(i)
            if (obj.optString("exerciseType") == exerciseType) {
                obj.put("completedSets", completedSets)
                obj.put("status", if (completedSets >= totalSets) "COMPLETED" else "IN_PROGRESS")
                obj.put("updatedAt", System.currentTimeMillis())
                found = true
                break
            }
        }
        if (!found) {
            progressArr.put(org.json.JSONObject().apply {
                put("exerciseType", exerciseType)
                put("completedSets", completedSets)
                put("status", if (completedSets >= totalSets) "COMPLETED" else "IN_PROGRESS")
                put("updatedAt", System.currentTimeMillis())
            })
        }

        // Recalculate overall status based on total sets for better reliability
        var totalCompletedSetsInPeriod = 0
        var anySetStarted = false
        for (i in 0 until progressArr.length()) {
            val obj = progressArr.getJSONObject(i)
            val cSets = obj.optInt("completedSets", 0)
            totalCompletedSetsInPeriod += cSets
            if (cSets > 0 || obj.optString("status") == "IN_PROGRESS") {
                anySetStarted = true
            }
        }
        
        // Görevdeki toplam hedef set sayısını bulmak için task'ı DAO'dan tazeleyelim (veya parametreyi kullanalım)
        // Ancak daha güvenlisi toplam hedef set sayısını parametre olarak almak (zaten ekledik).
        // Fakat user'ın ekranında 5 set görünüyor. 
        // Biz burada tekil egzersizlerin hedef setlerini değil, tüm görevin toplam hedef setini bilmeliyiz.
        
        // Yeni bir yaklaşım: taskExerciseCount yerine totalTaskSets geçebiliriz ama 
        // mevcut taskExerciseCount'u kullanarak "kaç egzersiz tam bitti" kontrolünü daha sağlam yapalım.
        
        var exercisesFullyCompleted = 0
        for (i in 0 until progressArr.length()) {
            if (progressArr.getJSONObject(i).optString("status") == "COMPLETED") {
                exercisesFullyCompleted++
            }
        }

        val allExercisesDone = exercisesFullyCompleted >= taskExerciseCount && taskExerciseCount > 0
        
        val overallStatus = when {
            allExercisesDone -> "COMPLETED"
            anySetStarted || progressArr.length() > 0 -> "IN_PROGRESS"
            else -> "PENDING"
        }

        val updated = existing.copy(
            progressJson = progressArr.toString(),
            status = overallStatus,
            lastUpdatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        updateTaskProgress(updated)
    }
}
