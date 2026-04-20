package com.example.exerciseformanalyzer.data.remote

// FirestoreService — Cloud Firestore CRUD işlemleri
// Koleksiyon referansları sabit; magic string kullanılmıyor.
// Her metod pair döner: (docId, model) — docId Room'daki firebaseDocId alanına yazılır.

import com.example.exerciseformanalyzer.model.firestore.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class FirestoreService {

    private val db: FirebaseFirestore = Firebase.firestore

    companion object {
        const val USERS           = "users"
        const val WORKOUT_REPORTS = "workout_reports"
        const val PLANS           = "plans"
        const val TASK_ASSIGNMENTS= "task_assignments"
        const val GROUPS          = "groups"
        const val GROUP_MEMBERS   = "group_members"
    }

    // =====================================================================
    // KULLANICI
    // =====================================================================

    /** Yeni profil yaz veya mevcut üzerine güncelle (merge ile güvenli). */
    suspend fun saveUserProfile(uid: String, profile: FirestoreUser) {
        db.collection(USERS).document(uid).set(profile).await()
    }

    /** Firestore'dan kullanıcı profilini getir; yoksa null döner. */
    suspend fun getUserProfile(uid: String): FirestoreUser? {
        return db.collection(USERS).document(uid).get().await()
            .toObject<FirestoreUser>()
    }

    /** Email adresiyle sadece PATIENT rolündeki kullanıcıyı ara. */
    suspend fun findUserByEmail(email: String): FirestoreUser? {
        return db.collection(USERS)
            .whereEqualTo("email", email)
            .whereEqualTo("role", "PATIENT") // Sadece hastalar aranabilir
            .limit(1)
            .get().await()
            .documents.firstOrNull()?.toObject<FirestoreUser>()
    }

    /** Hastanın expertId alanını güncelle (uzman-hasta eşleşmesi). */
    suspend fun linkPatientToExpert(patientUid: String, expertUid: String) {
        db.collection(USERS).document(patientUid)
            .update("expertId", expertUid).await()
    }

    /** Uzmanın hastalarını listele. */
    suspend fun getPatientsByExpert(expertUid: String): List<FirestoreUser> {
        return db.collection(USERS)
            .whereEqualTo("expertId", expertUid)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreUser>() }
    }

    // =====================================================================
    // ANTRENMAN RAPORU
    // =====================================================================

    /** Yeni rapor Firestore'a yükler; oluşturulan doc ID'sini döner. */
    suspend fun uploadWorkoutReport(report: FirestoreWorkoutReport): String {
        return db.collection(WORKOUT_REPORTS).add(report).await().id
    }

    /** Kullanıcının tüm raporlarını çek (cihaz değişimi / ilk sync için). */
    suspend fun getWorkoutReportsByUid(userUid: String): List<Pair<String, FirestoreWorkoutReport>> {
        return db.collection(WORKOUT_REPORTS)
            .whereEqualTo("userId", userUid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreWorkoutReport>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    // =====================================================================
    // GÖREV ATAMALARI
    // =====================================================================

    /** Yeni görev oluşturur; doc ID'sini döner. */
    suspend fun createTask(task: FirestoreTaskAssignment): String {
        return db.collection(TASK_ASSIGNMENTS).add(task).await().id
    }

    /** Görev durumunu günceller (IN_PROGRESS, DONE veya MISSED). */
    suspend fun updateTaskStatus(taskDocId: String, status: String, completedAt: Long? = null, exercises: List<FirestoreExerciseItem>? = null) {
        val updates = mutableMapOf<String, Any>("status" to status)
        completedAt?.let { updates["completedAt"] = it }
        exercises?.let { updates["exercises"] = it }
        db.collection(TASK_ASSIGNMENTS).document(taskDocId).update(updates).await()
    }

    /** Hastanın görevlerini Firestore'dan çek. */
    suspend fun getTasksForPatient(patientUid: String): List<Pair<String, FirestoreTaskAssignment>> {
        return db.collection(TASK_ASSIGNMENTS)
            .whereEqualTo("patientId", patientUid)
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreTaskAssignment>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    // =====================================================================
    // GRUP
    // =====================================================================

    /** Yeni grup oluşturur; doc ID'sini döner. */
    suspend fun createGroup(group: FirestoreGroup): String {
        return db.collection(GROUPS).add(group).await().id
    }

    /** Gruba üye ekler. */
    suspend fun joinGroup(member: FirestoreGroupMember): String {
        return db.collection(GROUP_MEMBERS).add(member).await().id
    }

    /** Kullanıcıyı gruptan çıkar. */
    suspend fun leaveGroup(groupDocId: String, userUid: String) {
        db.collection(GROUP_MEMBERS)
            .whereEqualTo("groupId", groupDocId)
            .whereEqualTo("userId", userUid)
            .get().await()
            .documents.forEach { it.reference.delete().await() }
    }
}
