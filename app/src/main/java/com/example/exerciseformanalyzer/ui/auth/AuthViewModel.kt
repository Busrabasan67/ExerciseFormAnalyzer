package com.example.exerciseformanalyzer.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.example.exerciseformanalyzer.domain.repository.IUserRepository
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

    private val authRepository: IAuthRepository = (application as MainApplication).authRepository
    val currentUid: String? get() = authRepository.currentUid
    private val userRepository: IUserRepository = (application as MainApplication).userRepository
    private val userPrefs = (application as MainApplication).userPreferencesRepository

    private val loginUseCase = (application as MainApplication).loginUseCase
    private val registerUseCase = (application as MainApplication).registerUseCase
    private val logoutUseCase = (application as MainApplication).logoutUseCase
    private val checkAutoLoginUseCase = (application as MainApplication).checkAutoLoginUseCase

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkAutoLogin() {
        if (_uiState.value != AuthUiState.Idle) return
        
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = checkAutoLoginUseCase()) {
                is com.example.exerciseformanalyzer.domain.usecase.auth.CheckAutoLoginUseCase.AutoLoginResult.LoggedIn -> {
                    _uiState.value = AuthUiState.Success(result.uid, result.role)
                }
                is com.example.exerciseformanalyzer.domain.usecase.auth.CheckAutoLoginUseCase.AutoLoginResult.NotLoggedIn -> {
                    _uiState.value = AuthUiState.Idle
                    userPrefs.clearUserSession()
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
            when (val result = loginUseCase(email, pass)) {
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
            when (val result = registerUseCase(fullName, email, pass, role)) {
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
            logoutUseCase()
            userPrefs.clearUserSession()
            _uiState.value = AuthUiState.Idle
        }
    }
}
