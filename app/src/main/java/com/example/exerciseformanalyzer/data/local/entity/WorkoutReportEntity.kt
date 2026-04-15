package com.example.exerciseformanalyzer.data.local.entity
//Yapay Zeka Sonuçları
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_reports")
data class WorkoutReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val exerciseId: Int,
    val score: Int, // 0-100 arası form puanı
    val reps: Int, // Tekrar sayısı
    val totalTimeSeconds: Int, // Süre
    val feedback: String?, // Örn: "Dizlerini bükmedin"
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false // Offline-first mantığı için kritik!
)