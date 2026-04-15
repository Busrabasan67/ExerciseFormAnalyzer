package com.example.exerciseformanalyzer.data.repository
//Egzersiz kayıt/okuma işlemleri
//Arayüzün (ViewModel) ve AI motorunun konuştuğu tek yerdir. DAO'ları birleştirerek iş mantığını (Business Logic) kurar.
import com.example.exerciseformanalyzer.data.local.dao.ExerciseDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutPlanDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutReportDao
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity

class WorkoutRepository(
    private val reportDao: WorkoutReportDao,
    private val planDao: WorkoutPlanDao,
    private val exerciseDao: ExerciseDao
) {

    /**
     * Geliştirici 1 (AI) egzersizi bitirince bunu çağırır.
     * Veri her zaman önce LOKAL veri tabanına yazılır. Buluta atma işini Worker yapacak.
     */
    suspend fun saveWorkoutResult(
        userId: Int,
        exerciseId: Int,
        score: Int,
        reps: Int,
        time: Int,
        feedback: String?
    ) {
        val report = WorkoutReportEntity(
            userId = userId,
            exerciseId = exerciseId,
            score = score,
            reps = reps,
            totalTimeSeconds = time,
            feedback = feedback,
            isSynced = false // Başlangıçta false (Senkronizasyon bekliyor)
        )
        reportDao.insertReport(report)

        // Not: Burada SyncWorker'ı tetikleyen bir komut da eklenebilir.
    }

    /**
     * Hastanın profilinde geçmiş raporları çizerken kullanılır.
     */
    suspend fun getPatientHistory(userId: Int): List<WorkoutReportEntity> {
        return reportDao.getReportsByUser(userId)
    }

    /**
     * AI motoru açılırken kuralları getirmek için kullanılır.
     */
    suspend fun getExerciseRules(exerciseId: Int) = exerciseDao.getExerciseById(exerciseId)

    /**
     * Hastanın bugünkü görevlerini arayüze verir.
     */
    suspend fun getPatientTasks(userId: Int) = planDao.getActivePlansForPatient(userId)
}