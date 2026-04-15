package com.example.exerciseformanalyzer.data.local.entity
//Kullanıcılar
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val email: String,
    val passwordHash: String?, // Google girişi için null olabilir
    val googleId: String? = null,
    val role: String, // "ADMIN", "DOCTOR", "PATIENT"
    val healthNotes: String? = null, // Fıtık, menisküs vb. (Sadece hastalar için)
    val isSynced: Boolean = false // İnternet gelince buluta gitti mi?
)