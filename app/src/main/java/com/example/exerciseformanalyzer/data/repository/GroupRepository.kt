package com.example.exerciseformanalyzer.data.repository

import com.example.exerciseformanalyzer.data.local.dao.GroupDao
import com.example.exerciseformanalyzer.data.local.entity.GroupEntity
import com.example.exerciseformanalyzer.data.local.entity.GroupMemberEntity
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.domain.repository.IGroupRepository
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroup
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember
import kotlinx.coroutines.flow.Flow
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * GroupRepository — Sosyal grup yönetimi (IGroupRepository implementasyonu)
 * Room (cache) + Firestore (bulut) senkronizasyon mantığı
 */
class GroupRepository(
    private val groupDao: GroupDao,
    private val firestoreService: FirestoreService
) : IGroupRepository {
    // Kullanıcının dahil olduğu grupları Room'dan reaktif akışla izle
    override fun observeGroupsForUser(userUid: String): Flow<List<GroupEntity>> {
        return groupDao.observeGroupsForUser(userUid)
    }

    // Gruptaki üyeleri izle
    override fun observeMembersOfGroup(groupDocId: String): Flow<List<GroupMemberEntity>> {
        return groupDao.observeMembersOfGroup(groupDocId)
    }

    /**
     * Yeni grup oluştur.
     * 1. Room'a isSynced=false olarak yaz (offline support)
     * 2. Firestore'a yükle
     * 3. Room'daki kaydı docId ile güncelle
     */
    override suspend fun createGroup(creatorUid: String, name: String, description: String, isPrivate: Boolean): GroupEntity {
        // Room'a önce yaz
        val entity = GroupEntity(
            name = name,
            description = description,
            creatorUid = creatorUid,
            isPrivate = isPrivate,
            createdAt = System.currentTimeMillis(),
            isSynced = false
        )
        val localId = groupDao.insertGroup(entity).toInt()

        // Firestore'a yükle
        try {
            val firestoreGroup = FirestoreGroup(
                name = name,
                description = description,
                creatorId = creatorUid,
                isPrivate = isPrivate
            )
            val docId = firestoreService.createGroup(firestoreGroup)
            groupDao.markGroupAsSynced(localId, docId)

            // Kurucuyu otomatik üye olarak ekle
            val memberEntity = GroupMemberEntity(
                groupFirebaseDocId = docId,
                userUid = creatorUid,
                role = "ADMIN",
                joinedAt = System.currentTimeMillis(),
                isSynced = false
            )
            joinGroup(memberEntity, docId, creatorUid)

            return entity.copy(id = localId, firebaseDocId = docId, isSynced = true)
        } catch (e: Exception) {
            return entity.copy(id = localId)
        }
    }

    /**
     * Gruba katıl.
     */
    override suspend fun joinGroup(memberEntity: GroupMemberEntity, groupDocId: String, userUid: String) {
        groupDao.insertMember(memberEntity)
        try {
            val firestoreMember = FirestoreGroupMember(
                groupId = groupDocId,
                userId = userUid,
                role = "MEMBER"
            )
            firestoreService.joinGroup(firestoreMember)
            groupDao.markMemberAsSynced(memberEntity.id)
        } catch (e: Exception) {
            // Offline — SyncWorker sonra halleder
        }
    }

    /**
     * Gruptan ayrıl.
     */
    override suspend fun leaveGroup(groupDocId: String, userUid: String) {
        groupDao.removeMember(groupDocId, userUid)
        try {
            firestoreService.leaveGroup(groupDocId, userUid)
        } catch (e: Exception) {
            // Offline — SyncWorker sonra halleder
        }
    }

    /** Keşfet sayfasında gösterilecek grupları çek (Public). */
    override suspend fun getExploreGroups(): List<Pair<String, FirestoreGroup>> {
        return firestoreService.getExploreGroups()
    }

    /** Kullanıcının bekleyen davetlerini çek. */
    override suspend fun getMyInvites(userId: String): List<Pair<String, FirestoreGroupInvite>> {
        return firestoreService.getInvitesForUser(userId)
    }

    /** Email ile kullanıcı bul (Davet göndermek için). */
    override suspend fun findUserForInvite(email: String): com.example.exerciseformanalyzer.model.firestore.FirestoreUser? {
        return firestoreService.findAnyUserByEmail(email)
    }

    /** Davet gönder. */
    override suspend fun inviteToGroup(invite: FirestoreGroupInvite) {
        firestoreService.sendGroupInvite(invite)
    }

    /** Daveti cevapla. Onaylanırsa gruba katıl. */
    override suspend fun respondToInvite(inviteId: String, invite: FirestoreGroupInvite, accept: Boolean) {
        val status = if (accept) "ACCEPTED" else "REJECTED"
        firestoreService.respondToGroupInvite(inviteId, status)
        
        if (accept) {
            val memberEntity = GroupMemberEntity(
                groupFirebaseDocId = invite.groupId,
                userUid = invite.toUserId,
                role = "MEMBER",
                joinedAt = System.currentTimeMillis(),
                isSynced = false
            )
            joinGroup(memberEntity, invite.groupId, invite.toUserId)
        }
    }

    /** Katılma isteği gönder. */
    override suspend fun sendJoinRequest(request: com.example.exerciseformanalyzer.model.firestore.FirestoreGroupJoinRequest) {
        firestoreService.sendGroupJoinRequest(request)
    }

    /** Bekleyen katılım isteklerini getir. */
    override suspend fun getPendingJoinRequests(groupId: String): List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreGroupJoinRequest>> {
        return firestoreService.getJoinRequestsForGroup(groupId)
    }

    /** Katılma isteğine cevap ver. Onaylanırsa üyeyi ekle. */
    override suspend fun respondToJoinRequest(requestId: String, request: com.example.exerciseformanalyzer.model.firestore.FirestoreGroupJoinRequest, accept: Boolean) {
        val status = if (accept) "ACCEPTED" else "REJECTED"
        firestoreService.respondToGroupJoinRequest(requestId, status)

        if (accept) {
            val memberEntity = GroupMemberEntity(
                groupFirebaseDocId = request.groupId,
                userUid = request.userId,
                role = "MEMBER",
                joinedAt = System.currentTimeMillis(),
                isSynced = false
            )
            joinGroup(memberEntity, request.groupId, request.userId)
        }
    }

    /** Tüm üyeleri Firestore'dan çek (Ayrıntılı liste için). */
    override suspend fun getFirestoreMembers(groupId: String): List<FirestoreGroupMember> {
        return firestoreService.getGroupMembers(groupId)
    }

    /** Üyeyi gruptan çıkar. */
    override suspend fun removeMember(groupId: String, userId: String) {
        groupDao.removeMember(groupId, userId) // Yerelden sil
        firestoreService.leaveGroup(groupId, userId) // Buluttan sil
    }

    override suspend fun uploadGroupCoverPhoto(groupId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val storageRef = com.google.firebase.ktx.Firebase.storage.reference.child("group_covers/$groupId.jpg")
            storageRef.putBytes(imageBytes).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            // Firestore güncelle
            firestoreService.updateGroupSettings(groupId, mapOf("coverImageUrl" to downloadUrl))
            
            // Room güncelle
            val existing = groupDao.getGroupByDocId(groupId)
            if (existing != null) {
                groupDao.insertGroup(existing.copy(coverImageUrl = downloadUrl))
            }
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroupSettings(groupId: String, coverImageUrl: String?, allowMemberUpload: Boolean) {
        try {
            firestoreService.updateGroupSettings(groupId, mapOf(
                "coverImageUrl" to (coverImageUrl ?: ""),
                "allowMemberPhotoUpload" to allowMemberUpload
            ))
            
            val existing = groupDao.getGroupByDocId(groupId)
            if (existing != null) {
                groupDao.insertGroup(existing.copy(
                    coverImageUrl = coverImageUrl,
                    allowMemberPhotoUpload = allowMemberUpload
                ))
            }
        } catch (e: Exception) {}
    }
}
