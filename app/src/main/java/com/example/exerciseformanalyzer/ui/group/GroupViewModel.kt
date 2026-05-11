package com.example.exerciseformanalyzer.ui.group

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.R
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

data class SelectedGroupContext(
    val docId: String,
    val name: String,
    val description: String,
    val creatorId: String
)

class GroupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MainApplication
    private val groupRepository = GroupRepository(
        groupDao = app.database.groupDao(),
        firestoreService = app.firestoreService
    )
    private val authRepo = app.authRepository

    val currentUid: String get() = authRepo.currentUid ?: ""
    private fun s(@StringRes resId: Int, vararg args: Any): String = app.getString(resId, *args)

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

    private val _selectedGroup = MutableStateFlow<SelectedGroupContext?>(null)
    val selectedGroup: StateFlow<SelectedGroupContext?> = _selectedGroup.asStateFlow()

    fun setSelectedGroup(docId: String, name: String, description: String, creatorId: String) {
        _selectedGroup.value = SelectedGroupContext(docId, name, description, creatorId)
    }

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
                _uiState.value = GroupUiState.Error(s(R.string.ui_error_occurred))
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
                    _uiState.value = GroupUiState.Error(s(R.string.ui_user_not_found))
                    return@launch
                }

                val invite = FirestoreGroupInvite(
                    groupId = groupDocId,
                    groupName = groupName,
                    fromUserId = uid,
                    fromUserName = authRepo.currentUserEmail.orEmpty(),
                    toUserId = targetUser.uid,
                    toUserEmail = email
                )
                groupRepository.inviteToGroup(invite)
                _uiState.value = GroupUiState.Success
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error(s(R.string.ui_invite_send_failed, s(R.string.unknown_error)))
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
                    userName = profile?.fullName.orEmpty(),
                    groupId = groupDocId,
                    groupName = groupName,
                    creatorId = creatorId,
                    status = "PENDING"
                )
                groupRepository.sendJoinRequest(request)
                loadDiscoveryData()
                _uiState.value = GroupUiState.Success
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error(s(R.string.ui_request_send_failed, s(R.string.unknown_error)))
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
                _uiState.value = GroupUiState.Error(s(R.string.ui_error_occurred))
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                groupRepository.removeMember(groupId, userId)
                loadGroupAdminData(groupId) // Veriyi tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error(s(R.string.ui_remove_member_failed))
            }
        }
    }

    fun respondToInvite(inviteId: String, invite: FirestoreGroupInvite, accept: Boolean) {
        viewModelScope.launch {
            try {
                groupRepository.respondToInvite(inviteId, invite, accept)
                loadDiscoveryData() // Listeyi tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error(s(R.string.ui_error_occurred))
            }
        }
    }

    fun observeGroupMembers(groupDocId: String): Flow<List<GroupMemberEntity>> {
        return groupRepository.observeMembersOfGroup(groupDocId)
    }

    fun createGroup(name: String, description: String, isPrivate: Boolean = false) {
        val uid = currentUid
        if (uid.isEmpty()) {
            _uiState.value = GroupUiState.Error(s(R.string.ui_login_required))
            return
        }
        if (name.isBlank()) {
            _uiState.value = GroupUiState.Error(s(R.string.ui_err_group_name_empty))
            return
        }

        _uiState.value = GroupUiState.Loading
        viewModelScope.launch {
            try {
                groupRepository.createGroup(uid, name, description, isPrivate)
                _uiState.value = GroupUiState.Success
                loadDiscoveryData() // Keşfet listesini de tazele
            } catch (e: Exception) {
                _uiState.value = GroupUiState.Error(s(R.string.ui_group_create_failed, s(R.string.unknown_error)))
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
                _uiState.value = GroupUiState.Error(s(R.string.ui_leave_failed_err, s(R.string.unknown_error)))
            }
        }
    }

    fun resetState() {
        _uiState.value = GroupUiState.Idle
    }
}
