package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    // Lokal ID bazlı (geriye uyumluluk)
    @Query("SELECT * FROM workout_plans WHERE patientId = :patientId AND isActive = 1 ORDER BY dueDate ASC")
    suspend fun getActivePlansForPatient(patientId: Int): List<WorkoutPlanEntity>

    // Firebase UID bazlı — Firebase entegrasyonu sonrası kullanılır
    @Query("SELECT * FROM workout_plans WHERE patientUid = :patientUid AND isActive = 1 ORDER BY dueDate ASC")
    fun observeActivePlansForPatientByUid(patientUid: String): Flow<List<WorkoutPlanEntity>>

    // Senkronizasyon için
    @Query("SELECT * FROM workout_plans WHERE isSynced = 0")
    suspend fun getUnsyncedPlans(): List<WorkoutPlanEntity>

    // Firestore'a yüklenince hem isSynced güncelle hem de doc ID'sini kaydet
    @Query("UPDATE workout_plans SET isSynced = 1, firebaseDocId = :docId WHERE id = :planId")
    suspend fun markPlanAsSynced(planId: Int, docId: String = "")
}