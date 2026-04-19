package com.example.exerciseformanalyzer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.exerciseformanalyzer.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TaskMarkMissedWorker
 *
 * Bu Worker, belirli periyotlarla (örneğin günde bir kez) çalışarak
 * Room veritabanındaki "PENDING" durumunda olan ve "dueDate" (bitiş tarihi) geçmiş olan
 * görevleri bulur ve durumlarını "MISSED" (Kaçırıldı) olarak günceller.
 * Güncellenen görevlerin isSynced bayrağı, SyncWorker'ın Firestore'u güncellemesi için 0'a çekilir.
 */
class TaskMarkMissedWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val taskDao = database.taskAssignmentDao()

            val nowMs = System.currentTimeMillis()
            val expiredTasks = taskDao.getExpiredPendingTasks(nowMs)

            if (expiredTasks.isEmpty()) {
                return@withContext Result.success()
            }

            for (task in expiredTasks) {
                // Görevi MISSED olarak işaretle
                taskDao.markTaskAsMissed(task.id)

                // Senkronizasyon için isSynced=0 yapmak gerekir ki SyncWorker Firebase'e yollasın
                // Dao'da updateTask var, mevcut task'ı klonlayıp updateTask ile isSynced=false atayalım
                val updatedTask = task.copy(
                    status = com.example.exerciseformanalyzer.data.local.entity.TaskStatus.MISSED.name,
                    isSynced = false
                )
                taskDao.updateTask(updatedTask)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
