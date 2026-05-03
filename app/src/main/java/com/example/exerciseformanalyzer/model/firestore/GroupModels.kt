package com.example.exerciseformanalyzer.model.firestore

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// =========================================================
// GRUP — groups/{groupId}
// =========================================================
data class FsGroup(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val isPrivate: Boolean = false,
    val creatorId: String = "",
    val creatorName: String = "",
    val coverImageUrl: String? = null,
    val allowMemberPhotoUpload: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// GRUP ÜYELİĞİ — groupMembers/{groupId}_{userId}
// =========================================================
data class FsGroupMember(
    val groupId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val role: String = "member",  // "admin" | "moderator" | "member"
    val joinedAt: Long = 0L,
    val status: String = "active" // "active" | "removed"
)

data class FsGroupMessage(
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "member",
    val type: String = "text", // "text" | "program"
    val text: String = "",
    val programId: String = "",
    val createdAt: Long = 0L,
    val deleted: Boolean = false
)

data class FsGroupProgram(
    val programId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val title: String = "",
    val note: String = "",
    val createdById: String = "",
    val createdByName: String = "",
    val exercises: List<FirestoreExerciseItem> = emptyList(),
    val scheduleType: String = "DAILY",
    val daysOfWeek: List<Int> = emptyList(),
    val autoRepeat: Boolean = false,
    val repeatDurationWeeks: Int? = null,
    val createdAt: Long = 0L,
    val deleted: Boolean = false
)

data class FsGroupProgramApplication(
    val applicationId: String = "",
    val programId: String = "",
    val groupId: String = "",
    val userId: String = "",
    val taskId: String = "",
    val createdAt: Long = 0L
)

// =========================================================
// ADMIN DAVETİ — groupInvites/{groupId}_{toUserId}
// Admin bir kullanıcıyı gruba davet eder.
// Kullanıcının Davetlerim ekranına düşer.
// =========================================================
data class FsGroupInvite(
    val inviteId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val toUserId: String = "",
    val toUserName: String = "",
    val toUserEmail: String = "",
    val status: String = "pending", // "pending" | "accepted" | "rejected"
    @ServerTimestamp val createdAt: Date? = null
)

// =========================================================
// KULLANICI KATILMA İSTEĞİ — groupJoinRequests/{groupId}_{fromUserId}
// Kullanıcı private gruba katılmak için admin'e istek gönderir.
// Adminin Gelen İstekler ekranına düşer, kullanıcının Davetlerim'ine DEĞİL.
// =========================================================
data class FsGroupJoinRequest(
    val requestId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserEmail: String = "",
    val toAdminId: String = "",
    val status: String = "pending", // "pending" | "accepted" | "rejected"
    @ServerTimestamp val createdAt: Date? = null
)
