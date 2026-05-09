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
import kotlinx.coroutines.channels.awaitClose
import android.util.Log

class FirestoreService {

    private val db: FirebaseFirestore = Firebase.firestore

    companion object {
        const val USERS           = "users"
        const val WORKOUT_REPORTS = "workout_reports"
        const val PLANS           = "plans"
        const val TASK_ASSIGNMENTS= "task_assignments"
        const val GROUPS          = "groups"
        const val GROUP_MEMBERS   = "groupMembers"
        const val GROUP_INVITES   = "group_invites"
        const val PATIENT_REQUESTS = "patient_requests"
        const val GROUP_JOIN_REQUESTS = "group_join_requests"
        const val DOCTOR_PATIENTS = "doctor_patients"
        const val TASK_PROGRESS = "task_progress"
        const val EXPERT_NOTES = "expert_notes"
        const val CHATS = "chats"
        const val CHAT_READS = "chat_reads"
        const val RELATIONSHIP_NOTIFICATIONS = "relationship_notifications"
    }

    // =====================================================================
    // KULLANICI
    // =====================================================================

    /** Yeni profil yaz veya mevcut üzerine güncelle (merge ile güvenli). */
    suspend fun saveUserProfile(uid: String, profile: FirestoreUser) {
        db.collection(USERS).document(uid).set(profile).await()
    }

