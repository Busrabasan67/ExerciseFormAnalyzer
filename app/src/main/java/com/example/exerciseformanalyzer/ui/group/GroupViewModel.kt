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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

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

    fun observeMyGroups(): Flow<List<GroupEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return groupRepository.observeGroupsForUser(uid)
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
