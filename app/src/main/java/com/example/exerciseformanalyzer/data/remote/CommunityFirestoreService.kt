package com.example.exerciseformanalyzer.data.remote

import com.example.exerciseformanalyzer.model.firestore.FsGroup
import com.example.exerciseformanalyzer.model.firestore.FsGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FsGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FsGroupMessage
import com.example.exerciseformanalyzer.model.firestore.FsGroupProgram
import com.example.exerciseformanalyzer.model.firestore.FirestoreTaskAssignment
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

internal fun resolveFirestoreGroup(
    group: FsGroup,
    documentId: String,
    isPrivateField: Boolean?,
    legacyPrivateField: Boolean?
): FsGroup = group.copy(
    groupId = group.groupId.ifBlank { documentId },
    isPrivate = isPrivateField ?: legacyPrivateField ?: group.isPrivate
)

/**
 * CommunityFirestoreService
 * Topluluk (Grup) özelliği için Firestore işlemlerini yönetir.
 * FirestoreService'den TAMAMEN AYRI — bakım ve test kolaylığı için.
 *
 * Koleksiyonlar:
 *  - groups              : Grup tanımları
 *  - groupMembers        : {groupId}_{userId} — üyelik kayıtları
 *  - groupInvites        : Admin → Kullanıcı davetleri
 *  - groupJoinRequests   : Kullanıcı → Admin katılma istekleri
 */
class CommunityFirestoreService {

    private val db: FirebaseFirestore = Firebase.firestore

    companion object {
        const val GROUPS               = "groups"
        const val GROUP_MEMBERS        = "groupMembers"
        const val GROUP_INVITES        = "groupInvites"
        const val GROUP_JOIN_REQUESTS  = "groupJoinRequests"
        const val GROUP_MESSAGES       = "groupMessages"
        const val GROUP_PROGRAMS       = "groupPrograms"
        const val GROUP_PROGRAM_APPLICATIONS = "groupProgramApplications"
        const val GROUP_NOTIFICATION_READS = "groupNotificationReads"
        const val COMMUNITY_NOTIFICATION_READS = "communityNotificationReads"
        const val TASK_ASSIGNMENTS     = "task_assignments"
        const val USERS                = "users"
    }

    private fun normalizeRole(role: String): String = when (role.lowercase()) {
        "admin" -> "admin"
        "moderator", "authorized", "yetkili" -> "moderator"
        else -> "member"
    }

    private fun canInvite(role: String): Boolean =
        normalizeRole(role) in listOf("admin", "moderator")

    private fun canShareProgram(role: String): Boolean =
        normalizeRole(role) in listOf("admin", "moderator")

    private fun groupFromDocument(document: DocumentSnapshot): FsGroup? =
        document.toObject<FsGroup>()?.let { group ->
            resolveFirestoreGroup(
                group = group,
                documentId = document.id,
                isPrivateField = document.getBoolean("isPrivate"),
                legacyPrivateField = document.getBoolean("private")
            )
        }

    // =====================================================================
    // GRUP OLUŞTUR — batch: group + creator member kaydı aynı anda
    // =====================================================================

    suspend fun createGroupWithAdmin(group: FsGroup, creatorAsMember: FsGroupMember): String {
        val batch = db.batch()

        // groups/{groupId}
        val groupRef = db.collection(GROUPS).document()
        val groupId = groupRef.id
        val finalGroup = group.copy(groupId = groupId)
        batch.set(groupRef, finalGroup)

        // groupMembers/{groupId}_{creatorId}
        val memberRef = db.collection(GROUP_MEMBERS).document("${groupId}_${creatorAsMember.userId}")
        val finalMember = creatorAsMember.copy(groupId = groupId)
        batch.set(memberRef, finalMember)

        batch.commit().await()
        return groupId
    }

    // =====================================================================
    // KEŞFET
    // =====================================================================

    /** Tüm grupları getir (UI katmanında isPrivate filtrelemesi yapılır). */
    suspend fun getAllGroups(): List<FsGroup> {
        return db.collection(GROUPS)
            .limit(100)
            .get().await()
            .documents.mapNotNull { groupFromDocument(it) }
    }

    // =====================================================================
    // ÜYELİK DURUMU SORGULARI
    // =====================================================================

