package com.example.exerciseformanalyzer.data.remote

import com.example.exerciseformanalyzer.model.firestore.FsGroup
import com.example.exerciseformanalyzer.model.firestore.FsGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FsGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

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
        const val USERS                = "users"
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
            .documents.mapNotNull { it.toObject<FsGroup>() }
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
        db.collection(GROUP_MEMBERS).document(docId).set(member).await()
    }

    // =====================================================================
    // PRIVATE GRUP — Kullanıcı Katılma İsteği
    // =====================================================================

    /**
     * Kullanıcı private gruba katılmak için admin'e istek gönderir.
     * Çift istek koruması: pending request varsa yeni oluşturmaz.
     */
    suspend fun sendJoinRequest(request: FsGroupJoinRequest): Result<Unit> {
        if (isMember(request.groupId, request.fromUserId)) {
            return Result.failure(Exception("Zaten bu grubun üyesisiniz."))
        }
        if (hasPendingJoinRequest(request.groupId, request.fromUserId)) {
            return Result.failure(Exception("Zaten bekleyen bir katılma isteğiniz var."))
        }
        val docId = "${request.groupId}_${request.fromUserId}"
        val finalRequest = request.copy(requestId = docId)
        db.collection(GROUP_JOIN_REQUESTS).document(docId).set(finalRequest).await()
        return Result.success(Unit)
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
                transaction.update(requestRef, "status", "accepted")

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
        if (isMember(invite.groupId, invite.toUserId)) {
            return Result.failure(Exception("Kullanıcı zaten bu grubun üyesi."))
        }
        if (hasPendingInvite(invite.groupId, invite.toUserId)) {
            return Result.failure(Exception("Bu kullanıcıya zaten bekleyen bir davet gönderilmiş."))
        }
        val docId = "${invite.groupId}_${invite.toUserId}"
        val finalInvite = invite.copy(inviteId = docId)
        db.collection(GROUP_INVITES).document(docId).set(finalInvite).await()
        return Result.success(Unit)
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
                transaction.update(inviteRef, "status", "accepted")

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
        // Admin önce
        return members.sortedByDescending { it.role == "admin" }
    }

    /** Kullanıcıyı gruptan çıkar (status = "removed" yap). */
    suspend fun removeMember(groupId: String, userId: String) {
        db.collection(GROUP_MEMBERS)
            .document("${groupId}_${userId}")
            .update("status", "removed").await()
    }

    // =====================================================================
    // GRUPLARIMI — Kullanıcının üye olduğu gruplar
    // =====================================================================

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
                .documents.mapNotNull { it.toObject<FsGroup>() }
            groups.addAll(result)
        }
        return groups
    }
}
