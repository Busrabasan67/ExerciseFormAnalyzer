package com.example.exerciseformanalyzer.data.local.entity

// Uzman tarafından hastaya atanan egzersiz planının lokal cache'i.
// Ana kaynak Firestore plans/{planId} koleksiyonudur.
// TaskAssignmentEntity: plan içindeki tekil görevleri tutar (ayrı tablo).
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Firestore döküman ID — senkronizasyon ve Firestore sorguları için
    val firebaseDocId: String? = null,

    // Geriye dönük uyumluluk: lokal Room ID'leri
    val doctorId: Int = 0,
    val patientId: Int = 0,

    // Firebase UID bazlı alanlar (Firebase entegrasyonu sonrası kullanılacak)
    val expertUid: String = "",
    val patientUid: String = "",

    val title: String = "",           // Plan adı, örn: "Diz Rehabilitasyon Planı"
    val description: String? = null,  // Uzman notları

    val assignedDate: Long,           // Planın verildiği tarih (epoch ms)
    val dueDate: Long,                // Bitiş tarihi (WorkManager timeout için)

    val isActive: Boolean = true,
    val isSynced: Boolean = false
)