package com.example.exerciseformanalyzer.data.repository

import com.example.exerciseformanalyzer.data.local.dao.GroupDao
import com.example.exerciseformanalyzer.data.local.entity.GroupEntity
import com.example.exerciseformanalyzer.data.local.entity.GroupMemberEntity
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroup
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember
import kotlinx.coroutines.flow.Flow

/**
 * GroupRepository — Sosyal grup yönetimi
 * Room (cache) + Firestore (bulut) senkronizasyon mantığı
 */
class GroupRepository(
    private val groupDao: GroupDao,
    private val firestoreService: FirestoreService
) {
    // Kullanıcının dahil olduğu grupları Room'dan reaktif akışla izle
    fun observeGroupsForUser(userUid: String): Flow<List<GroupEntity>> {
        return groupDao.observeGroupsForUser(userUid)
    }

    // Gruptaki üyeleri izle
    fun observeMembersOfGroup(groupDocId: String): Flow<List<GroupMemberEntity>> {
        return groupDao.observeMembersOfGroup(groupDocId)
    }

    /**
     * Yeni grup oluştur.
     * 1. Room'a isSynced=false olarak yaz (offline support)
     * 2. Firestore'a yükle
     * 3. Room'daki kaydı docId ile güncelle
     */
    suspend fun createGroup(creatorUid: String, name: String, description: String, isPrivate: Boolean): GroupEntity {
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
    suspend fun joinGroup(memberEntity: GroupMemberEntity, groupDocId: String, userUid: String) {
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
    suspend fun leaveGroup(groupDocId: String, userUid: String) {
        groupDao.removeMember(groupDocId, userUid)
        try {
            firestoreService.leaveGroup(groupDocId, userUid)
        } catch (e: Exception) {
            // Offline — SyncWorker sonra halleder
        }
    }
}
