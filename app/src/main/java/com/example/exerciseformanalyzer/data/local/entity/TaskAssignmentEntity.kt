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

    val patientUid: String = "",
    val expertUid: String = "",

    val title: String = "",
    val note: String = "",

    val dueDate: Long,

    val status: String = TaskStatus.PENDING.name,

    // JSON array of exercises [{exerciseType, targetType, targetReps, targetDurationSeconds}]
    val exercisesJson: String = "[]",

    val completedAt: Long? = null,
    val linkedReportId: Int? = null,

    val isSynced: Boolean = false
)