    /** Kullanıcının bu grupta aktif üye olup olmadığını döner. */
    suspend fun isMember(groupId: String, userId: String): Boolean {
        val doc = db.collection(GROUP_MEMBERS)
            .document("${groupId}_${userId}")
            .get().await()
        if (!doc.exists()) return false
        val status = doc.getString("status") ?: return false
        return status == "active"
    }

    /** Kullanıcının bu grup için pending join request'i var mı? */
    suspend fun hasPendingJoinRequest(groupId: String, userId: String): Boolean {
        val doc = db.collection(GROUP_JOIN_REQUESTS)
            .document("${groupId}_${userId}")
            .get().await()
        if (!doc.exists()) return false
        val status = doc.getString("status") ?: return false
        return status == "pending"
    }

    /** Kullanıcıya bu grup için pending invite var mı? */
    suspend fun hasPendingInvite(groupId: String, toUserId: String): Boolean {
        val doc = db.collection(GROUP_INVITES)
            .document("${groupId}_${toUserId}")
            .get().await()
        if (!doc.exists()) return false
        val status = doc.getString("status") ?: return false
        return status == "pending"
    }

    /**
     * Kullanıcının bir gruptaki durumunu tek sorguda döner:
     * "member" | "pendingRequest" | "pendingInvite" | "none"
     */
    suspend fun getUserGroupStatus(groupId: String, userId: String): String {
        if (isMember(groupId, userId)) return "member"
        if (hasPendingJoinRequest(groupId, userId)) return "pendingRequest"
        if (hasPendingInvite(groupId, userId)) return "pendingInvite"
        return "none"
    }

    // =====================================================================
    // PUBLIC GRUP — Direkt Katıl
    // =====================================================================

    /** Public gruba direkt katıl — member kaydı oluştur. */
    suspend fun joinPublicGroup(member: FsGroupMember) {
        require(!member.groupId.isBlank() && !member.userId.isBlank()) {
            "groupId ve userId boş olamaz"
        }
        val docId = "${member.groupId}_${member.userId}"
        db.runTransaction { transaction ->
            val groupRef = db.collection(GROUPS).document(member.groupId)
            val memberRef = db.collection(GROUP_MEMBERS).document(docId)

            val group = groupFromDocument(transaction.get(groupRef))
                ?: throw IllegalStateException("Grup bulunamadı.")
            if (group.isPrivate) {
                throw IllegalStateException("Gizli gruplara katılma isteği göndermeniz gerekiyor.")
            }

            val existingMember = transaction.get(memberRef)
            if (existingMember.exists() && existingMember.getString("status") == "active") {
                throw IllegalStateException("Zaten bu grubun üyesisiniz.")
            }

            transaction.set(memberRef, member)
        }.await()
    }

    // =====================================================================
    // PRIVATE GRUP — Kullanıcı Katılma İsteği
    // =====================================================================

    /**
     * Kullanıcı private gruba katılmak için admin'e istek gönderir.
     * Çift istek koruması: pending request varsa yeni oluşturmaz.
     */
    suspend fun sendJoinRequest(request: FsGroupJoinRequest): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val groupRef = db.collection(GROUPS).document(request.groupId)
                val requestRef = db.collection(GROUP_JOIN_REQUESTS)
                    .document("${request.groupId}_${request.fromUserId}")
                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${request.groupId}_${request.fromUserId}")
                val inviteRef = db.collection(GROUP_INVITES)
                    .document("${request.groupId}_${request.fromUserId}")

                val group = groupFromDocument(transaction.get(groupRef))
                    ?: throw IllegalStateException("Grup bulunamadı.")
                if (!group.isPrivate) {
                    throw IllegalStateException("Herkese açık gruplara direkt katılabilirsiniz.")
                }

                val existingMember = transaction.get(memberRef)
                if (existingMember.exists() && existingMember.getString("status") == "active") {
                    throw IllegalStateException("Zaten bu grubun üyesisiniz.")
                }

                val existingRequest = transaction.get(requestRef)
                if (existingRequest.exists() && existingRequest.getString("status") == "pending") {
                    throw IllegalStateException("Zaten bekleyen bir katılma isteğiniz var.")
                }

                val existingInvite = transaction.get(inviteRef)
                if (existingInvite.exists() && existingInvite.getString("status") == "pending") {
                    throw IllegalStateException("Bu grup için bekleyen bir davetiniz var.")
                }

