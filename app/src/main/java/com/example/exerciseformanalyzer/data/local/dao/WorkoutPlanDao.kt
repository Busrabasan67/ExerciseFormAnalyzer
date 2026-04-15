package com.example.exerciseformanalyzer.data.local.dao
//Doktor Planları Komutları
//Doktorların hastalarına atadığı görevleri ve zaman aşımlarını yönetir.
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.WorkoutPlanEntity

@Dao
interface WorkoutPlanDao {

    // Doktor bir hastaya yeni egzersiz planı atadığında çalışır.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    // Hastanın ana sayfasında (Geliştirici 3'ün ekranında) süresi geçmemiş görevleri listeler.
    @Query("SELECT * FROM workout_plans WHERE patientId = :patientId AND isActive = 1 ORDER BY dueDate ASC")
    suspend fun getActivePlansForPatient(patientId: Int): List<WorkoutPlanEntity>

    // --- SENKRONİZASYON (OFFLINE-FIRST) KOMUTLARI ---

    // İnternet yokken doktorun yazdığı planları bulur (Buluta gitmeyenler)
    @Query("SELECT * FROM workout_plans WHERE isSynced = 0")
    suspend fun getUnsyncedPlans(): List<WorkoutPlanEntity>

    // Plan buluta başarıyla yüklendiğinde "Eşitlendi" olarak işaretler
    @Query("UPDATE workout_plans SET isSynced = 1 WHERE id = :planId")
    suspend fun markPlanAsSynced(planId: Int)
}