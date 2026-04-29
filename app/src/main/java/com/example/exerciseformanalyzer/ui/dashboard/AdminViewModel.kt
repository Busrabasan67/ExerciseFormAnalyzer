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

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    fun setAdminPanel(panel: AdminPanelType) {
        _selectedAdminPanel.value = panel
    }

    fun fetchAdminStats() {
        viewModelScope.launch {
            _adminSystemStats.value = adminRepo.getSystemStats()
            fetchAllUsers()
        }
    }

    fun fetchAllUsers() {
        viewModelScope.launch {
            _allUsers.value = adminRepo.getAllUsers()
        }
    }

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
    }
}
