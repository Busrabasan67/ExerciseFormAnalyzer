package com.example.exerciseformanalyzer.data.remote

// FirestoreService — Cloud Firestore CRUD işlemleri
// Koleksiyon referansları sabit; magic string kullanılmıyor.
// Her metod pair döner: (docId, model) — docId Room'daki firebaseDocId alanına yazılır.

import com.example.exerciseformanalyzer.model.firestore.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.AggregateSource
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
        const val GROUP_INVITES   = "group_invites"
        const val CONNECTION_REQUESTS = "connection_requests"
        const val GROUP_JOIN_REQUESTS = "group_join_requests"
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
        val snapshot = db.collection(GROUP_MEMBERS)
            .whereEqualTo("groupId", groupDocId)
            .whereEqualTo("userId", userUid)
            .get().await()
            
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }
    }

    /** Keşfedilebilir grupları listeler. */
    suspend fun getExploreGroups(): List<Pair<String, FirestoreGroup>> {
        return db.collection(GROUPS)
            .limit(100)
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreGroup>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    /** Bir gruba davet gönderir. */
    suspend fun sendGroupInvite(invite: FirestoreGroupInvite): String {
        return db.collection(GROUP_INVITES).add(invite).await().id
    }

    /** Kullanıcıya gelen bekleyen grup davetlerini getir. */
    suspend fun getInvitesForUser(userId: String): List<Pair<String, FirestoreGroupInvite>> {
        return db.collection(GROUP_INVITES)
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("status", "PENDING")
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreGroupInvite>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    /** E-posta ile kullanıcı ara (Davet için). */
    suspend fun findAnyUserByEmail(email: String): FirestoreUser? {
        return db.collection(USERS)
            .whereEqualTo("email", email)
            .limit(1)
            .get().await()
            .documents.firstOrNull()?.toObject<FirestoreUser>()
    }

    /** Davete cevap ver (ACCEPTED/REJECTED). */
    suspend fun respondToGroupInvite(inviteId: String, status: String) {
        db.collection(GROUP_INVITES).document(inviteId).update("status", status).await()
    }

    /** Katılma isteği gönder. */
    suspend fun sendGroupJoinRequest(request: FirestoreGroupJoinRequest): String {
        return db.collection(GROUP_JOIN_REQUESTS).add(request).await().id
    }

    /** Yönetici için grubun bekleyen katılım isteklerini getir. */
    suspend fun getJoinRequestsForGroup(groupId: String): List<Pair<String, FirestoreGroupJoinRequest>> {
        return db.collection(GROUP_JOIN_REQUESTS)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("status", "PENDING")
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreGroupJoinRequest>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    /** Katılma isteğine cevap ver. */
    suspend fun respondToGroupJoinRequest(requestId: String, status: String) {
        db.collection(GROUP_JOIN_REQUESTS).document(requestId).update("status", status).await()
    }

    /** Grubun tüm üyelerini listele (Sıralama olmayan, tüm liste). */
    suspend fun getGroupMembers(groupId: String): List<FirestoreGroupMember> {
        return db.collection(GROUP_MEMBERS)
            .whereEqualTo("groupId", groupId)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreGroupMember>() }
    }

    // =====================================================================
    // BAĞLANTI İSTEKLERİ
    // =====================================================================

    /** Bir uzmandan hastaya bağlantı isteği gönderir. */
    suspend fun sendConnectionRequest(request: FirestoreConnectionRequest) {
        db.collection(CONNECTION_REQUESTS).add(request).await()
    }

    /** Hastaya gelen bekleyen (PENDING) istekleri listele. */
    suspend fun getPendingRequestsForPatient(patientEmail: String): List<Pair<String, FirestoreConnectionRequest>> {
        return db.collection(CONNECTION_REQUESTS)
            .whereEqualTo("toPatientEmail", patientEmail)
            .whereEqualTo("status", "PENDING")
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreConnectionRequest>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    /** İsteği güncelle (ACCEPTED / REJECTED). */
    suspend fun updateRequestStatus(requestId: String, status: String) {
        db.collection(CONNECTION_REQUESTS).document(requestId)
            .update("status", status).await()
    }
    // =====================================================================
    // SOSYAL VE OYUNLAŞTIRMA (FAZ 4)
    // =====================================================================

    /** Aktivite akışına yeni olay ekle. */
    suspend fun createActivity(activity: FirestoreActivity): String {
        return db.collection("activities").add(activity).await().id
    }

    /** Rozet ilerlemesini güncelle veya yeni oluştur. */
    suspend fun updateBadgeProgress(userId: String, badgeId: String, progress: Int, isUnlocked: Boolean) {
        val docId = "${userId}_$badgeId"
        val data = mapOf(
            "userId" to userId,
            "badgeId" to badgeId,
            "currentProgress" to progress,
            "isUnlocked" to isUnlocked
        )
        db.collection("user_badges").document(docId).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    /** Son aktiviteleri çek. */
    suspend fun getRecentActivities(limit: Long = 20): List<FirestoreActivity> {
        return db.collection("activities")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreActivity>() }
    }

    /** Liderlik tablosu: XP'ye göre sırala. */
    suspend fun getGlobalLeaderboard(limit: Long = 50): List<FirestoreUser> {
        return db.collection(USERS)
            .whereEqualTo("role", "PATIENT")
            .orderBy("xp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreUser>() }
    }

    /** Kullanıcının rozet ilerlemelerini çek. */
    suspend fun getUserBadges(userId: String): List<FirestoreUserBadgeProgress> {
        return db.collection("user_badges")
            .whereEqualTo("userId", userId)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreUserBadgeProgress>() }
    }

    // =====================================================================
    // ADMIN FONKSİYONLARI (AGREGASYON)
    // =====================================================================

    /** Belirli bir roldeki toplam kullanıcı sayısını getir. */
    suspend fun getUserCountByRole(role: String): Int {
        return db.collection(USERS)
            .whereEqualTo("role", role)
            .count().get(AggregateSource.SERVER).await().count.toInt()
    }

    /** Bugünkü toplam antrenman sayısını getir. */
    suspend fun getDailyWorkoutCount(): Int {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.time
        
        return db.collection(WORKOUT_REPORTS)
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .count().get(AggregateSource.SERVER).await().count.toInt()
    }

    /** Toplam yakılan kalori miktarını (tüm zamanlar) topla. */
    suspend fun getTotalCaloriesBurned(): Float {
        // Firestore aggregate sum() şu an için sınırlı veya yeni.
        // Prototip için tüm workout'ları çekmek pahalı olabilir ama örnek veri azsa sorun değil.
        // Optimizasyon: Cloud Functions ile bir sayaç tutulabilir.
        // Şimdilik basitleştirmek için son 1000 kaydı toplayalım.
        return db.collection(WORKOUT_REPORTS)
            .limit(1000)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreWorkoutReport>() }
            .sumOf { it.caloriesBurned.toDouble() }.toFloat()
    }

    /** Aktif (herkese açık) grup sayısını getir. */
    suspend fun getActiveGroupCount(): Int {
        return db.collection(GROUPS)
            .count().get(AggregateSource.SERVER).await().count.toInt()
    }

    /** Sistemdeki tüm kullanıcıları listele (limitli). */
    suspend fun getAllUsers(limit: Long = 100): List<FirestoreUser> {
        return db.collection(USERS)
            .limit(limit)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreUser>() }
    }
}
