package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.model.AdminSystemStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdminPanelType { ADMIN, PATIENT, EXPERT }

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val adminRepo = (application as MainApplication).adminRepository

    private val _selectedAdminPanel = MutableStateFlow(AdminPanelType.ADMIN)
    val selectedAdminPanel: StateFlow<AdminPanelType> = _selectedAdminPanel.asStateFlow()

    private val _adminSystemStats = MutableStateFlow(AdminSystemStats())
    val adminSystemStats: StateFlow<AdminSystemStats> = _adminSystemStats.asStateFlow()

    private val _allUsers = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val allUsers: StateFlow<List<FirestoreUser>> = _allUsers.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedRoleFilter = MutableStateFlow<String?>(null)
    val selectedRoleFilter: StateFlow<String?> = _selectedRoleFilter.asStateFlow()

    private val _filteredUsers = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val filteredUsers: StateFlow<List<FirestoreUser>> = _filteredUsers.asStateFlow()

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    private val _allGroups = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroup>>(emptyList())
    val allGroups: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroup>> = _allGroups.asStateFlow()

    private val _selectedGroupMembers = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember>>(emptyList())
    val selectedGroupMembers: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember>> = _selectedGroupMembers.asStateFlow()

    fun setAdminPanel(panel: AdminPanelType) {
        _selectedAdminPanel.value = panel
    }

    fun fetchAdminStats() {
        viewModelScope.launch {
            _adminSystemStats.value = adminRepo.getSystemStats()
            fetchAllUsers()
            fetchAllGroups()
        }
    }

    fun fetchAllUsers() {
        viewModelScope.launch {
            _allUsers.value = adminRepo.getAllUsers()
            applyFilters()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun updateRoleFilter(role: String?) {
        _selectedRoleFilter.value = role
        applyFilters()
    }

    private fun applyFilters() {
        var result = _allUsers.value
        val query = _searchQuery.value.lowercase()
        val role = _selectedRoleFilter.value

        if (query.isNotBlank()) {
            result = result.filter { 
                it.fullName.lowercase().contains(query) || it.email.lowercase().contains(query) 
            }
        }
        
        if (role != null) {
            result = result.filter { it.role == role }
        }
        
        _filteredUsers.value = result
    }

    fun updateUserRole(uid: String, newRole: String) {
        viewModelScope.launch {
            val success = adminRepo.updateUserRole(uid, newRole)
            if (success) fetchAllUsers()
        }
    }

    fun updateUserStatus(uid: String, newStatus: String) {
        viewModelScope.launch {
            val success = adminRepo.updateUserStatus(uid, newStatus)
            if (success) fetchAllUsers()
        }
    }

    private val _badges = MutableStateFlow<List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition>>>(emptyList())
    val badges: StateFlow<List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition>>> = _badges.asStateFlow()

    fun fetchBadges() {
        viewModelScope.launch {
            _badges.value = adminRepo.getBadgeDefinitions()
        }
    }

    fun createBadge(name: String, nameEn: String, description: String, descriptionEn: String, targetValue: Int, xpReward: Int, category: String) {
        viewModelScope.launch {
            val badge = com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition(
                name = name,
                nameEn = nameEn,
                description = description,
                descriptionEn = descriptionEn,
                targetValue = targetValue,
                xpReward = xpReward,
                category = category,
                type = "SYSTEM",
                createdBy = "ADMIN"
            )
            val id = adminRepo.createBadgeDefinition(badge)
            if (id != null) {
                fetchBadges()
                // Retroaktif: Daha önce bu koşulu sağlayan kullanıcılara rozeti ver
                adminRepo.evaluateBadgeRetroactively(id, badge)
            }
        }
    }

    fun updateBadge(badgeId: String, name: String, nameEn: String, description: String, descriptionEn: String, targetValue: Int, xpReward: Int, category: String) {
        viewModelScope.launch {
            val updates = mapOf(
                "name" to name,
                "nameEn" to nameEn,
                "description" to description,
                "descriptionEn" to descriptionEn,
                "targetValue" to targetValue,
                "xpReward" to xpReward,
                "category" to category
            )
            val success = adminRepo.updateBadgeDefinition(badgeId, updates)
            if (success) {
                fetchBadges()
                // Kategori veya hedef değişmiş olabilir: retroaktif değerlendirmeyi tekrar çalıştır
                val updatedBadge = com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition(
                    name = name, nameEn = nameEn,
                    description = description, descriptionEn = descriptionEn,
                    targetValue = targetValue, xpReward = xpReward, category = category
                )
                adminRepo.evaluateBadgeRetroactively(badgeId, updatedBadge)
            }
        }
    }

    fun deleteBadge(badgeId: String) {
        viewModelScope.launch {
            val success = adminRepo.deleteBadgeDefinition(badgeId)
            if (success) fetchBadges()
        }
    }

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
    }

    fun fetchAllGroups() {
        viewModelScope.launch {
            _allGroups.value = adminRepo.getAllGroups()
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val success = adminRepo.deleteGroup(groupId)
            if (success) fetchAllGroups()
        }
    }

    fun updateGroupVisibility(groupId: String, isPublic: Boolean) {
        viewModelScope.launch {
            adminRepo.updateGroupSettings(groupId, mapOf("isPrivate" to !isPublic))
            fetchAllGroups()
        }
    }

    fun loadGroupMembers(groupId: String) {
        viewModelScope.launch {
            _selectedGroupMembers.value = adminRepo.getGroupMembers(groupId)
        }
    }

    fun addMemberToGroup(groupId: String, userId: String, role: String) {
        viewModelScope.launch {
            val success = adminRepo.addMemberToGroup(groupId, userId, role)
            if (success) loadGroupMembers(groupId)
        }
    }

    fun updateMemberRole(groupId: String, userId: String, newRole: String) {
        viewModelScope.launch {
            val success = adminRepo.updateMemberRole(groupId, userId, newRole)
            if (success) loadGroupMembers(groupId)
        }
    }

    fun removeMemberFromGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            val success = adminRepo.removeMemberFromGroup(groupId, userId)
            if (success) loadGroupMembers(groupId)
        }
    }

    fun changeGroupCreator(groupId: String, newCreatorId: String) {
        viewModelScope.launch {
            val success = adminRepo.changeGroupCreator(groupId, newCreatorId)
            if (success) fetchAllGroups()
        }
    }

    fun sendAdminPasswordReset(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val email = authRepo.currentUserEmail
            if (email != null) {
                val result = authRepo.sendPasswordResetEmail(email)
                when (result) {
                    is com.example.exerciseformanalyzer.domain.model.AuthResult.Success -> {
                        val message = getApplication<android.app.Application>().getString(com.example.exerciseformanalyzer.R.string.ui_password_reset_sent_to, email)
                        onResult(true, message)
                    }
                    is com.example.exerciseformanalyzer.domain.model.AuthResult.Error -> onResult(false, result.message)
                    is com.example.exerciseformanalyzer.domain.model.AuthResult.Loading -> { /* Do nothing for loading state here */ }
                }
            } else {
                onResult(false, "E-posta adresi bulunamadı.")
            }
        }
    }
}
