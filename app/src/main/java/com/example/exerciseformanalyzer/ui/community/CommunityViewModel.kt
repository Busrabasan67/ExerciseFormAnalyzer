package com.example.exerciseformanalyzer.ui.community

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.repository.CommunityRepository
import com.example.exerciseformanalyzer.model.firestore.FsGroup
import com.example.exerciseformanalyzer.model.firestore.FsGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FsGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FsGroupMessage
import com.example.exerciseformanalyzer.model.firestore.FsGroupProgram
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// UI State tanımları
// ─────────────────────────────────────────────────────────────────────────────

sealed class CommunityEvent {
    object Idle : CommunityEvent()
    object Loading : CommunityEvent()
    data class Success(val message: String) : CommunityEvent()
    data class GroupCreated(val group: FsGroup) : CommunityEvent()
    data class Error(val message: String) : CommunityEvent()
}

data class GroupWithStatus(
    val group: FsGroup,
    val userStatus: String // "member" | "pendingRequest" | "pendingInvite" | "none"
)

// ─────────────────────────────────────────────────────────────────────────────
// CommunityViewModel
// ─────────────────────────────────────────────────────────────────────────────

class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MainApplication
    private val authRepo = app.authRepository
    private val planRepo = app.planRepository

    private val communityRepo = CommunityRepository(
        service = app.communityFirestoreService
    )

    val currentUid: String get() = authRepo.currentUid ?: ""
    private fun currentEmail(): String = authRepo.currentUserEmail ?: ""

    // ── Olay akışı ────────────────────────────────────────────────────────────
    private val _event = MutableStateFlow<CommunityEvent>(CommunityEvent.Idle)
    val event: StateFlow<CommunityEvent> = _event.asStateFlow()

    // ── Keşfet sekmesi ────────────────────────────────────────────────────────
    private val _exploreGroups = MutableStateFlow<List<GroupWithStatus>>(emptyList())
    val exploreGroups: StateFlow<List<GroupWithStatus>> = _exploreGroups.asStateFlow()

    // ── Gruplarım sekmesi ─────────────────────────────────────────────────────
    private val _myGroups = MutableStateFlow<List<FsGroup>>(emptyList())
    val myGroups: StateFlow<List<FsGroup>> = _myGroups.asStateFlow()

    private val _unreadGroupIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadGroupIds: StateFlow<Set<String>> = _unreadGroupIds.asStateFlow()

    // ── Davetlerim sekmesi (admin'den gelen davetler) ─────────────────────────
    private val _myInvites = MutableStateFlow<List<FsGroupInvite>>(emptyList())
    val myInvites: StateFlow<List<FsGroupInvite>> = _myInvites.asStateFlow()

    // ── Gelen Katılma İstekleri sekmesi (admin için, kullanıcıların istekleri) ─
    private val _incomingJoinRequests = MutableStateFlow<List<FsGroupJoinRequest>>(emptyList())
    val incomingJoinRequests: StateFlow<List<FsGroupJoinRequest>> = _incomingJoinRequests.asStateFlow()

    // ── Grup detayı ───────────────────────────────────────────────────────────
    private val _selectedGroup = MutableStateFlow<FsGroup?>(null)
    val selectedGroup: StateFlow<FsGroup?> = _selectedGroup.asStateFlow()

    private val _groupMembers = MutableStateFlow<List<FsGroupMember>>(emptyList())
    val groupMembers: StateFlow<List<FsGroupMember>> = _groupMembers.asStateFlow()

    private val _groupMessages = MutableStateFlow<List<FsGroupMessage>>(emptyList())
    val groupMessages: StateFlow<List<FsGroupMessage>> = _groupMessages.asStateFlow()

    private val _groupPrograms = MutableStateFlow<List<FsGroupProgram>>(emptyList())
    val groupPrograms: StateFlow<List<FsGroupProgram>> = _groupPrograms.asStateFlow()

    private val _appliedProgramIds = MutableStateFlow<Set<String>>(emptySet())
    val appliedProgramIds: StateFlow<Set<String>> = _appliedProgramIds.asStateFlow()

    // ── Admin davet — kullanıcı arama ─────────────────────────────────────────
    private val _searchResults = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val searchResults: StateFlow<List<FirestoreUser>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null
    private var messagesJob: Job? = null
    private var programsJob: Job? = null
    private var appliedProgramsJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Tüm sekme verilerini yükle
    // ─────────────────────────────────────────────────────────────────────────

    fun loadAll() {
        loadExplore()
        loadMyGroups()
        loadMyInvites()
        loadIncomingJoinRequests()
        loadUnreadGroupIds()
    }

    fun loadExplore() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val allGroups = communityRepo.getAllGroups()
                // Her grup için kullanıcının durumunu sorgula
                val withStatus = allGroups.map { group ->
                    val status = communityRepo.getUserGroupStatus(group.groupId, uid)
                    GroupWithStatus(group = group, userStatus = status)
                }
                _exploreGroups.value = withStatus.sortedWith(
                    compareBy<GroupWithStatus> { it.group.isPrivate }
                        .thenBy { it.group.name.lowercase() }
                )
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Gruplar yüklenemedi: ${e.message}")
            }
        }
    }

    fun loadMyGroups() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                _myGroups.value = communityRepo.getMyGroups(uid)
                loadUnreadGroupIds()
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Gruplarım yüklenemedi.")
            }
        }
    }

    fun loadUnreadGroupIds() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _unreadGroupIds.value = runCatching {
                communityRepo.getUnreadGroupIds(uid)
            }.getOrDefault(emptySet())
        }
    }

    fun loadMyInvites() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Sadece kullanıcıya gelen davetler — adminin gönderdiği görünmez
                _myInvites.value = communityRepo.getMyInvites(uid)
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Davetler yüklenemedi.")
            }
        }
    }

    fun loadIncomingJoinRequests() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Admin'in yönettiği gruplara gelen katılma istekleri
                _incomingJoinRequests.value = communityRepo.getJoinRequestsForAdmin(uid)
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Katılma istekleri yüklenemedi.")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Grup oluştur
    // ─────────────────────────────────────────────────────────────────────────

    fun createGroup(name: String, description: String, isPrivate: Boolean) {
        val uid = currentUid
        if (uid.isEmpty()) { _event.value = CommunityEvent.Error("Giriş yapmanız gerekiyor."); return }
        if (name.isBlank()) { _event.value = CommunityEvent.Error("Grup adı boş olamaz."); return }

        _event.value = CommunityEvent.Loading
        viewModelScope.launch {
            try {
                // Kullanıcı profilini al
                val profile = app.firestoreService.getUserProfile(uid)
                val creatorName = profile?.fullName ?: "Kullanıcı"
                val creatorEmail = profile?.email ?: currentEmail()

                communityRepo.createGroup(
                    creatorId = uid,
                    creatorName = creatorName,
                    creatorEmail = creatorEmail,
                    name = name,
                    description = description,
                    isPrivate = isPrivate
                ).onSuccess { createdGroup ->
                    _event.value = CommunityEvent.GroupCreated(createdGroup)
                    loadAll()
                }.onFailure {
                    _event.value = CommunityEvent.Error("Grup oluşturulamadı: ${it.message}")
                }
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Grup oluşturulamadı: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public grup — direkt katıl
    // ─────────────────────────────────────────────────────────────────────────

    fun joinPublicGroup(group: FsGroup) {
        val uid = currentUid
        if (uid.isEmpty()) return
        _event.value = CommunityEvent.Loading
        viewModelScope.launch {
            try {
                val profile = app.firestoreService.getUserProfile(uid)
                communityRepo.joinPublicGroup(
                    group = group,
                    userId = uid,
                    userName = profile?.fullName ?: "Kullanıcı",
                    userEmail = profile?.email ?: currentEmail()
                ).onSuccess {
                    _event.value = CommunityEvent.Success("Topluluğa katıldınız.")
                    loadAll()
                }.onFailure {
                    _event.value = CommunityEvent.Error("Katılım başarısız: ${it.message}")
                }
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Katılım başarısız: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private grup — kullanıcı katılma isteği
    // ─────────────────────────────────────────────────────────────────────────

    fun sendJoinRequest(group: FsGroup) {
        val uid = currentUid
        if (uid.isEmpty()) return
        _event.value = CommunityEvent.Loading
        viewModelScope.launch {
            try {
                val profile = app.firestoreService.getUserProfile(uid)
                communityRepo.sendJoinRequest(
                    groupId = group.groupId,
                    groupName = group.name,
                    fromUserId = uid,
                    fromUserName = profile?.fullName ?: "Kullanıcı",
                    fromUserEmail = profile?.email ?: currentEmail(),
                    toAdminId = group.creatorId
                ).onSuccess {
                    _event.value = CommunityEvent.Success("Katılma isteği gönderildi.")
                    loadExplore()
                }.onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "İstek gönderilemedi.")
                }
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("İstek gönderilemedi: ${e.message}")
            }
        }
    }

    // ── Admin: Katılma isteğine cevap ver ─────────────────────────────────────

    fun acceptJoinRequest(request: FsGroupJoinRequest) {
        viewModelScope.launch {
            communityRepo.acceptJoinRequest(request)
                .onSuccess {
                    _event.value = CommunityEvent.Success("${request.fromUserName} gruba eklendi.")
                    loadIncomingJoinRequests()
                    loadGroupMembers(request.groupId)
                }
                .onFailure {
                    _event.value = CommunityEvent.Error("Kabul edilemedi: ${it.message}")
                }
        }
    }

    fun rejectJoinRequest(request: FsGroupJoinRequest) {
        viewModelScope.launch {
            communityRepo.rejectJoinRequest(request.groupId, request.fromUserId)
                .onSuccess {
                    _event.value = CommunityEvent.Success("İstek reddedildi.")
                    loadIncomingJoinRequests()
                }
                .onFailure {
                    _event.value = CommunityEvent.Error("Reddedilemedi: ${it.message}")
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private grup — admin daveti
    // ─────────────────────────────────────────────────────────────────────────

    fun searchUsers(query: String) {
        searchJob?.cancel()
        if (query.length < 2) { _searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            try {
                val results = communityRepo.searchUsersByEmailPrefix(query)
                // Kendini listeden çıkar
                _searchResults.value = results.filter { it.uid != currentUid }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun sendGroupInvite(group: FsGroup, targetUser: FirestoreUser) {
        val uid = currentUid
        if (uid.isEmpty()) return
        _event.value = CommunityEvent.Loading
        viewModelScope.launch {
            try {
                val profile = app.firestoreService.getUserProfile(uid)
                communityRepo.sendGroupInvite(
                    groupId = group.groupId,
                    groupName = group.name,
                    fromUserId = uid,
                    fromUserName = profile?.fullName ?: "Yönetici",
                    toUser = targetUser
                ).onSuccess {
                    _event.value = CommunityEvent.Success("${targetUser.fullName} davet edildi.")
                    clearSearch()
                }.onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "Davet gönderilemedi.")
                }
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Davet gönderilemedi: ${e.message}")
            }
        }
    }

    // ── Kullanıcı: Daveti cevapla ─────────────────────────────────────────────

    fun acceptGroupInvite(invite: FsGroupInvite) {
        viewModelScope.launch {
            communityRepo.acceptGroupInvite(invite)
                .onSuccess {
                    _event.value = CommunityEvent.Success("${invite.groupName} grubuna katıldınız.")
                    loadMyInvites()
                    loadMyGroups()
                }
                .onFailure {
                    _event.value = CommunityEvent.Error("Kabul edilemedi: ${it.message}")
                }
        }
    }

    fun rejectGroupInvite(invite: FsGroupInvite) {
        viewModelScope.launch {
            communityRepo.rejectGroupInvite(invite.groupId, invite.toUserId)
                .onSuccess {
                    _event.value = CommunityEvent.Success("Davet reddedildi.")
                    loadMyInvites()
                }
                .onFailure {
                    _event.value = CommunityEvent.Error("Reddedilemedi: ${it.message}")
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Grup detayı
    // ─────────────────────────────────────────────────────────────────────────

    fun selectGroup(group: FsGroup) {
        _selectedGroup.value = group
        loadGroupMembers(group.groupId)
        observeChat(group.groupId)
        markGroupNotificationsSeen(group.groupId)
    }

    fun markGroupNotificationsSeen(groupId: String) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            runCatching { communityRepo.markGroupNotificationsSeen(uid, groupId) }
            loadUnreadGroupIds()
        }
    }

    fun markInvitesSeen() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            runCatching { communityRepo.markInvitesSeen(uid) }
        }
    }

    fun markIncomingJoinRequestsSeen() {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            runCatching { communityRepo.markJoinRequestsSeen(uid) }
        }
    }

    private fun observeChat(groupId: String) {
        messagesJob?.cancel()
        programsJob?.cancel()
        appliedProgramsJob?.cancel()

        messagesJob = viewModelScope.launch {
            communityRepo.observeGroupMessages(groupId).collect {
                _groupMessages.value = it
                markGroupNotificationsSeen(groupId)
            }
        }
        programsJob = viewModelScope.launch {
            communityRepo.observeGroupPrograms(groupId).collect { _groupPrograms.value = it }
        }
        val uid = currentUid
        if (uid.isNotEmpty()) {
            appliedProgramsJob = viewModelScope.launch {
                communityRepo.observeAppliedProgramIds(uid).collect { _appliedProgramIds.value = it }
            }
        }
    }

    fun loadGroupMembers(groupId: String) {
        viewModelScope.launch {
            try {
                _groupMembers.value = communityRepo.getGroupMembers(groupId)
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Üyeler yüklenemedi.")
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                communityRepo.removeMember(groupId, userId)
                loadGroupMembers(groupId)
                _event.value = CommunityEvent.Success("Üye gruptan çıkarıldı.")
            } catch (e: Exception) {
                _event.value = CommunityEvent.Error("Üye çıkarılamadı: ${e.message}")
            }
        }
    }

    fun leaveGroup(groupId: String) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            communityRepo.leaveGroup(groupId, uid)
                .onSuccess {
                    messagesJob?.cancel()
                    programsJob?.cancel()
                    appliedProgramsJob?.cancel()
                    _selectedGroup.value = null
                    _groupMembers.value = emptyList()
                    _groupMessages.value = emptyList()
                    _groupPrograms.value = emptyList()
                    loadAll()
                    _event.value = CommunityEvent.Success("Gruptan çıkıldı.")
                }
                .onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "Gruptan çıkılamadı.")
                }
        }
    }

    fun closeGroup(groupId: String) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            communityRepo.closeGroup(groupId, uid)
                .onSuccess {
                    messagesJob?.cancel()
                    programsJob?.cancel()
                    appliedProgramsJob?.cancel()
                    _selectedGroup.value = null
                    _groupMembers.value = emptyList()
                    _groupMessages.value = emptyList()
                    _groupPrograms.value = emptyList()
                    loadAll()
                    _event.value = CommunityEvent.Success("Grup kapatıldı.")
                }
                .onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "Grup kapatılamadı.")
                }
        }
    }

    fun isAdmin(group: FsGroup?): Boolean =
        group != null && group.creatorId == currentUid

    fun currentMemberRole(): String =
        _groupMembers.value.firstOrNull { it.userId == currentUid }?.role?.lowercase() ?: "none"

    fun isCurrentMember(): Boolean =
        _groupMembers.value.any { it.userId == currentUid }

    fun canInvite(): Boolean = currentMemberRole() in listOf("admin", "moderator")

    fun canManageRoles(): Boolean = currentMemberRole() == "admin"

    fun canShareProgram(): Boolean = currentMemberRole() in listOf("admin", "moderator")

    fun canDeleteChatContent(): Boolean = currentMemberRole() == "admin"

    fun updateMemberRole(groupId: String, targetUserId: String, newRole: String) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            communityRepo.updateMemberRole(groupId, uid, targetUserId, newRole)
                .onSuccess {
                    if (newRole == "admin") {
                        val newAdmin = _groupMembers.value.firstOrNull { it.userId == targetUserId }
                        _selectedGroup.value = _selectedGroup.value?.copy(
                            creatorId = targetUserId,
                            creatorName = newAdmin?.userName.orEmpty()
                        )
                    }
                    _event.value = CommunityEvent.Success("Rol güncellendi.")
                    loadGroupMembers(groupId)
                    loadMyGroups()
                }
                .onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "Rol güncellenemedi.")
                }
        }
    }

    fun updateGroupPrivacy(groupId: String, isPrivate: Boolean) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            communityRepo.updateGroupPrivacy(groupId, uid, isPrivate)
                .onSuccess { updatedGroup ->
                    _selectedGroup.value = updatedGroup
                    _myGroups.value = _myGroups.value.map {
                        if (it.groupId == updatedGroup.groupId) updatedGroup else it
                    }
                    _exploreGroups.value = _exploreGroups.value.map {
                        if (it.group.groupId == updatedGroup.groupId) {
                            it.copy(group = updatedGroup)
                        } else {
                            it
                        }
                    }
                    if (!updatedGroup.isPrivate) {
                        loadGroupMembers(updatedGroup.groupId)
                        loadIncomingJoinRequests()
                    }
                    loadMyGroups()
                    loadExplore()
                    _event.value = CommunityEvent.Success("Grup gizliliği güncellendi.")
                }
                .onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "Grup gizliliği güncellenemedi.")
                }
        }
    }

    fun sendTextMessage(text: String) {
        val group = _selectedGroup.value ?: return
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            val profile = app.firestoreService.getUserProfile(uid)
            communityRepo.sendTextMessage(
                groupId = group.groupId,
                senderId = uid,
                senderName = profile?.fullName ?: "Kullanıcı",
                text = text
            ).onFailure {
                _event.value = CommunityEvent.Error(it.message ?: "Mesaj gönderilemedi.")
            }
        }
    }

    fun shareProgram(
        title: String,
        note: String,
        exercises: List<ExpertViewModel.TaskExerciseInput>,
        scheduleType: String,
        daysOfWeek: List<Int>,
        autoRepeat: Boolean,
        repeatWeeks: Int?
    ) {
        val group = _selectedGroup.value ?: return
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            val profile = app.firestoreService.getUserProfile(uid)
            val fsExercises = exercises.map { input ->
                val value = input.targetValue.toIntOrNull() ?: 1
                val sets = input.sets.toIntOrNull() ?: 1
                val rest = input.restTimeSeconds.toIntOrNull() ?: 30
                FirestoreExerciseItem(
                    exerciseType = input.exerciseType.name,
                    targetType = if (input.isDurationBased) "DURATION" else "REPS",
                    targetReps = if (!input.isDurationBased) value else null,
                    targetDurationSeconds = if (input.isDurationBased) value else null,
                    sets = sets,
                    restTimeSeconds = rest,
                    difficulty = input.difficulty,
                    category = input.category,
                    videoUrl = input.videoUrl,
                    status = "PENDING"
                )
            }
            communityRepo.shareProgram(
                group = group,
                createdById = uid,
                createdByName = profile?.fullName ?: "Kullanıcı",
                title = title,
                note = note,
                exercises = fsExercises,
                scheduleType = scheduleType,
                daysOfWeek = daysOfWeek,
                autoRepeat = autoRepeat,
                repeatDurationWeeks = repeatWeeks
            ).onSuccess {
                _event.value = CommunityEvent.Success("Program sohbette paylaşıldı.")
            }.onFailure {
                _event.value = CommunityEvent.Error(it.message ?: "Program paylaşılamadı.")
            }
        }
    }

    fun deleteMessage(message: FsGroupMessage) {
        val group = _selectedGroup.value ?: return
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            communityRepo.deleteMessage(group.groupId, uid, message)
                .onFailure { _event.value = CommunityEvent.Error(it.message ?: "Mesaj silinemedi.") }
        }
    }

    fun applyProgram(program: FsGroupProgram) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            communityRepo.applyProgramToUser(program, uid)
                .onSuccess {
                    planRepo.syncTasksForPatient(uid)
                    _event.value = CommunityEvent.Success("Program genel bakışınıza eklendi.")
                }
                .onFailure {
                    _event.value = CommunityEvent.Error(it.message ?: "Program uygulanamadı.")
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı
    // ─────────────────────────────────────────────────────────────────────────

    fun resetEvent() { _event.value = CommunityEvent.Idle }
}
