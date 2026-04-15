package com.example.exerciseformanalyzer.data.local.entity
//Doktorların Atadığı Planlar
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val doctorId: Int, // Atayan doktor
    val patientId: Int, // Atanan hasta
    val assignedDate: Long, // Planın verildiği tarih
    val dueDate: Long, // Planın bitiş tarihi (Zaman aşımı için)
    val isActive: Boolean = true,
    val isSynced: Boolean = false
)