package com.example.exerciseformanalyzer.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.GroupEntity
import com.example.exerciseformanalyzer.data.local.entity.GroupMemberEntity
import com.example.exerciseformanalyzer.data.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroup
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser

sealed class GroupUiState {
    object Idle : GroupUiState()
    object Loading : GroupUiState()
    object Success : GroupUiState()
    data class Error(val message: String) : GroupUiState()
}

class GroupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MainApplication
    private val groupRepository = GroupRepository(
        groupDao = app.database.groupDao(),
        firestoreService = app.firestoreService
    )
    private val authRepo = app.authRepository

    val currentUid: String get() = authRepo.currentUid ?: ""

    private val _uiState = MutableStateFlow<GroupUiState>(GroupUiState.Idle)
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    private val _exploreGroups = MutableStateFlow<List<Pair<String, FirestoreGroup>>>(emptyList())
    val exploreGroups: StateFlow<List<Pair<String, FirestoreGroup>>> = _exploreGroups.asStateFlow()

    private val _myInvites = MutableStateFlow<List<Pair<String, FirestoreGroupInvite>>>(emptyList())
    val myInvites: StateFlow<List<Pair<String, FirestoreGroupInvite>>> = _myInvites.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<Pair<String, FirestoreGroupJoinRequest>>>(emptyList())
    val pendingRequests: StateFlow<List<Pair<String, FirestoreGroupJoinRequest>>> = _pendingRequests.asStateFlow()

    private val _groupMembersFirestore = MutableStateFlow<List<FirestoreGroupMember>>(emptyList())
    val groupMembersFirestore: StateFlow<List<FirestoreGroupMember>> = _groupMembersFirestore.asStateFlow()

    fun observeMyGroups(): Flow<List<GroupEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return groupRepository.observeGroupsForUser(uid)
    }

    fun loadDiscoveryData() {
        val uid = currentUid
        if (uid.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // Tüm grupları çek (Gizlilik filtrelemesini UI tarafında yapıyoruz)
                _exploreGroups.value = groupRepository.getExploreGroups()
                _myInvites.value = groupRepository.getMyInvites(uid)
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Keşfet verileri yüklenemedi.")
            }
        }
    }

    fun inviteUserToGroup(groupDocId: String, groupName: String, email: String) {
        val uid = currentUid
        if (uid.isEmpty()) return

        viewModelScope.launch {
            try {
                val targetUser = groupRepository.findUserForInvite(email)
                if (targetUser == null) {
                    _uiState.value = GroupUiState.Error("Kullanıcı bulunamadı.")
                    return@launch
                }

                val invite = FirestoreGroupInvite(
                    groupId = groupDocId,
                    groupName = groupName,
                    fromUserId = uid,
                    fromUserName = authRepo.currentUserEmail ?: "Bilinmiyor", // Veya profile'dan isim
                    toUserId = targetUser.uid,
                    toUserEmail = email
                )
                groupRepository.inviteToGroup(invite)
                _uiState.value = GroupUiState.Success
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Davet gönderilemedi: ${e.message}")
            }
        }
    }

    fun requestToJoinGroup(groupDocId: String, groupName: String, creatorId: String) {
        val uid = currentUid
        if (uid.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val profile = app.firestoreService.getUserProfile(uid)
                val request = FirestoreGroupJoinRequest(
                    userId = uid,
                    userName = profile?.fullName ?: "Bilinmiyor",
                    groupId = groupDocId,
                    groupName = groupName,
                    creatorId = creatorId,
                    status = "PENDING"
                )
                groupRepository.sendJoinRequest(request)
                loadDiscoveryData()
                _uiState.value = GroupUiState.Success
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Katılım isteği gönderilemedi.")
            }
        }
    }

    fun loadGroupAdminData(groupId: String) {
        viewModelScope.launch {
            try {
                _pendingRequests.value = groupRepository.getPendingJoinRequests(groupId)
                _groupMembersFirestore.value = groupRepository.getFirestoreMembers(groupId)
            } catch (e: Exception) {
                // Sessiz hata
            }
        }
    }

    fun respondToJoinRequest(requestId: String, request: FirestoreGroupJoinRequest, accept: Boolean) {
        viewModelScope.launch {
            try {
                groupRepository.respondToJoinRequest(requestId, request, accept)
                loadGroupAdminData(request.groupId) // Veriyi tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("İstek yanıtlama hatası.")
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                groupRepository.removeMember(groupId, userId)
                loadGroupAdminData(groupId) // Veriyi tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Üye çıkarılamadı.")
            }
        }
    }

    fun respondToInvite(inviteId: String, invite: FirestoreGroupInvite, accept: Boolean) {
        viewModelScope.launch {
            try {
                groupRepository.respondToInvite(inviteId, invite, accept)
                loadDiscoveryData() // Listeyi tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Davet yanıtlama hatası.")
            }
        }
    }

    fun observeGroupMembers(groupDocId: String): Flow<List<GroupMemberEntity>> {
        return groupRepository.observeMembersOfGroup(groupDocId)
    }

    fun createGroup(name: String, description: String, isPrivate: Boolean = false) {
        val uid = currentUid
        if (uid.isEmpty()) {
            _uiState.value = GroupUiState.Error("Giriş yapmanız gerekiyor.")
            return
        }
        if (name.isBlank()) {
            _uiState.value = GroupUiState.Error("Grup adı boş olamaz.")
            return
        }

        _uiState.value = GroupUiState.Loading
        viewModelScope.launch {
            try {
                groupRepository.createGroup(uid, name, description, isPrivate)
                _uiState.value = GroupUiState.Success
                loadDiscoveryData() // Keşfet listesini de tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Grup oluşturulamadı: ${e.message}")
            }
        }
    }

    fun leaveGroup(groupDocId: String) {
        val uid = currentUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                groupRepository.leaveGroup(groupDocId, uid)
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error("Gruptan ayrılınamadı: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = GroupUiState.Idle
    }
}
