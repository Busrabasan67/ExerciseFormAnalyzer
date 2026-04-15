package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity

@Dao
interface WorkoutReportDao {
    @Insert
    suspend fun insertReport(report: WorkoutReportEntity)

    // Hastanın geçmişini tarihe göre getirir
    @Query("SELECT * FROM workout_reports WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getReportsByUser(userId: Int): List<WorkoutReportEntity>

    // Buluta aktarılmamış verileri getirir (Arka plan senkronizasyonu için)
    @Query("SELECT * FROM workout_reports WHERE isSynced = 0")
    suspend fun getUnsyncedReports(): List<WorkoutReportEntity>

    // Buluta başarıyla aktarılan veriyi "aktarıldı" olarak işaretler
    @Query("UPDATE workout_reports SET isSynced = 1 WHERE id = :reportId")
    suspend fun markAsSynced(reportId: Int)
}