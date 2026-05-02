package com.example.exerciseformanalyzer.data.repository

import com.example.exerciseformanalyzer.data.remote.CommunityFirestoreService
import com.example.exerciseformanalyzer.model.firestore.FsGroup
import com.example.exerciseformanalyzer.model.firestore.FsGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FsGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FsGroupMessage
import com.example.exerciseformanalyzer.model.firestore.FsGroupProgram
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.flow.Flow

/**
 * CommunityRepository
 * Topluluk (Grup) özelliği için tek sorumlu repository.
 *
 * Tüm operasyonlar CommunityFirestoreService üzerinden Firestore'a gider.
 * ViewModel doğrudan servisle konuşmaz — katman ayrımı korunur.
 */
class CommunityRepository(
    private val service: CommunityFirestoreService
) {

    // ── Grup oluştur ─────────────────────────────────────────────────────────

    suspend fun createGroup(
        creatorId: String,
        creatorName: String,
        creatorEmail: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Grup adı boş olamaz." }
        val group = FsGroup(
            name = name,
            description = description,
            isPrivate = isPrivate,
            creatorId = creatorId,
            creatorName = creatorName
        )
        val member = FsGroupMember(
            userId = creatorId,
            userName = creatorName,
            userEmail = creatorEmail,
            role = "admin",
            joinedAt = System.currentTimeMillis(),
            status = "active"
        )
        service.createGroupWithAdmin(group, member)
    }

    // ── Keşfet ───────────────────────────────────────────────────────────────

    suspend fun getAllGroups(): List<FsGroup> =
        service.getAllGroups()

    // ── Durum sorguları ───────────────────────────────────────────────────────

    /** "member" | "pendingRequest" | "pendingInvite" | "none" */
    suspend fun getUserGroupStatus(groupId: String, userId: String): String =
        service.getUserGroupStatus(groupId, userId)

    // ── Public grup — direkt katıl ────────────────────────────────────────────

    suspend fun joinPublicGroup(
        group: FsGroup,
        userId: String,
        userName: String,
        userEmail: String
    ): Result<Unit> = runCatching {
        val member = FsGroupMember(
            groupId = group.groupId,
            userId = userId,
            userName = userName,
            userEmail = userEmail,
            role = "member",
            joinedAt = System.currentTimeMillis(),
            status = "active"
        )
        service.joinPublicGroup(member)
    }

    // ── Private grup — kullanıcı katılma isteği ───────────────────────────────

    suspend fun sendJoinRequest(
        groupId: String,
        groupName: String,
        fromUserId: String,
        fromUserName: String,
        fromUserEmail: String,
        toAdminId: String
    ): Result<Unit> {
        val request = FsGroupJoinRequest(
            groupId = groupId,
            groupName = groupName,
            fromUserId = fromUserId,
            fromUserName = fromUserName,
            fromUserEmail = fromUserEmail,
            toAdminId = toAdminId
        )
        return service.sendJoinRequest(request)
    }

    suspend fun getJoinRequestsForAdmin(adminId: String): List<FsGroupJoinRequest> =
        service.getJoinRequestsForAdmin(adminId)

    suspend fun acceptJoinRequest(request: FsGroupJoinRequest): Result<Unit> =
        service.acceptJoinRequest(request)

    suspend fun rejectJoinRequest(groupId: String, fromUserId: String): Result<Unit> =
        service.rejectJoinRequest(groupId, fromUserId)

    // ── Private grup — admin daveti ───────────────────────────────────────────

    suspend fun searchUsersByEmailPrefix(query: String): List<FirestoreUser> =
        service.searchUsersByEmailPrefix(query)

    suspend fun sendGroupInvite(
        groupId: String,
        groupName: String,
        fromUserId: String,
        fromUserName: String,
        toUser: FirestoreUser
    ): Result<Unit> {
        val invite = FsGroupInvite(
            groupId = groupId,
            groupName = groupName,
            fromUserId = fromUserId,
            fromUserName = fromUserName,
            toUserId = toUser.uid,
            toUserName = toUser.fullName,
            toUserEmail = toUser.email
        )
        return service.sendGroupInvite(invite)
    }

    suspend fun getMyInvites(toUserId: String): List<FsGroupInvite> =
        service.getInvitesForUser(toUserId)

    suspend fun getSentInvites(fromUserId: String): List<FsGroupInvite> =
        service.getSentInvitesByAdmin(fromUserId)

    suspend fun acceptGroupInvite(invite: FsGroupInvite): Result<Unit> =
        service.acceptGroupInvite(invite)

    suspend fun rejectGroupInvite(groupId: String, toUserId: String): Result<Unit> =
        service.rejectGroupInvite(groupId, toUserId)

    // ── Üyeler ───────────────────────────────────────────────────────────────

    suspend fun getGroupMembers(groupId: String): List<FsGroupMember> =
        service.getGroupMembers(groupId)

    suspend fun removeMember(groupId: String, userId: String) =
        service.removeMember(groupId, userId)

    // ── Gruplarım ────────────────────────────────────────────────────────────

    suspend fun updateMemberRole(
        groupId: String,
        actorUserId: String,
        targetUserId: String,
        newRole: String
    ): Result<Unit> =
        service.updateMemberRole(groupId, actorUserId, targetUserId, newRole)

    fun observeGroupMessages(groupId: String): Flow<List<FsGroupMessage>> =
        service.observeGroupMessages(groupId)

    fun observeGroupPrograms(groupId: String): Flow<List<FsGroupProgram>> =
        service.observeGroupPrograms(groupId)

    fun observeAppliedProgramIds(userId: String): Flow<Set<String>> =
        service.observeAppliedProgramIds(userId)

    suspend fun sendTextMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        text: String
    ): Result<Unit> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Mesaj boş olamaz."))
        val message = FsGroupMessage(
            groupId = groupId,
            senderId = senderId,
            senderName = senderName,
            text = text.trim()
        )
        return service.sendTextMessage(message)
    }

    suspend fun shareProgram(
        group: FsGroup,
        createdById: String,
        createdByName: String,
        title: String,
        note: String,
        exercises: List<FirestoreExerciseItem>,
        scheduleType: String,
        daysOfWeek: List<Int>,
        autoRepeat: Boolean,
        repeatDurationWeeks: Int?
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(IllegalArgumentException("Program adı boş olamaz."))
        if (exercises.isEmpty()) return Result.failure(IllegalArgumentException("En az bir egzersiz ekleyin."))
        val program = FsGroupProgram(
            groupId = group.groupId,
            groupName = group.name,
            title = title.trim(),
            note = note.trim(),
            createdById = createdById,
            createdByName = createdByName,
            exercises = exercises,
            scheduleType = scheduleType,
            daysOfWeek = daysOfWeek,
            autoRepeat = autoRepeat,
            repeatDurationWeeks = repeatDurationWeeks
        )
        return service.shareProgram(program)
    }

    suspend fun deleteMessage(groupId: String, actorUserId: String, message: FsGroupMessage): Result<Unit> =
        service.deleteMessage(groupId, actorUserId, message)

    suspend fun applyProgramToUser(program: FsGroupProgram, userId: String): Result<Unit> =
        service.applyProgramToUser(program, userId)

    suspend fun hasCommunityNotifications(userId: String): Boolean =
        service.hasCommunityNotifications(userId)

    suspend fun getUnreadGroupIds(userId: String): Set<String> =
        service.getUnreadGroupIds(userId)

    suspend fun markGroupNotificationsSeen(userId: String, groupId: String) =
        service.markGroupNotificationsSeen(userId, groupId)

    suspend fun markInvitesSeen(userId: String) =
        service.markInvitesSeen(userId)

    suspend fun markJoinRequestsSeen(userId: String) =
        service.markJoinRequestsSeen(userId)

    suspend fun closeGroup(groupId: String, actorUserId: String): Result<Unit> =
        service.closeGroup(groupId, actorUserId)

    suspend fun getMyGroups(userId: String): List<FsGroup> =
        service.getMyGroups(userId)
}
