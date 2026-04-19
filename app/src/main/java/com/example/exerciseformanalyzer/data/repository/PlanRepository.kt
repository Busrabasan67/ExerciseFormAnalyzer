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
import com.example.exerciseformanalyzer.model.firestore.FirestorePlan
import com.example.exerciseformanalyzer.model.firestore.FirestoreTaskAssignment
import kotlinx.coroutines.flow.Flow

class PlanRepository(
    private val planDao: WorkoutPlanDao,
    private val taskDao: TaskAssignmentDao,
    private val firestoreService: FirestoreService
) {

    private val TAG = "PlanRepository"

    /**
     * Hastanın aktif olan bekleyen görevlerini UI için reaktif olarak izler.
     */
    fun observePendingTasks(patientUid: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observePendingTasksForPatient(patientUid)
    }

    /**
     * Hastanın tüm görev geçmişini izler (tamamlananlar ve kaçırılanlar dahil).
     */
    fun observeAllTasks(patientUid: String): Flow<List<TaskAssignmentEntity>> {
        return taskDao.observeAllTasksForPatient(patientUid)
    }

    /**
     * Uzman tarafından yeni bir antrenman planı oluşturulur.
     * İçerisinde birden fazla görev (Task) olabilir.
     * Önce Room'a yazılır, internet varsa anında Firestore'a itilir.
     */
    suspend fun createPlanWithTasks(
        expertUid: String,
        patientUid: String,
        title: String,
        description: String,
        assignedDate: Long,
        dueDate: Long,
        tasks: List<TaskAssignmentEntity> // Görevlerin geçici halleri (planId eksik)
    ): Result<Unit> {
        return try {
            // 1. Planı Room'a kaydet
            val localPlan = WorkoutPlanEntity(
                patientId = 0, // Geriye dönük uyumluluk, kullanmıyoruz
                patientUid = patientUid,
                expertUid = expertUid,
                title = title,
                description = description,
                assignedDate = assignedDate,
                dueDate = dueDate,
                isActive = true,
                isSynced = false
            )
            val localPlanId = planDao.insertPlan(localPlan)

            // 2. Her bir göreve localPlanId (Room primary key) ata ve Room'a kaydet
            val insertedTasks = tasks.map {
                val taskToInsert = it.copy(planId = localPlanId.toInt(), patientUid = patientUid, isSynced = false)
                val taskId = taskDao.insertTask(taskToInsert)
                taskToInsert.copy(id = taskId.toInt())
            }

            // 3. Firestore'a anında yükleme dene
            try {
                // Plan upload
                val fsPlan = FirestorePlan(
                    expertId = expertUid,
                    patientId = patientUid,
                    title = title,
                    description = description,
                    assignedDate = assignedDate,
                    dueDate = dueDate,
                    isActive = true
                )
                val planDocId = firestoreService.createPlan(fsPlan)
                planDao.markPlanAsSynced(localPlanId.toInt(), planDocId)

                // Task upload
                for (task in insertedTasks) {
                    val fsTask = FirestoreTaskAssignment(
                        planId = planDocId,
                        patientId = patientUid,
                        exerciseId = task.exerciseId.toString(),
                        exerciseName = task.exerciseName,
                        targetReps = task.targetReps,
                        targetDurationSec = task.targetDurationSec,
                        dueDate = task.dueDate,
                        status = task.status
                    )
                    val taskDocId = firestoreService.createTask(fsTask)
                    taskDao.markTaskAsSynced(task.id, taskDocId)
                }

                Log.d(TAG, "Plan ve görevler anında senkronize edildi.")
            } catch (e: Exception) {
                Log.w(TAG, "Anında senkronizasyon başarısız, SyncWorker halledecek: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Plan oluşturma hatası: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Egzersiz tamamlandığında görevi DONE olarak işaretler.
     */
    suspend fun completeTask(taskId: Int, firebaseDocId: String?, reportId: Int) {
        val completedAt = System.currentTimeMillis()
        
        // 1. Room'u güncelle
        taskDao.markTaskAsDone(taskId, completedAt, reportId)

        // 2. Firestore'u anında güncelle (Zaten SyncWorker tam desteklemediği için anlık atıyoruz)
        if (firebaseDocId != null && firebaseDocId.isNotEmpty()) {
            try {
                firestoreService.updateTaskStatus(
                    taskDocId = firebaseDocId,
                    status = TaskStatus.DONE.name,
                    completedAt = completedAt
                )
            } catch (e: Exception) {
                Log.w(TAG, "Görev tamamlama Firestore'a aktarılamadı: ${e.message}")
            }
        }
    }
}
