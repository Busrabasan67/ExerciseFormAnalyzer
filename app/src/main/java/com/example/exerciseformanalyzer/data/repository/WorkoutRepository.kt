package com.example.exerciseformanalyzer.data.repository

// WorkoutRepository — Egzersiz kayıt ve okuma için tek giriş noktası
//
// Offline-first veri akışı:
//   1. Egzersiz biter → CalorieCalculator ile kalori hesapla
//   2. WorkoutReportEntity → Room'a yaz (isSynced = false)
//   3. SyncWorker arka planda → Firestore'a yükle → isSynced = true, firebaseDocId dolar
//
// UI (ViewModel) doğrudan DAO veya Firebase'e erişmez; bu repository üzerinden geçer.

import com.example.exerciseformanalyzer.data.local.dao.ExerciseDao
import com.example.exerciseformanalyzer.data.local.dao.UserDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutPlanDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutReportDao
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.domain.CalorieCalculator
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.model.firestore.FirestoreWorkoutReport
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(
    private val reportDao: WorkoutReportDao,
    private val planDao: WorkoutPlanDao,
    private val exerciseDao: ExerciseDao,
    private val userDao: UserDao,
    private val firestoreService: FirestoreService
) {

    /**
     * Egzersiz tamamlandığında çağrılır.
     * Kalori hesaplanır → Room'a yazılır → Firestore'a anlık yükleme denenir.
     * İnternet yoksa isSynced = false kalır; SyncWorker sonra tekrar dener.
     *
     * @param userUid Firebase Auth UID (boş olabilir, offline kullanım için)
     * @param localUserId Room'daki lokal kullanıcı ID'si (geriye uyumluluk)
     */
    suspend fun saveWorkoutResult(
        userUid: String,
        localUserId: Int,
        exerciseType: ExerciseType,
        exerciseId: Int,
        score: Int,
        reps: Int,
        durationSeconds: Long,
        feedback: String?
    ) {
        // 1. Kullanıcı ağırlığını Room'dan çek (profil varsa MET daha doğru sonuç verir)
        val weightKg = if (userUid.isNotEmpty()) {
            userDao.getUserByUid(userUid)?.weightKg ?: 70f
        } else {
            70f
        }

        // 2. Kalori hesapla (MET formülü)
        val calories = CalorieCalculator.calculateForSession(
            exerciseType = exerciseType,
            weightKg = weightKg,
            durationSeconds = durationSeconds
        )

        // 3. Room'a yaz — ÖNCE lokal, senkronizasyon sonra
        val report = WorkoutReportEntity(
            userId = localUserId,
            userUid = userUid,
            exerciseId = exerciseId,
            score = score,
            reps = reps,
            totalTimeSeconds = durationSeconds.toInt(),
            caloriesBurned = calories,
            feedback = feedback,
            isSynced = false  // Başlangıçta false — SyncWorker doldurur
        )
        reportDao.insertReport(report)

        // 4. Anlık Firestore yükleme dene (internet varsa hemen; yoksa SyncWorker halleder)
        if (userUid.isNotEmpty()) {
            try {
                val firestoreReport = FirestoreWorkoutReport(
                    userId = userUid,
                    exerciseId = exerciseId.toString(),
                    exerciseName = exerciseType.displayName,
                    score = score,
                    reps = reps,
                    durationSeconds = durationSeconds.toInt(),
                    caloriesBurned = calories,
                    feedback = feedback ?: ""
                )
                val docId = firestoreService.uploadWorkoutReport(firestoreReport)
                // Room'daki kaydı senkronize edildi olarak işaretle
                // Not: lastInsertRowId yerine email bazlı getirmek daha güvenli;
                //      gerçek uygulamada insertReport Long döndürmeli.
                // TODO: insertReport'un Long dönmesi için DAO güncellenmeli.
            } catch (e: Exception) {
                // Internet yoksa sessizce geç — SyncWorker halleder
                android.util.Log.w("WorkoutRepository", "Anlık sync başarısız, SyncWorker bekliyor: ${e.message}")
            }
        }
    }

    /**
     * Hastanın geçmiş raporlarını gerçek zamanlı izle (Flow — UI otomatik güncellenir).
     */
    fun observePatientHistory(userUid: String): Flow<List<WorkoutReportEntity>> {
        return reportDao.observeReportsByUid(userUid)
    }

    /**
     * Lokal ID bazlı geçmiş (geriye uyumluluk).
     */
    suspend fun getPatientHistory(userId: Int): List<WorkoutReportEntity> {
        return reportDao.getReportsByUser(userId)
    }

    /**
     * AI motoru açılırken egzersiz kurallarını getirmek için kullanılır.
     */
    suspend fun getExerciseRules(exerciseId: Int) = exerciseDao.getExerciseById(exerciseId)

    /**
     * Hastanın bugünkü görevlerini arayüze verir.
     */
    suspend fun getPatientTasks(userId: Int) = planDao.getActivePlansForPatient(userId)
}