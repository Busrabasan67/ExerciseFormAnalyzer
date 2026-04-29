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
    val isSmoker: Boolean = false,
    val isDrinker: Boolean = false,
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
    val restTimeSeconds: Int = 30,
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val category: String = "STRENGTH", // REHAB, STRENGTH, CARDIO
    val videoUrl: String? = null,
    val status: String = "PENDING"
)

data class FirestoreTaskAssignment(
    val patientId: String = "",
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
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GRUP — groups/{docId}
// =========================================================
data class FirestoreGroup(
    val name: String = "",
    val description: String = "",
    val creatorId: String = "",
    val isPrivate: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GRUP ÜYELİĞİ — group_members/{docId}
// =========================================================
data class FirestoreGroupMember(
    val groupId: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String = "MEMBER",   // "ADMIN" | "MEMBER"
    
    // --- LİDERLİK TABLOSU İÇİN (FAZ 4) ---
    val totalScore: Int = 0,
    val totalCalories: Float = 0f,
    val workoutCount: Int = 0,

    @ServerTimestamp val joinedAt: Date? = null
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
    val description: String = "",
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
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

// =========================================================
// BAĞLANTI İSTEĞİ — connection_requests/{docId}
// =========================================================
data class FirestoreConnectionRequest(
    val fromExpertId: String = "",
    val fromExpertName: String = "",
    val toPatientEmail: String = "",
    val status: String = "PENDING", // "PENDING" | "ACCEPTED" | "REJECTED"
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GRUP DAVETİ — group_invites/{docId}
// =========================================================
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