    suspend fun incrementUserXp(userId: String, amount: Int) {
        if (amount <= 0) return
        val userRef = db.collection(USERS).document(userId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentXp = snapshot.getLong("xp")?.toInt() ?: 0
            val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
            
            val newXp = currentXp + amount
            // Basit seviye mantığı: her 1000 XP'de bir seviye
            val newLevel = (newXp / 1000) + 1
            
            transaction.update(userRef, "xp", newXp)
            if (newLevel > currentLevel) {
                transaction.update(userRef, "level", newLevel)
            }
        }.await()
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

    suspend fun updateProfileImageUrl(uid: String, url: String) {
        db.collection(USERS).document(uid).update("profileImageUrl", url).await()
    }

    /** İsteği kabul et ve ilişkiyi kur (Transaction). */
    suspend fun acceptConnectionRequestTransaction(
        requestId: String,
        doctorId: String,
        patientId: String,
        patientName: String,
        patientEmail: String
    ) {
        db.runTransaction { transaction ->
            // 1. İstek durumunu güncelle
            val requestRef = db.collection(PATIENT_REQUESTS).document(requestId)
            transaction.update(requestRef, "status", "ACCEPTED")

            // 2. doctor_patients koleksiyonuna ekle
            val relationId = "${doctorId}_${patientId}"
            val relationRef = db.collection(DOCTOR_PATIENTS).document(relationId)
            val relationData = mapOf(
                "doctorId" to doctorId,
                "patientId" to patientId,
                "patientName" to patientName,
                "patientEmail" to patientEmail,
                "status" to "active",
                "createdAt" to System.currentTimeMillis()
            )
            transaction.set(relationRef, relationData)

            // 3. Kullanıcı dökümanındaki expertId'yi güncelle
            val userRef = db.collection(USERS).document(patientId)
            transaction.update(userRef, "expertId", doctorId)
        }.await()
    }

    /** Doktor-hasta ilişkisi hala aktif mi kontrol et. */
    suspend fun checkRelationActive(doctorId: String, patientId: String): Boolean {
        val doc = db.collection(USERS).document(patientId).get().await()
        val currentExpertId = doc.getString("expertId") ?: ""
        return currentExpertId == doctorId
    }

    /** Hastanın expertId alanını temizle. */
    suspend fun unlinkPatientFromExpert(patientUid: String) {
        db.collection(USERS).document(patientUid)
            .update("expertId", "").await()
    }

    suspend fun unlinkPatientFromExpertByPatient(patientId: String, oldExpertId: String, patientName: String) {
        val notificationRef = db.collection(RELATIONSHIP_NOTIFICATIONS).document()
        db.runTransaction { transaction ->
            transaction.update(db.collection(USERS).document(patientId), "expertId", "")
            transaction.set(
                db.collection(DOCTOR_PATIENTS).document("${oldExpertId}_$patientId"),
                mapOf(
                    "doctorId" to oldExpertId,
                    "patientId" to patientId,
                    "status" to "removed",
                    "removedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            transaction.set(
                notificationRef,
                FirestoreRelationshipNotification(
                    id = notificationRef.id,
                    expertId = oldExpertId,
                    patientId = patientId,
                    patientName = patientName,
                    message = "${patientName.ifBlank { "Hasta" }} ilişiğini kesti",
                    createdAt = System.currentTimeMillis()
                )
            )
        }.await()
        markRequestsAsRemoved(oldExpertId, patientId)
    }

    suspend fun acceptConnectionRequestWithSingleExpertRule(
        request: FirestorePatientRequest,
        currentExpertId: String?
    ) {
        val patientId = request.patientId
        val newExpertId = request.doctorId
        val oldExpertId = currentExpertId.orEmpty().takeIf { it.isNotBlank() && it != newExpertId }
        val notificationRef = oldExpertId?.let { db.collection(RELATIONSHIP_NOTIFICATIONS).document() }

        db.runTransaction { transaction ->
            transaction.update(db.collection(PATIENT_REQUESTS).document(request.requestId), "status", "ACCEPTED")

            oldExpertId?.let { oldId ->
                transaction.set(
                    db.collection(DOCTOR_PATIENTS).document("${oldId}_$patientId"),
                    mapOf(
                        "doctorId" to oldId,
                        "patientId" to patientId,
                        "status" to "removed",
                        "removedAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                transaction.set(
                    notificationRef!!,
                    FirestoreRelationshipNotification(
                        id = notificationRef.id,
                        expertId = oldId,
                        patientId = patientId,
                        patientName = request.patientName,
                        message = "${request.patientName.ifBlank { "Hasta" }} ilişiğini kesti",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            transaction.set(
                db.collection(DOCTOR_PATIENTS).document("${newExpertId}_$patientId"),
                mapOf(
                    "doctorId" to newExpertId,
                    "patientId" to patientId,
                    "patientName" to request.patientName,
                    "patientEmail" to request.patientEmail,
                    "status" to "active",
                    "createdAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            transaction.update(db.collection(USERS).document(patientId), "expertId", newExpertId)
        }.await()

        oldExpertId?.let { markRequestsAsRemoved(it, patientId) }
    }

    fun observeRelationshipNotifications(expertId: String): kotlinx.coroutines.flow.Flow<List<FirestoreRelationshipNotification>> =
        kotlinx.coroutines.flow.callbackFlow {
            val listener = db.collection(RELATIONSHIP_NOTIFICATIONS)
                .whereEqualTo("expertId", expertId)
                .whereEqualTo("isDismissed", false)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val notifications = snapshot?.documents
                        ?.mapNotNull { doc -> doc.toObject<FirestoreRelationshipNotification>()?.copy(id = doc.id) }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                    trySend(notifications)
                }
            awaitClose { listener.remove() }
        }

    suspend fun dismissRelationshipNotification(notificationId: String) {
        db.collection(RELATIONSHIP_NOTIFICATIONS).document(notificationId)
            .update("isDismissed", true).await()
    }

    /** Uzmanın hastalarını listele. */
    suspend fun getPatientsByExpert(expertUid: String): List<FirestoreUser> {
        // Artık doctor_patients koleksiyonundan çekiyoruz
        val relations = db.collection(DOCTOR_PATIENTS)
            .whereEqualTo("doctorId", expertUid)
            .whereEqualTo("status", "active")
            .get().await()

        val patients = mutableListOf<FirestoreUser>()
        for (doc in relations.documents) {
            val pId = doc.getString("patientId") ?: continue
            val userDoc = db.collection(USERS).document(pId).get().await()
            val user = userDoc.toObject<FirestoreUser>()
            if (user != null) {
                patients.add(user)
            }
        }
        return patients
    }

    /** E-posta ile canlı hasta arama (Prefix search). */
    suspend fun searchPatientsByEmail(query: String): List<FirestoreUser> {
        // 'role' filtresini kaldırdık çünkü composite index gerektiriyor. 
        // ViewModel tarafında filtreleme yapacağız.
        return db.collection(USERS)
            .whereGreaterThanOrEqualTo("email", query)
            .whereLessThanOrEqualTo("email", query + "\uf8ff")
            .limit(20)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreUser>() }
    }

    // =====================================================================
    // UZMAN NOTLARI
    // =====================================================================
    
    suspend fun getExpertNotes(patientId: String, expertId: String): List<FirestoreExpertNote> {
        return db.collection(EXPERT_NOTES)
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("expertId", expertId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreExpertNote>()?.copy(id = it.id) }
    }

    suspend fun addExpertNote(note: FirestoreExpertNote): String {
        val ref = db.collection(EXPERT_NOTES).document()
        val noteWithId = note.copy(id = ref.id)
        ref.set(noteWithId).await()
        return ref.id
    }

    // =====================================================================
    // SOHBET (MESAJLAŞMA)
    // =====================================================================
    
    private fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    private fun getChatReadId(chatId: String, userId: String): String = "${chatId}_$userId"

    fun observeMessages(uid1: String, uid2: String): kotlinx.coroutines.flow.Flow<List<FirestoreChatMessage>> = kotlinx.coroutines.flow.callbackFlow {
        val chatId = getChatId(uid1, uid2)
        val listener = db.collection(CHATS).document(chatId).collection("messages")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject<FirestoreChatMessage>()?.copy(id = doc.id)
                    }
                    trySend(messages)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(uid1: String, uid2: String, messageText: String, senderId: String) {
        val chatId = getChatId(uid1, uid2)
        val receiverId = if (senderId == uid1) uid2 else uid1
        val message = FirestoreChatMessage(
            senderId = senderId,
            receiverId = receiverId,
            message = messageText,
            createdAt = System.currentTimeMillis()
        )
        val ref = db.collection(CHATS).document(chatId).collection("messages").document()
        ref.set(message.copy(id = ref.id)).await()
    }

    suspend fun markChatAsRead(currentUid: String, otherUid: String) {
        if (currentUid.isBlank() || otherUid.isBlank()) return
        val chatId = getChatId(currentUid, otherUid)
        db.collection(CHAT_READS)
            .document(getChatReadId(chatId, currentUid))
            .set(
                mapOf(
                    "chatId" to chatId,
                    "userId" to currentUid,
                    "lastReadAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    fun observeUnreadChatPartnerIds(currentUid: String): kotlinx.coroutines.flow.Flow<Set<String>> =
        kotlinx.coroutines.flow.callbackFlow {
            val latestIncomingByPartner = mutableMapOf<String, Pair<String, Long>>()
            val readAtByChatId = mutableMapOf<String, Long>()

            fun emitUnread() {
                val unreadPartnerIds = latestIncomingByPartner
                    .filter { (_, chatAndTime) ->
                        val (chatId, latestIncomingAt) = chatAndTime
                        latestIncomingAt > (readAtByChatId[chatId] ?: 0L)
                    }
                    .keys
                    .toSet()
                trySend(unreadPartnerIds)
            }

            val messageListener = db.collectionGroup("messages")
                .whereEqualTo("receiverId", currentUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Index henüz oluşturulmamış olabilir (FAILED_PRECONDITION).
                        // Uygulamanın çökmesini önlemek için hata loglanıp boş set döndürülür.
                        android.util.Log.w(
                            "FirestoreService",
                            "observeUnreadChatPartnerIds: messageListener hatası (Firestore index gerekiyor olabilir): ${error.message}"
                        )
                        trySend(emptySet())
                        return@addSnapshotListener
                    }
                    latestIncomingByPartner.clear()
                    snapshot?.documents.orEmpty().forEach { doc ->
                        val message = doc.toObject<FirestoreChatMessage>() ?: return@forEach
                        val chatId = doc.reference.parent.parent?.id ?: return@forEach
                        val previous = latestIncomingByPartner[message.senderId]?.second ?: 0L
                        if (message.createdAt > previous) {
                            latestIncomingByPartner[message.senderId] = chatId to message.createdAt
                        }
                    }
                    emitUnread()
                }

            val readListener = db.collection(CHAT_READS)
                .whereEqualTo("userId", currentUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    readAtByChatId.clear()
                    snapshot?.documents.orEmpty().forEach { doc ->
                        val chatId = doc.getString("chatId") ?: return@forEach
                        readAtByChatId[chatId] = doc.getLong("lastReadAt") ?: 0L
                    }
                    emitUnread()
                }

            awaitClose {
                messageListener.remove()
                readListener.remove()
            }
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
    suspend fun getTaskProgress(taskId: String, periodKey: String): FirestoreTaskProgress? {
        val docId = "${taskId}_${periodKey}"
        return try {
            db.collection(TASK_PROGRESS).document(docId).get().await().toObject(FirestoreTaskProgress::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateTaskProgress(progress: FirestoreTaskProgress) {
        val docId = "${progress.taskId}_${progress.periodKey}"
        db.collection(TASK_PROGRESS).document(docId).set(progress).await()
    }

    suspend fun createTask(task: FirestoreTaskAssignment): String {
        return db.collection(TASK_ASSIGNMENTS).add(task).await().id
    }

    /** Görev durumunu günceller (IN_PROGRESS, DONE veya MISSED). */
    suspend fun updateTaskStatus(taskDocId: String, status: String, completedAt: Long? = null, exercises: List<FirestoreExerciseItem>? = null, expertNote: String? = null) {
        val updates = mutableMapOf<String, Any>("status" to status)
        completedAt?.let { updates["completedAt"] = it }
        exercises?.let { updates["exercises"] = it }
        expertNote?.let { updates["expertNote"] = it }
        db.collection(TASK_ASSIGNMENTS).document(taskDocId).update(updates).await()
    }

    /** Görevi tamamen günceller (Düzenleme ekranı için). */
    suspend fun updateTask(taskDocId: String, task: FirestoreTaskAssignment) {
        db.collection(TASK_ASSIGNMENTS).document(taskDocId).set(task, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    /** Görevi Firestore'dan siler. */
    suspend fun deleteTask(taskDocId: String) {
        db.collection(TASK_ASSIGNMENTS).document(taskDocId).delete().await()
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

    /** Hastanın görevlerini Firestore'dan canlı izle. */
    fun observeTasksForPatient(patientUid: String): kotlinx.coroutines.flow.Flow<List<Pair<String, FirestoreTaskAssignment>>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = db.collection(TASK_ASSIGNMENTS)
            .whereEqualTo("patientId", patientUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    val model = doc.toObject<FirestoreTaskAssignment>() ?: return@mapNotNull null
                    Pair(doc.id, model)
                } ?: emptyList()
                trySend(tasks)
            }
        awaitClose { listener.remove() }
    }

    /** Uzmanın atadığı görevleri Firestore'dan çek. */
    suspend fun getTasksForExpert(expertUid: String): List<Pair<String, FirestoreTaskAssignment>> {
        return db.collection(TASK_ASSIGNMENTS)
            .whereEqualTo("expertId", expertUid)
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreTaskAssignment>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    /** Uzmanın atadığı görevleri Firestore'dan canlı izle. */
    fun observeTasksForExpert(expertUid: String): kotlinx.coroutines.flow.Flow<List<Pair<String, FirestoreTaskAssignment>>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = db.collection(TASK_ASSIGNMENTS)
            .whereEqualTo("expertId", expertUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    val model = doc.toObject<FirestoreTaskAssignment>() ?: return@mapNotNull null
                    Pair(doc.id, model)
                } ?: emptyList()
                trySend(tasks)
            }
        awaitClose { listener.remove() }
    }

    /** Uzmanın belirli bir hastaya atadığı aktif görevleri pasif yap. */
    suspend fun deactivateTasksByDoctor(doctorId: String, patientId: String) {
        val tasks = db.collection(TASK_ASSIGNMENTS)
            .whereEqualTo("expertId", doctorId)
            .whereEqualTo("patientId", patientId)
            .get().await()
        
        tasks.documents.forEach { doc ->
            val status = doc.getString("status") ?: ""
            if (status == "PENDING" || status == "IN_PROGRESS") {
                doc.reference.update("status", "inactive").await()
            }
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
    suspend fun sendConnectionRequest(request: FirestorePatientRequest) {
        val doc = db.collection(PATIENT_REQUESTS).document()
        val finalRequest = request.copy(requestId = doc.id)
        doc.set(finalRequest).await()
    }

    /** Hastaya gelen bekleyen (pending) istekleri listele. */
    suspend fun getPendingRequestsForPatient(patientId: String): List<FirestorePatientRequest> {
        return db.collection(PATIENT_REQUESTS)
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("status", "pending")
            .get().await()
            .documents.mapNotNull { it.toObject<FirestorePatientRequest>() }
    }

    /** Hastaya gelen bekleyen istekleri canlı izle. */
    fun observePendingRequestsForPatient(patientId: String): kotlinx.coroutines.flow.Flow<List<FirestorePatientRequest>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = db.collection(PATIENT_REQUESTS)
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { it.toObject<FirestorePatientRequest>() } ?: emptyList()
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    /** Uzmanın gönderdiği istekleri listele. */
    suspend fun getSentRequestsByDoctor(doctorId: String): List<FirestorePatientRequest> {
        return db.collection(PATIENT_REQUESTS)
            .whereEqualTo("doctorId", doctorId)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestorePatientRequest>() }
    }

    /** Uzmanın gönderdiği istekleri canlı izle. */
    fun observeSentRequestsByDoctor(doctorId: String): kotlinx.coroutines.flow.Flow<List<FirestorePatientRequest>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = db.collection(PATIENT_REQUESTS)
            .whereEqualTo("doctorId", doctorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { it.toObject<FirestorePatientRequest>() } ?: emptyList()
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    /** İsteği güncelle (accepted / rejected). */
    suspend fun updateRequestStatus(requestId: String, status: String) {
        db.collection(PATIENT_REQUESTS).document(requestId)
            .update("status", status).await()
    }

    /** Gönderilen isteği iptal et (sil). */
    suspend fun deleteConnectionRequest(requestId: String) {
        db.collection(PATIENT_REQUESTS).document(requestId).delete().await()
    }

    /** İlgili doktor ve hasta arasındaki tüm istekleri 'removed' yap. */
    suspend fun markRequestsAsRemoved(doctorId: String, patientId: String) {
        val requests = db.collection(PATIENT_REQUESTS)
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("patientId", patientId)
            .get().await()
        
        requests.documents.forEach { doc ->
            doc.reference.update("status", "removed").await()
        }
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

    suspend fun incrementBadgeProgress(userId: String, badgeId: String, increment: Int, target: Int, xpReward: Int = 0) {
        val docRef = db.collection("user_badges").document("${userId}_${badgeId}")
        
        // Önce mevcut durumu oku
        val snapshot = docRef.get().await()
        
        if (snapshot.exists()) {
            val current = snapshot.getLong("currentProgress")?.toInt() ?: 0
            val isAlreadyUnlocked = snapshot.getBoolean("isUnlocked") ?: false
            
            if (!isAlreadyUnlocked) {
                val newProgress = current + increment
                val willUnlock = newProgress >= target
                
                val updates = mutableMapOf<String, Any>(
                    "currentProgress" to newProgress
                )
                if (willUnlock) {
                    updates["isUnlocked"] = true
                    updates["unlockedAt"] = System.currentTimeMillis()
                    // Rozet XP'sini ver
                    incrementUserXp(userId, xpReward)
                    android.util.Log.d("BadgeSystem", "🏆 Rozet açıldı! +$xpReward XP eklendi.")
                }
                docRef.update(updates).await()
                android.util.Log.d("BadgeEval", "Belge güncellendi: $badgeId progress=$newProgress unlocked=$willUnlock")
            }
        } else {
            val willUnlock = increment >= target
            val progressData = mapOf(
                "userId" to userId,
                "badgeId" to badgeId,
                "currentProgress" to increment,
                "targetValue" to target,
                "isUnlocked" to willUnlock,
                "unlockedAt" to if (willUnlock) System.currentTimeMillis() else null
            )
            if (willUnlock) {
                incrementUserXp(userId, xpReward)
            }
            docRef.set(progressData).await()
            android.util.Log.d("BadgeEval", "Yeni belge oluşturuldu: $badgeId progress=$increment unlocked=$willUnlock")
        }
    }

    /** Sistemdeki tüm rozet tanımlarını getirir. */
    suspend fun getBadgeDefinitions(): List<Pair<String, FirestoreBadgeDefinition>> {
        return db.collection("badges")
            .get().await()
            .documents.mapNotNull { doc ->
                val model = doc.toObject<FirestoreBadgeDefinition>() ?: return@mapNotNull null
                Pair(doc.id, model)
            }
    }

    /**
     * Yeni rozet tanımlandığında (veya kategori değiştirildiğinde) çağrılır.
     * Geçmiş workout_reports verilerine bakarak zaten hak etmiş tüm kullanıcılara rozeti verir.
     * Admin'in bir rozeti sonradan tanımlaması halinde kullanıcıların egzersizi yeniden yapmasına
     * gerek kalmaz.
     */
    suspend fun evaluateBadgeRetroactively(badgeId: String, badge: FirestoreBadgeDefinition) {
        android.util.Log.d("BadgeRetro", "=== Retroaktif Değlendirme Başladı: badge=$badgeId category=${badge.category} target=${badge.targetValue} ===")

        val cat = badge.category.trim().uppercase()
        val squatTypes = setOf("SQUAT", "HALF_SQUAT", "JUMP_SQUAT", "BULGARIAN_SPLIT_SQUAT")

        // ÖNEMLİ: document.id kullanılıyor (getString("uid") değil!)
        val patientDocs = db.collection(USERS)
            .whereEqualTo("role", "PATIENT")
            .get().await()
            .documents

        android.util.Log.d("BadgeRetro", "Bulunan hasta doküman sayısı: ${patientDocs.size}")

        for (doc in patientDocs) {
            val userId = doc.id  // ✅ Doküman ID'si = Firebase UID
            android.util.Log.d("BadgeRetro", "Hasta taranıyor: $userId")

            try {
                val reportDocs = db.collection(WORKOUT_REPORTS)
                    .whereEqualTo("userId", userId)
                    .get().await()
                    .documents

                android.util.Log.d("BadgeRetro", "  Rapor sayısı: ${reportDocs.size}")

                var totalProgress = 0
                for (rDoc in reportDocs) {
                    val report = rDoc.toObject<FirestoreWorkoutReport>() ?: continue
                    // exerciseName = displayName (örn. "Squat"), uppercase + normalize et
                    val rawName = report.exerciseName.trim().uppercase()
                        .replace("-", "_").replace(" ", "_")
                    android.util.Log.d("BadgeRetro", "    Rapor: exerciseName='${report.exerciseName}' -> normalized='$rawName' reps=${report.reps} cal=${report.caloriesBurned}")

                    val contribution = when {
                        cat == rawName -> report.reps
                        cat == "SQUAT_ALL" && squatTypes.contains(rawName) -> report.reps
                        cat == "CALORIES" -> report.caloriesBurned.toInt()
                        else -> 0
                    }
                    if (contribution > 0) {
                        android.util.Log.d("BadgeRetro", "    ✅ Katkı: +$contribution (cat=$cat == rawName=$rawName)")
                    } else {
                        android.util.Log.d("BadgeRetro", "    ⏭ Katkı yok (cat=$cat != rawName=$rawName)")
                    }
                    totalProgress += contribution
                }

                android.util.Log.d("BadgeRetro", "  Toplam ilerleme: $totalProgress / Hedef: ${badge.targetValue}")

                if (totalProgress > 0) {
                    val docRef = db.collection("user_badges").document("${userId}_${badgeId}")
                    val existing = docRef.get().await()
                    val alreadyUnlocked = existing.getBoolean("isUnlocked") ?: false

                    if (!alreadyUnlocked) {
                        val willUnlock = totalProgress >= badge.targetValue
                        val data = mapOf(
                            "userId" to userId,
                            "badgeId" to badgeId,
                            "currentProgress" to totalProgress,
                            "targetValue" to badge.targetValue,
                            "isUnlocked" to willUnlock,
                            "unlockedAt" to if (willUnlock) System.currentTimeMillis() else null
                        )
                        if (willUnlock) {
                            // Eğer daha önce açılmamışsa XP ver (tekrar XP vermemek için kontrol)
                            val existingProgress = db.collection(USERS).document(userId)
                                .collection("user_badges").document(badgeId)
                                .get().await().toObject<FirestoreUserBadgeProgress>()
                                
                            if (existingProgress == null || !existingProgress.isUnlocked) {
                                incrementUserXp(userId, badge.xpReward)
                                android.util.Log.d("BadgeRetro", "User $userId earned retroactive badge: ${badge.name}. Awarded ${badge.xpReward} XP.")
                            }
                        }
                        
                        docRef.set(data).await()
                        android.util.Log.d("BadgeRetro", if (willUnlock) "  🏆 ROZET VERİLDİ: $userId" else "  📊 İlerleme kaydedildi: $totalProgress")
                    } else {
                        android.util.Log.d("BadgeRetro", "  ⏭ Zaten açık, atlandı")
                    }
                } else {
                    android.util.Log.d("BadgeRetro", "  ⏭ Sıfır ilerleme, kayıt yapılmadı")
                }
            } catch (e: Exception) {
                android.util.Log.e("BadgeRetro", "  HATA (userId=$userId): ${e.message}", e)
            }
        }
        android.util.Log.d("BadgeRetro", "=== Retroaktif Değlendirme TAMAMLANDI ===")
    }

    /** Yeni bir rozet tanımı oluşturur. */
    suspend fun createBadgeDefinition(badge: FirestoreBadgeDefinition): String {
        return db.collection("badges").add(badge).await().id
    }

    /** Bir rozet tanımını siler. */
    suspend fun deleteBadgeDefinition(badgeId: String) {
        db.collection("badges").document(badgeId).delete().await()
    }

    /** Rozet tanımını günceller. */
    suspend fun updateBadgeDefinition(badgeId: String, updates: Map<String, Any>) {
        db.collection("badges").document(badgeId).update(updates).await()
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

    /** Uzman için hastanın detaylı egzersiz analizini getir. */
    suspend fun getPatientDetailedAnalysis(patientId: String): Map<String, Any?> {
        val reports = db.collection(WORKOUT_REPORTS)
            .whereEqualTo("userId", patientId)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreWorkoutReport>() }

        val exerciseStats = reports.groupBy { it.exerciseName }
            .mapValues { entry ->
                val avgScore = entry.value.map { it.score }.average()
                val totalReps = entry.value.sumOf { it.reps }
                val totalDuration = entry.value.sumOf { it.durationSeconds }
                mapOf(
                    "avgScore" to avgScore,
                    "totalReps" to totalReps,
                    "totalDuration" to totalDuration,
                    "sessionCount" to entry.value.size
                )
            }

        return mapOf(
            "exerciseStats" to exerciseStats,
            "totalWorkouts" to reports.size,
            "lastActive" to (reports.maxByOrNull { it.timestamp?.time ?: 0 }?.timestamp)
        )
    }

    /** Görev durumunu detaylı olarak güncelle (Partial Progress dahil). */
    suspend fun updateTaskStatusWithNote(taskId: String, status: String, expertNote: String? = null) {
        val updates = mutableMapOf<String, Any>("status" to status)
        expertNote?.let { updates["expertNote"] = it }
        updates["updatedAt"] = System.currentTimeMillis()
        
        db.collection(TASK_ASSIGNMENTS).document(taskId)
            .update(updates).await()
    }
    /** Toplam yakılan kalori miktarını (tüm zamanlar) topla. */
    suspend fun getTotalCaloriesBurned(): Float {
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

    suspend fun getAllUsers(limit: Long = 100): List<FirestoreUser> {
        return db.collection(USERS)
            .limit(limit)
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreUser>() }
    }

    /** Kullanıcının rolünü günceller (Örn: PATIENT -> EXPERT) */
    suspend fun updateUserRole(uid: String, newRole: String) {
        db.collection(USERS).document(uid).update("role", newRole).await()
    }

    /** Kullanıcının durumunu günceller (ACTIVE, PASSIVE, DELETED vb.) */
    suspend fun updateUserStatus(uid: String, newStatus: String) {
        db.collection(USERS).document(uid).update("status", newStatus).await()
    }

    suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>) {
        db.collection(GROUPS).document(groupId).update(settings).await()
    }

    /** Sistemdeki tüm grupları getir (Admin için). */
    suspend fun getAllGroupsAdmin(): List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroup> {
        return db.collection(GROUPS)
            .get().await()
            .documents.mapNotNull { it.toObject<com.example.exerciseformanalyzer.model.firestore.FirestoreGroup>() }
    }

    /** Bir grubu ve tüm alt koleksiyonlarını (istatistikler vb.) temizle. */
    suspend fun deleteGroupAdmin(groupId: String) {
        // Not: Gerçek bir uygulamada üye listesi vb. alt koleksiyonlar da temizlenmeli.
        db.collection(GROUPS).document(groupId).delete().await()
    }

    /** Gruba doğrudan üye ekle (Üst seviye koleksiyona). */
    suspend fun addMemberToGroup(groupId: String, userId: String, role: String = "member") {
        val userProfile = getUserProfile(userId)
        val member = com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember(
            groupId = groupId,
            userId = userId,
            userName = userProfile?.fullName ?: "Bilinmeyen",
            userEmail = userProfile?.email ?: "",
            role = role,
            joinedAt = System.currentTimeMillis(),
            status = "active"
        )
        // CommunityFirestoreService ile uyumlu ID formatı: groupId_userId
        db.collection(GROUP_MEMBERS).document("${groupId}_${userId}").set(member).await()
    }

    /** Üyeyi gruptan tamamen çıkar. */
    suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        db.collection(GROUP_MEMBERS).document("${groupId}_${userId}").delete().await()
    }

    /** Gruptaki üyenin yetkisini (Moderator/Member) güncelle. */
    suspend fun updateMemberRole(groupId: String, userId: String, newRole: String) {
        db.collection(GROUP_MEMBERS).document("${groupId}_${userId}").update("role", newRole).await()
    }

    /** Grubun yöneticisini (Kurucu) değiştir. Eski yöneticiyi üyeye düşürür. */
    suspend fun changeGroupCreator(groupId: String, newCreatorId: String) {
        db.runTransaction { transaction ->
            val groupRef = db.collection(GROUPS).document(groupId)
            val oldCreatorId = transaction.get(groupRef).getString("creatorId") ?: ""
            
            // 1. Eski yöneticinin rolünü "member" yap
            if (oldCreatorId.isNotEmpty()) {
                val oldMemberRef = db.collection(GROUP_MEMBERS).document("${groupId}_${oldCreatorId}")
                transaction.update(oldMemberRef, "role", "member")
            }
            
            // 2. Yeni yöneticinin (Kurucu) mülkiyetini ve rolünü güncelle
            val newMemberRef = db.collection(GROUP_MEMBERS).document("${groupId}_${newCreatorId}")
            transaction.update(groupRef, "creatorId", newCreatorId)
            transaction.update(newMemberRef, "role", "admin")
        }.await()
    }

    /** Admin için sistem geneli detaylı analiz verilerini getir. */
    suspend fun getAdminDetailedStats(): Map<String, Any> {
        val allReports = db.collection(WORKOUT_REPORTS)
            .limit(2000) // Performans için limitli
            .get().await()
            .documents.mapNotNull { it.toObject<FirestoreWorkoutReport>() }

        val allUsers = db.collection(USERS).get().await()
            .documents.mapNotNull { it.toObject<FirestoreUser>() }

        // 1. Rol Dağılımı (PATIENT, EXPERT, ADMIN)
        val roleDistribution = allUsers.groupBy { it.role }.mapValues { it.value.size }

        // 2. Antrenman Trendi (Son 7 gün)
        val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val workoutTrend = allReports
            .filter { (it.timestamp?.time ?: 0) >= sevenDaysAgo }
            .groupBy { it.timestamp?.let { d -> sdf.format(d) } ?: "???" }
            .mapValues { it.value.size }
            .toList()
            .sortedBy { it.first }

        // 3. Egzersiz Popülerliği
        val exercisePopularity = allReports.groupBy { it.exerciseName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        return mapOf(
            "roleDistribution" to roleDistribution,
            "workoutTrend" to workoutTrend,
            "exercisePopularity" to exercisePopularity
        )
    }
}
