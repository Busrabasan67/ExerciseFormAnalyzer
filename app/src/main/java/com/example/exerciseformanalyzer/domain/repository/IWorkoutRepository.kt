package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.ExerciseEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.domain.model.TaskContext
import com.example.exerciseformanalyzer.model.ExerciseType
import kotlinx.coroutines.flow.Flow

/**
 * Egzersiz kayıt ve okuma işlemleri için domain-layer sözleşmesi.
 *
 * Offline-first: Room'a yaz → SyncWorker arka planda Firestore'a gönderir.
 */
interface IWorkoutRepository {

    /**
     * Egzersiz tamamlandığında çağrılır.
     * Kalori hesaplanır → XP / Streak güncellenir → Room'a yazılır → Firestore'a anlık yükleme denenir.
     *
     * @param taskContext Görev bağlamlı seans için bağlam; serbest egzersizde null.
     */
    suspend fun saveWorkoutResult(
        userUid: String,
        localUserId: Int,
        exerciseType: ExerciseType,
        exerciseId: Int,
        score: Int,
        sessionNewReps: Int,
        sessionNewDurationSec: Long,
        feedback: String?,
        taskContext: TaskContext? = null
    )

    /** Hastanın geçmiş raporlarını gerçek zamanlı izler. */
    fun observePatientHistory(userUid: String): Flow<List<WorkoutReportEntity>>

    /** Lokal ID bazlı geçmiş sorgusu (geriye uyumluluk). */
    suspend fun getPatientHistory(userId: Int): List<WorkoutReportEntity>

    /**
     * Egzersiz seti yarıda bırakıldığında (kullanıcı geri çıkınca) çağrılır.
     * Tam workout raporu yazmadan yalnızca görevin exercisesJson içindeki
     * actualReps / actualDurationSeconds alanlarını günceller.
     * Böylece bir sonraki girişte kaldığı yerden devam edilebilir.
     *
     * @param taskContext    Mevcut görev bağlamı (taskId, exerciseIndex, completedSets vb.)
     * @param sessionNewReps    Bu seansa ait toplam yeni tekrar sayısı (delta)
     * @param sessionNewDurSec  Bu seansa ait toplam yeni süre (delta saniye)
     */
    suspend fun savePartialProgress(
        userUid: String,
        taskContext: TaskContext,
        sessionNewReps: Int,
        sessionNewDurSec: Long
    )

    /** Egzersiz kurallarını getirmek için kullanılır (AI motoru). */
    suspend fun getExerciseRules(exerciseId: Int): ExerciseEntity?
}
