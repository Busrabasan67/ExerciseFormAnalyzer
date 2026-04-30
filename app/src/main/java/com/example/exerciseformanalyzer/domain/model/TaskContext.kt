package com.example.exerciseformanalyzer.domain.model

/**
 * Görev bağlamlı egzersiz seansı için bağlam verisi.
 *
 * Serbest egzersizde (FAB) null kalır.
 * PatientDashboard → "Başla" butonundan gelen görev seanslarında set edilir.
 *
 * Not: Bu sınıf daha önce MainViewModel içindeydi ve WorkoutRepository'ye
 * UI katman bağımlılığı yaratıyordu. Domain katmanına taşınarak katman
 * bağımlılığı kırılmıştır.
 */
data class TaskContext(
    val taskId: Int,                    // Room local ID
    val firebaseTaskId: String = "",    // Firestore DocId (progress sync için)
    val exerciseIndex: Int,             // exercisesJson array içindeki konum (0-tabanlı)
    val exerciseType: String = "",      // Egzersiz tipi adı (progress güncelleme için)
    val targetType: String,             // "REPS" | "DURATION"
    val targetReps: Int,
    val targetDurationSeconds: Int,
    val targetSets: Int,
    val completedSets: Int,
    val restTimeSeconds: Int,
    val scheduleType: String = "DAILY"  // Periyot anahtarı için
)
