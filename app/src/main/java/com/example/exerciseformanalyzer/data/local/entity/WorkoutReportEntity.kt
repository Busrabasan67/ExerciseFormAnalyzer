package com.example.exerciseformanalyzer.data.local.entity

// Egzersiz raporu tablosu — yapay zeka analiz sonuçları.
// Egzersiz bittiğinde ÖNCE buraya yazılır (offline-first),
// SyncWorker arka planda Firestore'a aktarır.
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_reports")
data class WorkoutReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Lokal Room user ID (geriye dönük uyumluluk için)
    val userId: Int,

    // Firebase UID — Firestore senkronizasyonunda kullanılır.
    // Giriş yapılmadan önce kaydedilen raporlarda boş kalabilir.
    val userUid: String = "",

    val exerciseId: Int,
    val score: Int,           // 0-100 arası form puanı
    val reps: Int,            // Tekrar sayısı
    val totalTimeSeconds: Int,// Egzersiz süresi (saniye)

    // MET formülü ile hesaplanan yakılan kalori.
    // CalorieCalculator servisi tarafından doldurulur.
    val caloriesBurned: Float = 0f,

    val feedback: String?,    // Örn: "Dizlerini bükmedin"
    val timestamp: Long = System.currentTimeMillis(),

    // Firestore'da bu dökümanın ID'si (senkronizasyon sonrası dolar)
    val firebaseDocId: String? = null,

    // Offline-first senkronizasyon bayrağı
    val isSynced: Boolean = false
)