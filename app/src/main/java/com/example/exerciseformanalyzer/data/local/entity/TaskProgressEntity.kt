package com.example.exerciseformanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Günlük veya Haftalık periyotlardaki görev ilerlemesini tutar.
 * Bu sayede ana görev tanımı (TaskAssignment) değişmeden,
 * her gün/hafta için yeni bir ilerleme kaydı oluşturulabilir.
 */
@Entity(tableName = "task_progress")
data class TaskProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: String = "", // FirestoreDocId of TaskAssignment
    val patientUid: String = "",
    val periodKey: String = "", // Örn: "2026-05-01" (DAILY) veya "2026-W18" (WEEKLY)
    
    // O periyodun egzersiz ilerlemelerini tutan JSON
    // [{exerciseType, completedSets, completedReps, status}]
    val progressJson: String = "[]",
    
    val status: String = "pending", // pending, in_progress, completed
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
