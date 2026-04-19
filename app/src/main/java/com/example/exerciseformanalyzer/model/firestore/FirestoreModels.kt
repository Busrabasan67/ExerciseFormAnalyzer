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
// PLAN — plans/{docId}
// =========================================================
data class FirestorePlan(
    val expertId: String = "",
    val patientId: String = "",
    val title: String = "",
    val description: String = "",
    val assignedDate: Long = 0L,
    val dueDate: Long = 0L,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GÖREV ATAMALARI — task_assignments/{docId}
// =========================================================
data class FirestoreTaskAssignment(
    val planId: String = "",
    val patientId: String = "",
    val exerciseId: String = "",
    val exerciseName: String = "",
    val targetReps: Int = 0,
    val targetDurationSec: Int = 0,
    val dueDate: Long = 0L,
    val status: String = "PENDING", // "PENDING" | "DONE" | "MISSED"
    val completedAt: Long? = null,
    val reportId: String? = null,
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
