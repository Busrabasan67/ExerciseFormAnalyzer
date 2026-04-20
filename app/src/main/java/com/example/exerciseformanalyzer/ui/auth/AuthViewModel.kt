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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val uid: String, val role: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = (application as MainApplication).authRepository
    private val userRepository: UserRepository = (application as MainApplication).userRepository

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkAutoLogin() {
        if (_uiState.value != AuthUiState.Idle) return
        
        val uid = authRepository.currentUid
        if (uid != null && authRepository.isLoggedIn) {
            _uiState.value = AuthUiState.Loading
            viewModelScope.launch {
                authRepository.syncUserProfileFromFirestore(uid) // Pulls safely
                userRepository.observeCurrentUser(uid).collect { user ->
                    if (user != null) {
                        _uiState.value = AuthUiState.Success(uid, user.role.uppercase())
                    }
                }
            }
        }
    }

    // Login process
    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Email ve şifre boş olamaz.")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.loginWithEmail(email, pass)) {
                is AuthResult.Success -> {
                    // Start observing the DB to get the user role
                    userRepository.observeCurrentUser(result.data.uid).collect { user ->
                        if (user != null) {
                            _uiState.value = AuthUiState.Success(result.data.uid, user.role.uppercase())
                        }
                    }
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is AuthResult.Loading -> { } // repo Loading dönmüyor
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
        authRepository.signOut()
        _uiState.value = AuthUiState.Idle
    }
}
