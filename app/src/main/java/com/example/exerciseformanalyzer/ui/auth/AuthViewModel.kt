package com.example.exerciseformanalyzer.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.repository.AuthRepository
import com.example.exerciseformanalyzer.data.repository.AuthResult
import com.example.exerciseformanalyzer.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val uid: String, val role: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = (application as MainApplication).authRepository
    private val userRepository: UserRepository = (application as MainApplication).userRepository
    private val userPrefs: com.example.exerciseformanalyzer.data.preferences.UserPreferencesRepository = (application as MainApplication).userPreferencesRepository

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkAutoLogin() {
        if (_uiState.value != AuthUiState.Idle) return
        
        viewModelScope.launch {
            val uid = authRepository.currentUid
            val isLoggedIn = authRepository.isLoggedIn
            
            if (uid != null && isLoggedIn) {
                val rememberMe = userPrefs.rememberMe.first()
                val timestamp = userPrefs.loginTimestamp.first()
                val daysPassed = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60 * 24)

                // 3 gün kuralı: Beni hatırla seçiliyse 3 gün, seçili değilse hemen çıkış
                if (rememberMe && daysPassed < 3) {
                    _uiState.value = AuthUiState.Loading
                    authRepository.syncUserProfileFromFirestore(uid)
                    userRepository.observeCurrentUser(uid).take(1).collect { user ->
                        if (user != null) {
                            _uiState.value = AuthUiState.Success(uid, user.role.uppercase())
                        }
                    }
                } else if (!rememberMe) {
                    // Eğer beni hatırla seçili değilse, uygulama yeniden açıldığında çıkış yaptır
                    logout()
                } else {
                    // 3 gün dolmuşsa çıkış yaptır
                    logout()
                }
            }
        }
    }

    // Login process
    fun login(email: String, pass: String, rememberMe: Boolean = false) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Email ve şifre boş olamaz.")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.loginWithEmail(email, pass)) {
                is AuthResult.Success -> {
                    val uid = result.data.uid
                    // Start observing the DB to get the user role
                    userRepository.observeCurrentUser(uid).take(1).collect { user ->
                        if (user != null) {
                            val role = user.role.uppercase()
                            userPrefs.saveUserSession(uid, role, rememberMe)
                            _uiState.value = AuthUiState.Success(uid, role)
                        }
                    }
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is AuthResult.Loading -> { }
            }
        }
    }


    // Register process
    fun register(fullName: String, email: String, pass: String, role: String) {
        if (fullName.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Lütfen tüm alanları doldurunuz.")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.registerWithEmail(fullName, email, pass, role)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState.Success(result.data.uid, role.uppercase())
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is AuthResult.Loading -> { }
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
    
    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            userPrefs.clearUserSession()
            _uiState.value = AuthUiState.Idle
        }
    }
}
