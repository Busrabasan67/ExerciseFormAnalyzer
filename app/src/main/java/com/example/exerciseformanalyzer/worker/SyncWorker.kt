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
//   4. Görevleri de aynı şekilde senkronize eder

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

            // --- GÖREV (TASK) SENKRONİZASYONU ---
            val taskDao = database.taskAssignmentDao()
            val unsyncedTasks = taskDao.getUnsyncedTasks()
            Log.d(TAG, "${unsyncedTasks.size} senkronize edilmemiş görev bulundu.")

            for (task in unsyncedTasks) {
                try {
                    if (task.firebaseDocId.isNullOrEmpty()) {
                        val exercisesList = mutableListOf<com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem>()
                        try {
                            val arr = org.json.JSONArray(task.exercisesJson)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                exercisesList.add(com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem(
                                    exerciseType = obj.optString("exerciseType"),
                                    targetType = obj.optString("targetType"),
                                    targetReps = if (obj.has("targetReps")) obj.getInt("targetReps") else null,
                                    targetDurationSeconds = if (obj.has("targetDurationSeconds")) obj.getInt("targetDurationSeconds") else null,
                                    actualReps = if (obj.has("actualReps")) obj.getInt("actualReps") else null,
                                    actualDurationSeconds = if (obj.has("actualDurationSeconds")) obj.getInt("actualDurationSeconds") else null,
                                    status = obj.optString("status")
                                ))
                            }
                        } catch (e: Exception) {}

                        val firestoreTask = com.example.exerciseformanalyzer.model.firestore.FirestoreTaskAssignment(
                            patientId = task.patientUid,
                            expertId = task.expertUid,
                            title = task.title,
                            note = task.note,
                            dueDate = task.dueDate,
                            status = task.status,
                            exercises = exercisesList
                        )
    
                        val docId = firestoreService.createTask(firestoreTask)
                        taskDao.markTaskAsSynced(task.id, docId)
                        Log.d(TAG, "Görev ${task.id} (Firestore: $docId) başarıyla oluşturuldu.")
                    } else {
                        // Sadece statüsü değil, varsa öğe ilerlemeleri de değişmiş olabilir
                        val exercisesList = mutableListOf<com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem>()
                        try {
                            val arr = org.json.JSONArray(task.exercisesJson)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                exercisesList.add(com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem(
                                    exerciseType = obj.optString("exerciseType"),
                                    targetType = obj.optString("targetType"),
                                    targetReps = if (obj.has("targetReps")) obj.getInt("targetReps") else null,
                                    targetDurationSeconds = if (obj.has("targetDurationSeconds")) obj.getInt("targetDurationSeconds") else null,
                                    actualReps = if (obj.has("actualReps")) obj.getInt("actualReps") else null,
                                    actualDurationSeconds = if (obj.has("actualDurationSeconds")) obj.getInt("actualDurationSeconds") else null,
                                    status = obj.optString("status")
                                ))
                            }
                        } catch (e: Exception) {}

                        firestoreService.updateTaskStatus(task.firebaseDocId, task.status, task.completedAt, exercisesList)
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