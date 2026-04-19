package com.example.exerciseformanalyzer.data.local.entity

// Grup üyelik tablosu — Firestore group_members koleksiyonunun lokal cache'i.
// Her satır bir kullanıcının bir gruba üyeliğini temsil eder.
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val firebaseDocId: String? = null,

    val groupId: Int = 0,                   // Lokal Room GroupEntity.id
    val groupFirebaseDocId: String? = null, // Firestore group ID

    val userUid: String,                    // Üye kullanıcının Firebase UID'si
    val userName: String = "",              // Görüntü için cache'lendi

    // "ADMIN" | "MEMBER"
    val role: String = "MEMBER",

    val joinedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
