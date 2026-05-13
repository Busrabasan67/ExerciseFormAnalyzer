package com.example.exerciseformanalyzer.model.firestore

// Firestore Data Model'leri
// Bu data class'lar Firestore dökümanlarını temsil eder.
// Room Entity'leri ile AYRI tutuldu:
//   - Firestore'da field isimleri farklı olabilir
//   - Zaman damgası formatları farklı (Firestore Date, Room Long)
//   - İki sistemin şeması bağımsız gelişebilir
//
// ÖNEMLİ: docId alanları bu class'lara dahil edilmedi.
// Sebep: docId Firestore dökümanının ID'sidir, döküman içindeki bir alan değil.
// FirestoreService içinde document.id olarak ayrıca okunur ve Room'a yazılır.

import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.PropertyName
import java.util.Date

// =========================================================
// KULLANICI — users/{uid}
// =========================================================
data class FirestoreUser(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "PATIENT",   // "PATIENT" | "EXPERT" | "ADMIN"
    val age: Int = 0,
    val weightKg: Float = 0f,
    val heightCm: Float = 0f,
    val firstName: String = "",
    val lastName: String = "",
    val diseaseInfo: String = "",
    val hasHernia: Boolean = false,
    val hasMeniscus: Boolean = false,
    val activityLevel: String = "medium",
    val goal: String = "general_health",
    val painAreas: List<String> = emptyList(),
    val exerciseLevel: String = "beginner",
    val diseases: List<String> = emptyList(),  // ["meniscus", "hernia"]
    @get:PropertyName("smoker")
    @set:PropertyName("smoker")
    var smoker: Boolean = false,
    @get:PropertyName("drinker")
    @set:PropertyName("drinker")
    var drinker: Boolean = false,
    val expertId: String = "",     // Hastanın bağlı olduğu uzmanın UID'si
    val status: String = "ACTIVE", // "ACTIVE" | "PASSIVE" | "DELETED"
    
    // --- OYUNLAŞTIRMA VE SOSYAL (FAZ 4) ---
    val xp: Int = 0,
    val level: Int = 1,
    val streak: Int = 0,
    val lastExerciseDate: String = "",
    val xpMultiplier: Float = 1.0f,
    val fcmToken: String = "",
    val badges: List<String> = emptyList(),
    val profileImageUrl: String? = null,
    val gender: String = "", // "MALE", "FEMALE", "OTHER"
    val defaultRestSeconds: Int = 90,

    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// ANTRENMAN RAPORU — workout_reports/{docId}
// =========================================================
data class FirestoreWorkoutReport(
    val userId: String = "",        // Firebase UID
    val exerciseId: String = "",
    val exerciseName: String = "",  // Görüntü için cache
    val score: Int = 0,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val caloriesBurned: Float = 0f,
    val feedback: String = "",
    @ServerTimestamp val timestamp: Date? = null
)

// =========================================================
// GÖREV ATAMALARI — task_assignments/{docId}
// Birden fazla egzersizi içerebilir
// =========================================================
data class FirestoreExerciseItem(
    val exerciseType: String = "",
    val targetType: String = "REPS", // "REPS" | "DURATION"
    val targetReps: Int? = null,
    val targetDurationSeconds: Int? = null,
    val actualReps: Int? = null,
    val actualDurationSeconds: Int? = null,
    val sets: Int = 1,
    val completedSets: Int = 0,
    val restTimeSeconds: Int? = null,
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val category: String = "STRENGTH", // REHAB, STRENGTH, CARDIO
    val videoUrl: String? = null,
    val status: String = "PENDING"
)

data class FirestoreTaskAssignment(
    val patientId: String = "",
    val patientName: String = "",
    val expertId: String = "",
    val title: String = "",
    val note: String = "",
    val dueDate: Long = 0L,
    val scheduleType: String = "DAILY", // DAILY, WEEKLY, CUSTOM
    val daysOfWeek: List<Int> = emptyList(), // 1=Mon, ..., 7=Sun
    val autoRepeat: Boolean = false,
    val repeatDurationWeeks: Int? = null,
    val status: String = "PENDING", // "PENDING" | "COMPLETED" | "MISSED"
    val exercises: List<FirestoreExerciseItem> = emptyList(),
    val expertNote: String? = null,
    @ServerTimestamp val createdAt: Date? = null,
    val completedAt: Long? = null,
    val updatedAt: Long? = null
)

// =========================================================
// GRUP — groups/{docId}
// =========================================================
data class FirestoreGroup(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val creatorId: String = "",
    @get:com.google.firebase.firestore.PropertyName("isPrivate")
    @set:com.google.firebase.firestore.PropertyName("isPrivate")
    var isPrivate: Boolean = false,
    val coverImageUrl: String? = null,
    val allowMemberPhotoUpload: Boolean = false,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: java.util.Date? = null
)

// =========================================================
// GRUP ÜYELİĞİ — group_members/{docId}
// =========================================================
data class FirestoreGroupMember(
    val groupId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val role: String = "member",
    val joinedAt: Long = 0L,
    val status: String = "active",
    val totalScore: Int = 0,
    val totalCalories: Float = 0f,
    val workoutCount: Int = 0
)

// =========================================================
// AKTİVİTE AKIŞI — activities/{docId} (FAZ 4)
// =========================================================
data class FirestoreActivity(
    val userId: String = "",
    val userName: String = "",
    val activityType: String = "WORKOUT", // "WORKOUT" | "BADGE_EARNED" | "QUEST_COMPLETED"
    val description: String = "",
    val statistics: Map<String, String> = emptyList<Pair<String,String>>().toMap(), // "calories" to "300", "duration" to "15:00"
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    @ServerTimestamp val timestamp: Date? = null
)

// =========================================================
// ROZET TANIMLARI — badges/{docId} (FAZ 4)
// =========================================================
data class FirestoreBadgeDefinition(
    val name: String = "",
    val nameEn: String = "",
    val description: String = "",
    val descriptionEn: String = "",
    val iconUrl: String = "",
    val type: String = "SYSTEM", // "SYSTEM" | "QUEST" | "DOCTOR"
    val category: String = "SQUAT", // ROZETİN İLGİLİ OLDUĞU ALAN
    val targetValue: Int = 100, // Hedef (örn: 100 tekrar)
    val xpReward: Int = 500,
    val createdBy: String = "SYSTEM" // ADMIN UID'si veya SYSTEM
)

// =========================================================
// KULLANICI ROZET İLERLEMESİ — user_badges/{docId} (FAZ 4)
// =========================================================
data class FirestoreUserBadgeProgress(
    val userId: String = "",
    val badgeId: String = "",
    val currentProgress: Int = 0,
    val targetValue: Int = 100,
    @get:PropertyName("isUnlocked")
    @set:PropertyName("isUnlocked")
    var isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

// =========================================================
// HASTA BAĞLANTI İSTEĞİ — patient_requests/{docId}
// =========================================================
data class FirestorePatientRequest(
    val requestId: String = "",    // Döküman ID'si (opsiyonel içerde tutmak için)
    val doctorId: String = "",
    val doctorName: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val patientEmail: String = "",
    val status: String = "pending", // "pending" | "accepted" | "rejected"
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GRUP DAVETİ — group_invites/{docId}
// =========================================================
data class FirestoreRelationshipNotification(
    val id: String = "",
    val expertId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val message: String = "",
    val type: String = "PATIENT_UNLINKED",
    val isDismissed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class FirestoreGroupInvite(
    val groupId: String = "",
    val groupName: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val toUserId: String = "",
    val toUserEmail: String = "",
    val status: String = "PENDING", // "PENDING" | "ACCEPTED" | "REJECTED"
    @ServerTimestamp val sentAt: Date? = null
)

// =========================================================
// GRUP KATILMA İSTEĞİ — group_join_requests/{docId}
// =========================================================
data class FirestoreGroupJoinRequest(
    val userId: String = "",
    val userName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val creatorId: String = "", // Talebin gideceği kişi (Admin)
    val status: String = "PENDING", // "PENDING" | "ACCEPTED" | "REJECTED"
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GÖREV İLERLEMESİ — task_progress/{taskId}_{periodKey}
// =========================================================
data class FirestoreTaskProgress(
    val taskId: String = "",
    val patientId: String = "",
    val periodKey: String = "", // "2026-05-01" or "2026-W18"
    val exercises: List<FirestoreExerciseItem> = emptyList(),
    val status: String = "PENDING",
    val updatedAt: Long = System.currentTimeMillis()
)

// =========================================================
// UZMAN NOTLARI — expert_notes/{docId}
// =========================================================
data class FirestoreExpertNote(
    val id: String = "", // Firestore doküman ID'si
    val expertId: String = "",
    val patientId: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Date? = null
)
// =========================================================
// SOHBET MESAJLARI — chats/{chatId}/messages/{msgId}
// =========================================================
data class FirestoreChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val createdAt: Long = 0L
)
