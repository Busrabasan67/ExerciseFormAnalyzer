package com.example.exerciseformanalyzer.worker
//İnternet gelince verileri buluta atacak sınıf
//İnternet Denetçisi ve Bulut Aktarıcısı
//Arka planda çalışır. İnternet bağlantısı sağlandığında, isSynced = false olan tüm verileri toplar, buluta yollar ve ardından yerelde true yapar.
//
//(Not: Firebase kodlarını yorum satırı olarak bıraktım, Firebase'e geçtiğinizde oraları açacaksınız).

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.exerciseformanalyzer.data.local.AppDatabase
import com.example.exerciseformanalyzer.data.local.entity.WorkoutPlanEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Senkronizasyon başlatılıyor...")

            // 1. Veri tabanı bağlantısını al (Hilt/Dagger kullanmıyorsanız bu yöntem geçerlidir)
            // Not: AppDatabase içinde getInstance() gibi bir Singleton metodunuz olmalıdır.
            val database = AppDatabase.getInstance(applicationContext)
            val reportDao = database.workoutReportDao()
            val planDao = database.workoutPlanDao()

            // 2. Buluta gitmemiş (Offline) raporları bul (TÜRÜNÜ AÇIKÇA BELİRTİYORUZ)
            val unsyncedReports: List<WorkoutReportEntity> = reportDao.getUnsyncedReports()

            // 3. Verileri Buluta (Firebase vb.) gönder
            for (report in unsyncedReports) {
                //  Firebase Entegrasyonu
                val success = true // Şimdilik başarılı varsayıyoruz

                if (success) {
                    reportDao.markAsSynced(report.id)
                    Log.d("SyncWorker", "Rapor ${report.id} buluta başarıyla aktarıldı.")
                }
            }

            // Doktor Planları için de türü açıkça belirtiyoruz
            val unsyncedPlans: List<WorkoutPlanEntity> = planDao.getUnsyncedPlans()
            for (plan in unsyncedPlans) {
//                FirebaseManager.uploadPlan(plan)
                planDao.markPlanAsSynced(plan.id)
            }

            Log.d("SyncWorker", "Tüm veriler başarıyla senkronize edildi.")
            Result.success()

        } catch (e: Exception) {
            Log.e("SyncWorker", "Senkronizasyon sırasında hata: ${e.message}")
            Result.retry() // Hata olursa internet gelince tekrar dener
        }
    }
}