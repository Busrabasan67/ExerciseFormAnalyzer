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
                isSynced = false
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
                    exercises = exercises
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
                        exercisesJson = exJson
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
                        isSynced = true
                    )
                    taskDao.insertTask(newTask)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync tasks for patient failed", e)
        }
    }
}
