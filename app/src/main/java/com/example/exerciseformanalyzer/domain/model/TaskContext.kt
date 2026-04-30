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
    val taskId: Int,
    val exerciseIndex: Int,         // exercisesJson array içindeki konum (0-tabanlı)
    val targetType: String,         // "REPS" | "DURATION"
    val targetReps: Int,
    val targetDurationSeconds: Int,
    val targetSets: Int,
    val completedSets: Int,
    val restTimeSeconds: Int
)
