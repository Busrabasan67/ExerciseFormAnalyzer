package com.example.exerciseformanalyzer.data.local.entity

// Sosyal grup tablosu — Firestore groups/{groupId} koleksiyonunun lokal cache'i.
// Kullanıcılar grup oluşturabilir, gruba katılabilir.
// Grup içinde rapor özetleri paylaşılabilir.
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Firestore döküman ID
    val firebaseDocId: String? = null,

    val name: String,
    val description: String? = null,

    // Grubu oluşturan kullanıcının Firebase UID'si
    val creatorUid: String,

    // Gizli grup mu? Gizli gruplara sadece davet ile katılınabilir.
    val isPrivate: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
