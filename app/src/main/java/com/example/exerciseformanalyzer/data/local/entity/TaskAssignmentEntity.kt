package com.example.exerciseformanalyzer.data.local.entity

// Plan içindeki tekil görev tablosu.
// Bir WorkoutPlan birden fazla TaskAssignment içerebilir.
// (Örn: "Pazartesi 10 squat" + "Çarşamba 5 şınav" aynı planın görevleri)
//
// Durum geçişleri:
//   PENDING → DONE : Hasta egzersizi tamamlayınca
//   PENDING → MISSED: TaskMarkMissedWorker dueDate geçince otomatik işaretler
import androidx.room.Entity
import androidx.room.PrimaryKey

// Görev durumu — WorkManager'ın kontrol edeceği alan
enum class TaskStatus { PENDING, DONE, MISSED }

@Entity(tableName = "task_assignments")
data class TaskAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Firestore döküman ID — senkronizasyon için
    val firebaseDocId: String? = null,

    // Bağlı olduğu plan (WorkoutPlanEntity.id veya firebaseDocId)
    val planId: Int = 0,
    val planFirebaseDocId: String? = null,

    // Hasta UID — sorgu kolaylığı için tekrarlı tutulur
    val patientUid: String = "",

    val exerciseId: Int,
    val exerciseName: String,      // Görevi gösterirken sorgu atmamak için cache'lendi

    val targetReps: Int = 0,       // Hedef tekrar sayısı
    val targetDurationSec: Int = 0,// Hedef süre (plank gibi zaman bazlı hareketler için)

    val dueDate: Long,             // Bu görev için son tarih (ms cinsinden epoch)

    // Görev durumu — PENDING varsayılan
    val status: String = TaskStatus.PENDING.name,

    // Görev tamamlandıysa, tamamlanma zamanı
    val completedAt: Long? = null,

    // Tamamlandıktan sonra hangi WorkoutReport ile ilişkilendirildiği
    val linkedReportId: Int? = null,

    val isSynced: Boolean = false
)
