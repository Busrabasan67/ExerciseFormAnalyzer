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
    val diseases: List<String> = emptyList(),  // ["meniscus", "hernia"]
    val isSmoker: Boolean = false,
    val isDrinker: Boolean = false,
    val expertId: String = "",     // Hastanın bağlı olduğu uzmanın UID'si
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
    val status: String = "PENDING" // Egzersiz bazlı progres "PENDING", "IN_PROGRESS", "COMPLETED"
)

data class FirestoreTaskAssignment(
    val patientId: String = "",
    val expertId: String = "",
    val title: String = "",
    val note: String = "",
    val dueDate: Long = 0L,
    val status: String = "PENDING", // "PENDING" | "DONE" | "MISSED"
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
    @ServerTimestamp val joinedAt: Date? = null
)
