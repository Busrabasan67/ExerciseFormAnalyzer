package com.example.exerciseformanalyzer.worker

// SyncWorker — Offline-first senkronizasyon işçisi
//
// Ne zaman çalışır?
//   WorkManager tarafından ağ bağlantısı sağlandığında otomatik tetiklenir.
//   Kısıtlaması: NETWORK_CONNECTED (sadece internette çalışır).
//
// Ne yapar?
//   1. Room'da isSynced = false olan raporları bulur
//   2. Firestore'a yükler
//   3. Başarılı olursa isSynced = true, firebaseDocId günceller
//   4. Plan ve görevleri de aynı şekilde senkronize eder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.exerciseformanalyzer.data.local.AppDatabase
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.model.firestore.FirestoreWorkoutReport

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "exercise_sync_worker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Senkronizasyon başlatılıyor...")

            val database = AppDatabase.getInstance(applicationContext)
            val reportDao = database.workoutReportDao()
            val planDao = database.workoutPlanDao()
            val firestoreService = FirestoreService()

            // --- RAPOR SENKRONİZASYONU ---
            val unsyncedReports = reportDao.getUnsyncedReports()
            Log.d(TAG, "${unsyncedReports.size} senkronize edilmemiş rapor bulundu.")

            for (report in unsyncedReports) {
                try {
                    val firestoreReport = FirestoreWorkoutReport(
                        userId = report.userUid,
                        exerciseId = report.exerciseId.toString(),
                        score = report.score,
                        reps = report.reps,
                        durationSeconds = report.totalTimeSeconds,
                        caloriesBurned = report.caloriesBurned,
                        feedback = report.feedback ?: ""
                    )

                    // Firestore'a yükle ve döküman ID'sini al
                    val docId = firestoreService.uploadWorkoutReport(firestoreReport)

                    // Room'da senkronize edildi olarak işaretle
                    reportDao.markAsSynced(report.id, docId)
                    Log.d(TAG, "Rapor ${report.id} (Firestore: $docId) başarıyla senkronize edildi.")

                } catch (e: Exception) {
                    // Tek bir rapor hata verse de diğerleri denenmeye devam eder
                    Log.e(TAG, "Rapor ${report.id} senkronizasyon hatası: ${e.message}")
                }
            }

            // --- PLAN SENKRONİZASYONU ---
            val unsyncedPlans = planDao.getUnsyncedPlans()
            Log.d(TAG, "${unsyncedPlans.size} senkronize edilmemiş plan bulundu.")

            for (plan in unsyncedPlans) {
                try {
                    val firestorePlan = com.example.exerciseformanalyzer.model.firestore.FirestorePlan(
                        expertId = plan.expertUid ?: "",
                        patientId = plan.patientUid ?: "",
                        title = plan.title ?: "",
                        description = plan.description ?: "",
                        assignedDate = plan.assignedDate,
                        dueDate = plan.dueDate,
                        isActive = plan.isActive
                    )
                    
                    val docId = firestoreService.createPlan(firestorePlan)
                    planDao.markPlanAsSynced(plan.id, docId)
                    Log.d(TAG, "Plan ${plan.id} (Firestore: $docId) başarıyla senkronize edildi.")
                } catch (e: Exception) {
                    Log.e(TAG, "Plan ${plan.id} senkronizasyon hatası: ${e.message}")
                }
            }

            // --- GÖREV (TASK) SENKRONİZASYONU ---
            val taskDao = database.taskAssignmentDao()
            val unsyncedTasks = taskDao.getUnsyncedTasks()
            Log.d(TAG, "${unsyncedTasks.size} senkronize edilmemiş görev bulundu.")

            for (task in unsyncedTasks) {
                try {
                    if (task.firebaseDocId.isNullOrEmpty()) {
                        val firestoreTask = com.example.exerciseformanalyzer.model.firestore.FirestoreTaskAssignment(
                            planId = task.planId.toString(), // TODO: Gerçekte Firestore Plan ID'si gerekebilir
                            patientId = task.patientUid,
                            exerciseId = task.exerciseId.toString(),
                            exerciseName = task.exerciseName,
                            targetReps = task.targetReps,
                            targetDurationSec = task.targetDurationSec,
                            dueDate = task.dueDate,
                            status = task.status,
                            completedAt = task.completedAt,
                            reportId = task.linkedReportId?.toString()
                        )
    
                        val docId = firestoreService.createTask(firestoreTask)
                        taskDao.markTaskAsSynced(task.id, docId)
                        Log.d(TAG, "Görev ${task.id} (Firestore: $docId) başarıyla oluşturuldu.")
                    } else {
                        // Sadece statüsü değişmiş (MISSED veya DONE)
                        firestoreService.updateTaskStatus(task.firebaseDocId, task.status, task.completedAt)
                        taskDao.markTaskAsSynced(task.id, task.firebaseDocId)
                        Log.d(TAG, "Görev ${task.id} (Firestore: ${task.firebaseDocId}) başarıyla güncellendi.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Görev ${task.id} senkronizasyon hatası: ${e.message}")
                }
            }


            Log.d(TAG, "Senkronizasyon tamamlandı.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Senkronizasyon genel hata: ${e.message}")
            Result.retry()  // Hata olursa WorkManager otomatik tekrar dener
        }
    }
}