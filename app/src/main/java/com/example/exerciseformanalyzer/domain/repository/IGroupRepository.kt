package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.GroupEntity
import com.example.exerciseformanalyzer.data.local.entity.GroupMemberEntity
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroup
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.flow.Flow

/**
 * Sosyal grup yönetimi için domain-layer sözleşmesi.
 */
interface IGroupRepository {

    /** Kullanıcının dahil olduğu grupları reaktif izler. */
    fun observeGroupsForUser(userUid: String): Flow<List<GroupEntity>>

    /** Gruptaki üyeleri reaktif izler. */
    fun observeMembersOfGroup(groupDocId: String): Flow<List<GroupMemberEntity>>

    /** Yeni grup oluşturur ve kurucuyu otomatik olarak ekler. */
    suspend fun createGroup(
        creatorUid: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): GroupEntity

    /** Gruba katılır. */
    suspend fun joinGroup(memberEntity: GroupMemberEntity, groupDocId: String, userUid: String)

    /** Gruptan ayrılır. */
    suspend fun leaveGroup(groupDocId: String, userUid: String)

    /** Keşfedilebilir (herkese açık) grupları Firestore'dan çeker. */
    suspend fun getExploreGroups(): List<Pair<String, FirestoreGroup>>

    /** Kullanıcının bekleyen grup davetlerini çeker. */
    suspend fun getMyInvites(userId: String): List<Pair<String, FirestoreGroupInvite>>

    /** E-posta ile kullanıcı arar (davet göndermek için). */
    suspend fun findUserForInvite(email: String): FirestoreUser?

    /** Gruba davet gönderir. */
    suspend fun inviteToGroup(invite: FirestoreGroupInvite)

    /** Daveti yanıtlar. Onaylanırsa gruba katılır. */
    suspend fun respondToInvite(inviteId: String, invite: FirestoreGroupInvite, accept: Boolean)

    /** Gruba katılma isteği gönderir. */
    suspend fun sendJoinRequest(request: FirestoreGroupJoinRequest)

    /** Bekleyen katılım isteklerini getirir. */
    suspend fun getPendingJoinRequests(groupId: String): List<Pair<String, FirestoreGroupJoinRequest>>

    /** Katılma isteğini yanıtlar. Onaylanırsa üyeyi ekler. */
    suspend fun respondToJoinRequest(
        requestId: String,
        request: FirestoreGroupJoinRequest,
        accept: Boolean
    )

    /** Gruptaki tüm üyeleri Firestore'dan çeker. */
    suspend fun getFirestoreMembers(groupId: String): List<FirestoreGroupMember>

    /** Üyeyi gruptan çıkarır. */
    suspend fun removeMember(groupId: String, userId: String)
}
