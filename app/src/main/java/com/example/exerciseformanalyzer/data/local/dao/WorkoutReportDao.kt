package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutReportDao {

    // Yeni rapor ekler; çakışma olursa üzerine yazar (Firebase'den dönen güncel veri için)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: WorkoutReportEntity)

    // Hastanın tüm raporlarını gerçek zamanlı izle — HistoryScreen için reaktif akış
    // NOT: suspend yerine Flow kullanıldı çünkü HistoryScreen her yeni rapor
    //      eklendiğinde otomatik güncellenmeli (collectAsState ile kullanılır).
    @Query("SELECT * FROM workout_reports WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeReportsByUser(userId: Int): Flow<List<WorkoutReportEntity>>

    // Firebase UID ile sorgu — Firebase Auth sonrası kullanılır
    @Query("SELECT * FROM workout_reports WHERE userUid = :userUid ORDER BY timestamp DESC")
    fun observeReportsByUid(userUid: String): Flow<List<WorkoutReportEntity>>

    // Tek seferlik veri çekme (eski uyumluluk + SyncWorker için suspend)
    @Query("SELECT * FROM workout_reports WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getReportsByUser(userId: Int): List<WorkoutReportEntity>

    // Buluta aktarılmamış raporları getir — SyncWorker bunu kullanır
    @Query("SELECT * FROM workout_reports WHERE isSynced = 0")
    suspend fun getUnsyncedReports(): List<WorkoutReportEntity>

    // Senkronizasyon sonrası isSynced güncelle ve firebaseDocId'yi kaydet
    @Query("UPDATE workout_reports SET isSynced = 1, firebaseDocId = :docId WHERE id = :reportId")
    suspend fun markAsSynced(reportId: Int, docId: String)

    // Kullanıcının toplam yakılan kaloriyi hesapla (profil istatistikleri için)
    @Query("SELECT SUM(caloriesBurned) FROM workout_reports WHERE userUid = :userUid")
    suspend fun getTotalCaloriesByUid(userUid: String): Float?
}