package com.example.exerciseformanalyzer.data.local.entity

// ExerciseSessionEntity — Egzersiz oturumu anlık verisi
//
// WorkoutReportEntity (sonuç özeti) ile farkı:
//   - ExerciseSession: Oturum anlık meta verisi (başlangıç zamanı, duraklamalar, set sayısı)
//   - WorkoutReport:   Analiz sonucu (skor, tekrar, kalori, form geri bildirimi)
//
// Kullanım akışı:
//   1. Kullanıcı egzersiz başlattığında → ExerciseSession INSERT (status=ACTIVE)
//   2. Egzersiz bitince → status=COMPLETED, endTime dolar
//   3. WorkoutReport oluşturulur ve sessionId ile ilişkilendirilir
//   4. SyncWorker her iki kaydı da Firestore'a gönderir

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_sessions")
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Firebase UID — giriş yapılmamışsa boş string (offline kullanım için güvenli)
    val userUid: String = "",
    val localUserId: Int = 0,

    // Hangi egzersiz yapıldı
    val exerciseName: String,
    val exerciseId: Int = 0,

    // Oturum zaman bilgisi
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationSeconds: Long = 0L,

    // Oturum durumu
    val status: String = SessionStatus.ACTIVE.name, // "ACTIVE" | "COMPLETED" | "CANCELLED"

    // Analiz istatistikleri (oturum süresince birikir)
    val totalSamples: Int = 0,       // Analiz edilen toplam kare sayısı
    val correctSamples: Int = 0,     // Doğru form olan kare sayısı
    val totalReps: Int = 0,

    // Bütün setin içindeki toplam kalori (WorkoutReport'ta da tekrarlanır — convenience untuk)
    val caloriesBurned: Float = 0f,

    // Senkronizasyon
    val isSynced: Boolean = false
)

enum class SessionStatus { ACTIVE, COMPLETED, CANCELLED }
