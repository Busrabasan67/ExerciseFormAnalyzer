package com.example.exerciseformanalyzer.data.local.dao

// ExerciseSessionDao — Egzersiz oturumu CRUD sorguları
// MainViewModel ve WorkoutRepository bu DAO'yu kullanır.

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSessionDao {

    // Yeni oturum başladığında kaydet; ID döner (WorkoutReport ile ilişkilendirmek için)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ExerciseSessionEntity): Long

    // Oturum bitince güncelle — status, endTime, kalori ve istatistikler dolar
    @Query("""
        UPDATE exercise_sessions 
        SET status = :status,
            endTime = :endTime,
            durationSeconds = :durationSeconds,
            totalReps = :totalReps,
            totalSamples = :totalSamples,
            correctSamples = :correctSamples,
            caloriesBurned = :calories
        WHERE id = :sessionId
    """)
    suspend fun completeSession(
        sessionId: Int,
        status: String,
        endTime: Long,
        durationSeconds: Long,
        totalReps: Int,
        totalSamples: Int,
        correctSamples: Int,
        calories: Float
    )

    // Kullanıcının tüm oturumlarını gerçek zamanlı izle (HistoryScreen için)
    @Query("SELECT * FROM exercise_sessions WHERE userUid = :userUid ORDER BY startTime DESC")
    fun observeSessionsByUid(userUid: String): Flow<List<ExerciseSessionEntity>>

    // Belirli oturumu getir (WorkoutReport ile ilişkilendirilirken)
    @Query("SELECT * FROM exercise_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Int): ExerciseSessionEntity?

    // SyncWorker için: Firestore'a henüz gitmemiş oturumları getir
    @Query("SELECT * FROM exercise_sessions WHERE isSynced = 0 AND status = 'COMPLETED'")
    suspend fun getUnsyncedCompletedSessions(): List<ExerciseSessionEntity>

    // Senkronizasyon tamamlandı işareti
    @Query("UPDATE exercise_sessions SET isSynced = 1 WHERE id = :sessionId")
    suspend fun markSessionAsSynced(sessionId: Int)

    // Aktif (yarıda kalmış) oturumları temizle — uygulama çöktükten sonra kalıntıları yönetmek için
    @Query("UPDATE exercise_sessions SET status = 'CANCELLED' WHERE status = 'ACTIVE'")
    suspend fun cancelAllActiveSessions()
}