                val finalRequest = request.copy(
                    requestId = requestRef.id,
                    toAdminId = group.creatorId
                )
                transaction.set(requestRef, finalRequest)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin'in yönettiği gruplara gelen pending katılma isteklerini getir. */
    suspend fun getJoinRequestsForAdmin(adminId: String): List<FsGroupJoinRequest> {
        return db.collection(GROUP_JOIN_REQUESTS)
            .whereEqualTo("toAdminId", adminId)
            .whereEqualTo("status", "pending")
            .get().await()
            .documents.mapNotNull { it.toObject<FsGroupJoinRequest>() }
    }

    /**
     * Admin katılma isteğini kabul eder:
     *  1. groupJoinRequests durumunu "accepted" yap
     *  2. groupMembers'a member olarak ekle (transaction)
     */
    suspend fun acceptJoinRequest(request: FsGroupJoinRequest): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val reqDocId = "${request.groupId}_${request.fromUserId}"
                val requestRef = db.collection(GROUP_JOIN_REQUESTS).document(reqDocId)
                val requestSnapshot = transaction.get(requestRef)
                if (!requestSnapshot.exists()) {
                    throw IllegalStateException("Katılma isteği bulunamadı.")
                }
                if (requestSnapshot.getString("status") != "pending") {
                    throw IllegalStateException("Bu istek daha önce yanıtlanmış.")
                }

                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${request.groupId}_${request.fromUserId}")
                val member = FsGroupMember(
                    groupId = request.groupId,
                    userId = request.fromUserId,
                    userName = request.fromUserName,
                    userEmail = request.fromUserEmail,
                    role = "member",
                    joinedAt = System.currentTimeMillis(),
                    status = "active"
                )
                transaction.update(requestRef, "status", "accepted")
                transaction.set(memberRef, member)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin katılma isteğini reddeder. */
    suspend fun rejectJoinRequest(groupId: String, fromUserId: String): Result<Unit> {
        return try {
            val docId = "${groupId}_${fromUserId}"
            db.collection(GROUP_JOIN_REQUESTS).document(docId)
                .update("status", "rejected").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // PRIVATE GRUP — Admin Daveti
    // =====================================================================

    /** E-posta prefix'i ile kullanıcı ara (autocomplete için). */
    suspend fun searchUsersByEmailPrefix(query: String): List<FirestoreUser> {
        if (query.length < 2) return emptyList()
        return db.collection(USERS)
            .whereGreaterThanOrEqualTo("email", query)
            .whereLessThanOrEqualTo("email", query + "\uf8ff")
            .limit(10)
            .get().await()
            .documents.mapNotNull { doc ->
                doc.toObject<FirestoreUser>()?.copy(uid = doc.id)
            }
    }

    /**
     * Admin kullanıcıya davet gönderir.
     * Çift davet koruması: pending invite varsa yeni gönderilmez.
     */
    suspend fun sendGroupInvite(invite: FsGroupInvite): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val groupRef = db.collection(GROUPS).document(invite.groupId)
                val senderRef = db.collection(GROUP_MEMBERS)
                    .document("${invite.groupId}_${invite.fromUserId}")
                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${invite.groupId}_${invite.toUserId}")
                val inviteRef = db.collection(GROUP_INVITES)
                    .document("${invite.groupId}_${invite.toUserId}")
                val requestRef = db.collection(GROUP_JOIN_REQUESTS)
                    .document("${invite.groupId}_${invite.toUserId}")

                val group = groupFromDocument(transaction.get(groupRef))
                    ?: throw IllegalStateException("Grup bulunamadı.")
                val senderSnapshot = transaction.get(senderRef)
                val senderStatus = senderSnapshot.getString("status") ?: ""
                val senderRole = normalizeRole(senderSnapshot.getString("role") ?: "")
                if (!senderSnapshot.exists() || senderStatus != "active" || !canInvite(senderRole)) {
                    throw IllegalStateException("Bu gruba davet göndermek için yönetici veya yetkili olmalısınız.")
                }

                val existingMember = transaction.get(memberRef)
                if (existingMember.exists() && existingMember.getString("status") == "active") {
                    throw IllegalStateException("Kullanıcı zaten bu grubun üyesi.")
                }

                val existingInvite = transaction.get(inviteRef)
                if (existingInvite.exists() && existingInvite.getString("status") == "pending") {
                    throw IllegalStateException("Bu kullanıcıya zaten bekleyen bir davet gönderilmiş.")
                }

                val existingRequest = transaction.get(requestRef)
                if (existingRequest.exists() && existingRequest.getString("status") == "pending") {
                    throw IllegalStateException("Bu kullanıcının bekleyen katılma isteği var.")
                }

                val finalInvite = invite.copy(inviteId = inviteRef.id)
                transaction.set(inviteRef, finalInvite)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Kullanıcının kendisine gelen pending davetleri getir.
     * Adminin gönderdiği davetler BURAYA DÜŞMEZ — toUserId eşleşmesi şart.
     */
    suspend fun getInvitesForUser(toUserId: String): List<FsGroupInvite> {
        return db.collection(GROUP_INVITES)
            .whereEqualTo("toUserId", toUserId)
            .whereEqualTo("status", "pending")
            .get().await()
            .documents.mapNotNull { it.toObject<FsGroupInvite>() }
    }

    /** Admin'in gönderdiği davetleri getir (Sent Invites listesi için). */
    suspend fun getSentInvitesByAdmin(fromUserId: String): List<FsGroupInvite> {
        return db.collection(GROUP_INVITES)
            .whereEqualTo("fromUserId", fromUserId)
            .get().await()
            .documents.mapNotNull { it.toObject<FsGroupInvite>() }
    }

    /**
     * Kullanıcı daveti kabul eder:
     *  1. groupInvites durumunu "accepted" yap
     *  2. groupMembers'a member olarak ekle (transaction)
     */
    suspend fun acceptGroupInvite(invite: FsGroupInvite): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val inviteDocId = "${invite.groupId}_${invite.toUserId}"
                val inviteRef = db.collection(GROUP_INVITES).document(inviteDocId)
                val inviteSnapshot = transaction.get(inviteRef)
                if (!inviteSnapshot.exists()) {
                    throw IllegalStateException("Davet bulunamadı.")
                }
                if (inviteSnapshot.getString("status") != "pending") {
                    throw IllegalStateException("Bu davet daha önce yanıtlanmış.")
                }

                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${invite.groupId}_${invite.toUserId}")
                val member = FsGroupMember(
                    groupId = invite.groupId,
                    userId = invite.toUserId,
                    userName = invite.toUserName,
                    userEmail = invite.toUserEmail,
                    role = "member",
                    joinedAt = System.currentTimeMillis(),
                    status = "active"
                )
                transaction.update(inviteRef, "status", "accepted")
                transaction.set(memberRef, member)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Kullanıcı daveti reddeder. */
    suspend fun rejectGroupInvite(groupId: String, toUserId: String): Result<Unit> {
        return try {
            val docId = "${groupId}_${toUserId}"
            db.collection(GROUP_INVITES).document(docId)
                .update("status", "rejected").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // ÜYELER
    // =====================================================================

    /** Grubun aktif üyelerini getir (admin en üste sıralanır). */
    suspend fun getGroupMembers(groupId: String): List<FsGroupMember> {
        val members = db.collection(GROUP_MEMBERS)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("status", "active")
            .get().await()
            .documents.mapNotNull { it.toObject<FsGroupMember>() }
            .map { it.copy(role = normalizeRole(it.role)) }
        return members.sortedWith(
            compareByDescending<FsGroupMember> { it.role == "admin" }
                .thenByDescending { it.role == "moderator" }
                .thenBy { it.userName.lowercase() }
        )
    }

    /** Kullanıcıyı gruptan çıkar (status = "removed" yap). */
    suspend fun removeMember(groupId: String, userId: String) {
        db.collection(GROUP_MEMBERS)
            .document("${groupId}_${userId}")
            .update("status", "removed").await()
    }

    suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val groupRef = db.collection(GROUPS).document(groupId)
                val memberRef = db.collection(GROUP_MEMBERS).document("${groupId}_${userId}")

                val group = groupFromDocument(transaction.get(groupRef))
                    ?: throw IllegalStateException("Grup bulunamadı.")
                val member = transaction.get(memberRef)
                val role = normalizeRole(member.getString("role") ?: "")
                if (!member.exists() || member.getString("status") != "active") {
                    throw IllegalStateException("Aktif grup üyeliği bulunamadı.")
                }
                if (role == "admin" || group.creatorId == userId) {
                    throw IllegalStateException("Grup yöneticisi gruptan ayrılamaz. Önce başka bir üyeyi yönetici yapın.")
                }

                transaction.update(memberRef, "status", "removed")
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMemberRole(
        groupId: String,
        actorUserId: String,
        targetUserId: String,
        newRole: String
    ): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val groupRef = db.collection(GROUPS).document(groupId)
                val actorRef = db.collection(GROUP_MEMBERS).document("${groupId}_${actorUserId}")
                val targetRef = db.collection(GROUP_MEMBERS).document("${groupId}_${targetUserId}")

                val actor = transaction.get(actorRef)
                if (!actor.exists() ||
                    actor.getString("status") != "active" ||
                    normalizeRole(actor.getString("role") ?: "") != "admin"
                ) {
                    throw IllegalStateException("Rolleri sadece grup yöneticisi değiştirebilir.")
                }

                val target = transaction.get(targetRef)
                if (!target.exists() || target.getString("status") != "active") {
                    throw IllegalStateException("Üye bulunamadı.")
                }
                if (normalizeRole(target.getString("role") ?: "") == "admin") {
                    throw IllegalStateException("Grup yöneticisinin rolü değiştirilemez.")
                }

                val normalizedNewRole = normalizeRole(newRole)
                if (normalizedNewRole !in listOf("admin", "moderator", "member")) {
                    throw IllegalStateException("Geçersiz rol.")
                }

                if (normalizedNewRole == "admin") {
                    transaction.update(actorRef, "role", "moderator")
                    transaction.update(targetRef, "role", "admin")
                    transaction.update(
                        groupRef,
                        mapOf(
                            "creatorId" to targetUserId,
                            "creatorName" to (target.getString("userName") ?: "")
                        )
                    )
                } else {
                    transaction.update(targetRef, "role", normalizedNewRole)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // GRUPLARIMI — Kullanıcının üye olduğu gruplar
    // =====================================================================

    suspend fun updateGroupPrivacy(
        groupId: String,
        actorUserId: String,
        isPrivate: Boolean
    ): Result<FsGroup> {
        return try {
            if (groupId.isBlank() || actorUserId.isBlank()) {
                throw IllegalArgumentException("Grup veya kullanıcı bilgisi eksik.")
            }

            val (updatedGroup, shouldAcceptPendingRequests) = db.runTransaction { transaction ->
                val groupRef = db.collection(GROUPS).document(groupId)
                val actorRef = db.collection(GROUP_MEMBERS).document("${groupId}_${actorUserId}")

                val group = groupFromDocument(transaction.get(groupRef))
                    ?: throw IllegalStateException("Grup bulunamadı.")
                val actor = transaction.get(actorRef)
                val actorRole = normalizeRole(actor.getString("role") ?: "")
                val isActiveAdmin = actor.exists() &&
                    actor.getString("status") == "active" &&
                    actorRole == "admin"
                if (!isActiveAdmin) {
                    throw IllegalStateException("Grup gizliliğini sadece yönetici değiştirebilir.")
                }

                transaction.update(groupRef, "isPrivate", isPrivate)
                group.copy(isPrivate = isPrivate) to (group.isPrivate && !isPrivate)
            }.await()

            if (shouldAcceptPendingRequests) {
                acceptPendingJoinRequestsForPublicGroup(updatedGroup.groupId)
            }

            Result.success(updatedGroup)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun acceptPendingJoinRequestsForPublicGroup(groupId: String) {
        val pendingRequests = db.collection(GROUP_JOIN_REQUESTS)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("status", "pending")
            .get().await()
            .documents

        pendingRequests.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { requestDoc ->
                val request = requestDoc.toObject<FsGroupJoinRequest>() ?: return@forEach
                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${request.groupId}_${request.fromUserId}")
                val member = FsGroupMember(
                    groupId = request.groupId,
                    userId = request.fromUserId,
                    userName = request.fromUserName,
                    userEmail = request.fromUserEmail,
                    role = "member",
                    joinedAt = System.currentTimeMillis(),
                    status = "active"
                )
                batch.update(requestDoc.reference, "status", "accepted")
                batch.set(memberRef, member)
            }
            batch.commit().await()
        }
    }

    fun observeGroupMessages(groupId: String): Flow<List<FsGroupMessage>> = callbackFlow {
        val registration = db.collection(GROUP_MESSAGES)
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toObject<FsGroupMessage>()?.copy(messageId = it.id) }
                    ?.filter { !it.deleted }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    fun observeGroupPrograms(groupId: String): Flow<List<FsGroupProgram>> = callbackFlow {
        val registration = db.collection(GROUP_PROGRAMS)
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val programs = snapshot?.documents
                    ?.mapNotNull { it.toObject<FsGroupProgram>()?.copy(programId = it.id) }
                    ?.filter { !it.deleted }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
                trySend(programs)
            }
        awaitClose { registration.remove() }
    }

    fun observeAppliedProgramIds(userId: String): Flow<Set<String>> = callbackFlow {
        val registration = db.collection(GROUP_PROGRAM_APPLICATIONS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents
                    ?.mapNotNull { it.getString("programId") }
                    ?.toSet()
                    ?: emptySet()
                trySend(ids)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendTextMessage(message: FsGroupMessage): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${message.groupId}_${message.senderId}")
                val member = transaction.get(memberRef)
                if (!member.exists() || member.getString("status") != "active") {
                    throw IllegalStateException("Mesaj göndermek için grubun aktif üyesi olmalısınız.")
                }

                val messageRef = db.collection(GROUP_MESSAGES).document()
                transaction.set(
                    messageRef,
                    message.copy(
                        messageId = messageRef.id,
                        senderRole = normalizeRole(member.getString("role") ?: "member"),
                        type = "text",
                        text = message.text.trim(),
                        createdAt = System.currentTimeMillis(),
                        deleted = false
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareProgram(program: FsGroupProgram): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val memberRef = db.collection(GROUP_MEMBERS)
                    .document("${program.groupId}_${program.createdById}")
                val member = transaction.get(memberRef)
                val role = normalizeRole(member.getString("role") ?: "")
                if (!member.exists() || member.getString("status") != "active" || !canShareProgram(role)) {
                    throw IllegalStateException("Program paylaşmak için yönetici veya yetkili olmalısınız.")
                }

                val programRef = db.collection(GROUP_PROGRAMS).document()
                val messageRef = db.collection(GROUP_MESSAGES).document()
                val now = System.currentTimeMillis()
                val finalProgram = program.copy(programId = programRef.id, createdAt = now, deleted = false)
                val programMessage = FsGroupMessage(
                    messageId = messageRef.id,
                    groupId = program.groupId,
                    senderId = program.createdById,
                    senderName = program.createdByName,
                    senderRole = role,
                    type = "program",
                    text = program.title,
                    programId = programRef.id,
                    createdAt = now,
                    deleted = false
                )
                transaction.set(programRef, finalProgram)
                transaction.set(messageRef, programMessage)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(groupId: String, actorUserId: String, message: FsGroupMessage): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val actorRef = db.collection(GROUP_MEMBERS).document("${groupId}_${actorUserId}")
                val actor = transaction.get(actorRef)
                if (!actor.exists() ||
                    actor.getString("status") != "active" ||
                    normalizeRole(actor.getString("role") ?: "") != "admin"
                ) {
                    throw IllegalStateException("Mesajları sadece grup yöneticisi silebilir.")
                }

                val messageRef = db.collection(GROUP_MESSAGES).document(message.messageId)
                transaction.update(messageRef, "deleted", true)
                if (message.programId.isNotBlank()) {
                    val programRef = db.collection(GROUP_PROGRAMS).document(message.programId)
                    transaction.update(programRef, "deleted", true)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyProgramToUser(program: FsGroupProgram, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val programRef = db.collection(GROUP_PROGRAMS).document(program.programId)
                val memberRef = db.collection(GROUP_MEMBERS).document("${program.groupId}_${userId}")
                val applicationRef = db.collection(GROUP_PROGRAM_APPLICATIONS)
                    .document("${program.programId}_${userId}")
                val taskRef = db.collection(TASK_ASSIGNMENTS).document()

                val programSnapshot = transaction.get(programRef)
                val latestProgram = programSnapshot.toObject(FsGroupProgram::class.java)
                    ?: throw IllegalStateException("Program bulunamadı.")
                if (latestProgram.deleted) {
                    throw IllegalStateException("Bu program artık kullanılamaz.")
                }

                val member = transaction.get(memberRef)
                if (!member.exists() || member.getString("status") != "active") {
                    throw IllegalStateException("Programı uygulamak için grubun aktif üyesi olmalısınız.")
                }

                val existingApplication = transaction.get(applicationRef)
                if (existingApplication.exists()) {
                    throw IllegalStateException("Bu programı zaten uyguladınız.")
                }

                val task = FirestoreTaskAssignment(
                    patientId = userId,
                    expertId = "GROUP:${latestProgram.groupId}",
                    title = "[${latestProgram.groupName}] ${latestProgram.title}",
                    note = latestProgram.note,
                    dueDate = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L),
                    scheduleType = latestProgram.scheduleType,
                    daysOfWeek = latestProgram.daysOfWeek,
                    autoRepeat = latestProgram.autoRepeat,
                    repeatDurationWeeks = latestProgram.repeatDurationWeeks,
                    status = "PENDING",
                    exercises = latestProgram.exercises
                )
                transaction.set(taskRef, task)
                transaction.set(
                    applicationRef,
                    mapOf(
                        "applicationId" to applicationRef.id,
                        "programId" to latestProgram.programId,
                        "groupId" to latestProgram.groupId,
                        "userId" to userId,
                        "taskId" to taskRef.id,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeAppliedProgramForUser(taskDocId: String, userId: String): Result<Unit> {
        return try {
            if (taskDocId.isBlank() || userId.isBlank()) {
                throw IllegalArgumentException("Program veya kullanıcı bilgisi eksik.")
            }

            val applications = db.collection(GROUP_PROGRAM_APPLICATIONS)
                .whereEqualTo("taskId", taskDocId)
                .whereEqualTo("userId", userId)
                .get().await()
                .documents

            val batch = db.batch()
            applications.forEach { batch.delete(it.reference) }
            batch.update(db.collection(TASK_ASSIGNMENTS).document(taskDocId), "status", "removed")
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasCommunityNotifications(userId: String): Boolean {
        if (userId.isBlank()) return false

        val communityRead = db.collection(COMMUNITY_NOTIFICATION_READS)
            .document(userId)
            .get().await()
        val invitesSeenAt = communityRead.getLong("invitesSeenAt") ?: 0L
        val requestsSeenAt = communityRead.getLong("requestsSeenAt") ?: 0L

        val pendingInvite = db.collection(GROUP_INVITES)
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("status", "pending")
            .get().await()
        if (pendingInvite.documents.any { (it.getDate("createdAt")?.time ?: 0L) > invitesSeenAt }) return true

        val pendingJoinRequest = db.collection(GROUP_JOIN_REQUESTS)
            .whereEqualTo("toAdminId", userId)
            .whereEqualTo("status", "pending")
            .get().await()
        if (pendingJoinRequest.documents.any { (it.getDate("createdAt")?.time ?: 0L) > requestsSeenAt }) return true

        return getUnreadGroupIds(userId).isNotEmpty()
    }

    suspend fun getUnreadGroupIds(userId: String): Set<String> {
        if (userId.isBlank()) return emptySet()

        val activeMemberships = db.collection(GROUP_MEMBERS)
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "active")
            .get().await()
            .documents

        val groupIds = activeMemberships.mapNotNull { it.getString("groupId") }
        if (groupIds.isEmpty()) return emptySet()

        val readDocs = db.collection(GROUP_NOTIFICATION_READS)
            .whereEqualTo("userId", userId)
            .get().await()
            .documents
        val readAtByGroup = readDocs.mapNotNull { doc ->
            val groupId = doc.getString("groupId") ?: return@mapNotNull null
            groupId to (doc.getLong("lastSeenAt") ?: 0L)
        }.toMap()

        val unreadGroupIds = mutableSetOf<String>()
        groupIds.forEach { groupId ->
            val messages = db.collection(GROUP_MESSAGES)
                .whereEqualTo("groupId", groupId)
                .get().await()
                .documents

            val latestMessageAt = messages.mapNotNull { doc ->
                val senderId = doc.getString("senderId").orEmpty()
                val deleted = doc.getBoolean("deleted") ?: false
                val createdAt = doc.getLong("createdAt") ?: 0L
                if (senderId.isNotBlank() && senderId != userId && !deleted) createdAt else null
            }.maxOrNull() ?: 0L

            if (latestMessageAt > (readAtByGroup[groupId] ?: 0L)) {
                unreadGroupIds.add(groupId)
            }
        }

        return unreadGroupIds
    }

    suspend fun markGroupNotificationsSeen(userId: String, groupId: String) {
        if (userId.isBlank() || groupId.isBlank()) return
        db.collection(GROUP_NOTIFICATION_READS)
            .document("${groupId}_${userId}")
            .set(
                mapOf(
                    "groupId" to groupId,
                    "userId" to userId,
                    "lastSeenAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun markInvitesSeen(userId: String) {
        if (userId.isBlank()) return
        db.collection(COMMUNITY_NOTIFICATION_READS)
            .document(userId)
            .set(mapOf("invitesSeenAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    suspend fun markJoinRequestsSeen(userId: String) {
        if (userId.isBlank()) return
        db.collection(COMMUNITY_NOTIFICATION_READS)
            .document(userId)
            .set(mapOf("requestsSeenAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    suspend fun closeGroup(groupId: String, actorUserId: String): Result<Unit> {
        return try {
            if (groupId.isBlank() || actorUserId.isBlank()) {
                throw IllegalArgumentException("Grup veya kullanıcı bilgisi eksik.")
            }

            val groupRef = db.collection(GROUPS).document(groupId)
            val group = groupFromDocument(groupRef.get().await())
                ?: throw IllegalStateException("Grup bulunamadı.")

            val actorMember = db.collection(GROUP_MEMBERS)
                .document("${groupId}_${actorUserId}")
                .get().await()
            val actorRole = normalizeRole(actorMember.getString("role") ?: "")
            val isActiveAdmin = actorMember.exists() &&
                actorMember.getString("status") == "active" &&
                actorRole == "admin"
            if (!isActiveAdmin || group.creatorId != actorUserId) {
                throw IllegalStateException("Grubu sadece grup yöneticisi kapatabilir.")
            }

            deleteDocumentsWhereEqual(GROUP_MESSAGES, "groupId", groupId)
            deleteDocumentsWhereEqual(GROUP_PROGRAMS, "groupId", groupId)
            deleteDocumentsWhereEqual(GROUP_PROGRAM_APPLICATIONS, "groupId", groupId)
            deleteDocumentsWhereEqual(GROUP_MEMBERS, "groupId", groupId)
            deleteDocumentsWhereEqual(GROUP_INVITES, "groupId", groupId)
            deleteDocumentsWhereEqual(GROUP_JOIN_REQUESTS, "groupId", groupId)
            deleteDocumentsWhereEqual(GROUP_NOTIFICATION_READS, "groupId", groupId)
            deleteDocumentsWhereEqual(TASK_ASSIGNMENTS, "expertId", "GROUP:$groupId")

            groupRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteDocumentsWhereEqual(collection: String, field: String, value: String) {
        val documents = db.collection(collection)
            .whereEqualTo(field, value)
            .get().await()
            .documents

        documents.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /** Kullanıcının aktif üye olduğu tüm grupları getir. */
    suspend fun getMyGroups(userId: String): List<FsGroup> {
        val memberDocs = db.collection(GROUP_MEMBERS)
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "active")
            .get().await()
            .documents

        val groupIds = memberDocs.mapNotNull { it.getString("groupId") }
        if (groupIds.isEmpty()) return emptyList()

        val groups = mutableListOf<FsGroup>()
        // Firestore 'whereIn' en fazla 10 ID ile çalışır
        groupIds.chunked(10).forEach { chunk ->
            val result = db.collection(GROUPS)
                .whereIn("groupId", chunk)
                .get().await()
                .documents.mapNotNull { groupFromDocument(it) }
            groups.addAll(result)
        }
        return groups
    }

    suspend fun updateGroupSettings(groupId: String, actorUserId: String, settings: Map<String, Any>): Result<Unit> = runCatching {
        val actorRef = db.collection(GROUP_MEMBERS).document("${groupId}_${actorUserId}")
        val actor = actorRef.get().await()
        val role = normalizeRole(actor.getString("role") ?: "")
        
        val groupRef = db.collection(GROUPS).document(groupId)
        val groupDoc = groupRef.get().await()
        val allowMemberUpload = groupDoc.getBoolean("allowMemberPhotoUpload") ?: false
        
        if (role == "admin" || (allowMemberUpload && actor.getString("status") == "active")) {
            groupRef.update(settings).await()
        } else {
            throw IllegalStateException("Bu ayarı değiştirme yetkiniz yok.")
        }
    }

    suspend fun updateGroupMemberUploadPermission(groupId: String, actorUserId: String, allowed: Boolean): Result<Unit> = runCatching {
        val actorRef = db.collection(GROUP_MEMBERS).document("${groupId}_${actorUserId}")
        val actor = actorRef.get().await()
        val role = normalizeRole(actor.getString("role") ?: "")
        
        if (role == "admin") {
            db.collection(GROUPS).document(groupId).update("allowMemberPhotoUpload", allowed).await()
        } else {
            throw IllegalStateException("Bu ayarı sadece grup yöneticisi değiştirebilir.")
        }
    }
}
