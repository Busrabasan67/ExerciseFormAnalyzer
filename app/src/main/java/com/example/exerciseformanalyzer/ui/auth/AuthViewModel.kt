package com.example.exerciseformanalyzer.ui.auth

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.domain.model.GoogleAuthResult
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.example.exerciseformanalyzer.domain.repository.IUserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val uid: String, val role: String) : AuthUiState()
    data class RequiresGoogleRoleSelection(val uid: String, val fullName: String, val email: String) : AuthUiState()
    object LoggedOut : AuthUiState()
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

    private fun s(@StringRes resId: Int): String = getApplication<MainApplication>().getString(resId)

    private fun localizedAuthError(message: String, @StringRes fallbackResId: Int): String {
        return message.takeUnless {
            it.contains("Kayit", ignoreCase = true) ||
                it.contains("Giris", ignoreCase = true) ||
                it.contains("Kullanici", ignoreCase = true) ||
                it.contains("Dogrulama", ignoreCase = true) ||
                it.contains("Sifre", ignoreCase = true) ||
                it.contains("Google girisi", ignoreCase = true) ||
                it.contains("Google kaydi", ignoreCase = true) ||
                it.contains("gonderilemedi", ignoreCase = true) ||
                it.contains("tamamlanamadi", ignoreCase = true)
        } ?: s(fallbackResId)
    }

    fun checkAutoLogin() {
        if (_uiState.value != AuthUiState.Idle) return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = checkAutoLoginUseCase()) {
                is com.example.exerciseformanalyzer.domain.usecase.auth.CheckAutoLoginUseCase.AutoLoginResult.LoggedIn -> {
                    _uiState.value = AuthUiState.Success(result.uid, result.role)
                }
                is com.example.exerciseformanalyzer.domain.usecase.auth.CheckAutoLoginUseCase.AutoLoginResult.NotLoggedIn -> {
                    userPrefs.clearUserSession()
                    _uiState.value = AuthUiState.Idle
                }
            }
        }
    }

    fun login(email: String, pass: String, rememberMe: Boolean = false) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error(s(R.string.auth_email_password_required))
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = loginUseCase(email, pass)) {
                is AuthResult.Success -> {
                    val uid = result.data.uid
                    userRepository.observeCurrentUser(uid).take(1).collect { user ->
                        if (user != null) {
                            val role = user.role.uppercase()
                            userPrefs.saveUserSession(uid, role, rememberMe)
                            _uiState.value = AuthUiState.Success(uid, role)
                        } else {
                            _uiState.value = AuthUiState.Error(s(R.string.auth_profile_not_found))
                        }
                    }
                }
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(localizedAuthError(result.message, R.string.auth_login_failed))
                is AuthResult.Loading -> Unit
            }
        }
    }

    fun register(fullName: String, email: String, pass: String, role: String) {
        if (fullName.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error(s(R.string.error_empty_fields))
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = registerUseCase(fullName, email, pass, role)) {
                is AuthResult.Success -> {
                    val finalRole = role.uppercase()
                    userPrefs.saveUserSession(result.data.uid, finalRole, false)
                    _uiState.value = AuthUiState.Success(result.data.uid, finalRole)
                }
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(localizedAuthError(result.message, R.string.auth_register_failed))
                is AuthResult.Loading -> Unit
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            logoutUseCase()
            userPrefs.clearUserSession()
            _uiState.value = AuthUiState.LoggedOut
            onComplete()
        }
    }

    fun loginWithGoogle(idToken: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.loginWithGoogle(idToken)) {
                is GoogleAuthResult.ExistingUser -> {
                    val role = result.role.uppercase()
                    userPrefs.saveUserSession(result.uid, role, true)
                    _uiState.value = AuthUiState.Success(result.uid, role)
                }
                is GoogleAuthResult.RequiresRoleSelection -> {
                    _uiState.value = AuthUiState.RequiresGoogleRoleSelection(
                        uid = result.uid,
                        fullName = result.fullName,
                        email = result.email
                    )
                }
                is GoogleAuthResult.Error -> _uiState.value = AuthUiState.Error(localizedAuthError(result.message, R.string.auth_google_failed))
            }
        }
    }

    fun completeGoogleRegistration(role: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.completeGoogleRegistration(role)) {
                is GoogleAuthResult.ExistingUser -> {
                    val finalRole = result.role.uppercase()
                    userPrefs.saveUserSession(result.uid, finalRole, true)
                    _uiState.value = AuthUiState.Success(result.uid, finalRole)
                }
                is GoogleAuthResult.RequiresRoleSelection -> {
                    _uiState.value = AuthUiState.RequiresGoogleRoleSelection(result.uid, result.fullName, result.email)
                }
                is GoogleAuthResult.Error -> _uiState.value = AuthUiState.Error(localizedAuthError(result.message, R.string.auth_google_failed))
            }
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch { authRepository.sendEmailVerification() }
    }

    fun reloadUser() {
        viewModelScope.launch { authRepository.reloadUser() }
    }

    fun setError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun sendPasswordResetEmail(email: String, onSuccess: () -> Unit) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error(s(R.string.password_reset_email_required))
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState.Idle
                    onSuccess()
                }
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(localizedAuthError(result.message, R.string.password_reset_failed))
                else -> _uiState.value = AuthUiState.Idle
            }
        }
    }
}
