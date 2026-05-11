package com.example.exerciseformanalyzer.ui.profile

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.model.WorkoutStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val workoutRepo = (application as MainApplication).workoutRepository

    val currentUid: String get() = authRepo.currentUid ?: ""

    private fun localizedAuthError(message: String?, @StringRes fallbackResId: Int): String {
        return message
            ?.takeUnless {
                it.contains("Sifre", ignoreCase = true) ||
                    it.contains("sifirlama", ignoreCase = true) ||
                    it.contains("gonderilemedi", ignoreCase = true) ||
                    it.contains("guncellenemedi", ignoreCase = true)
            }
            ?: getApplication<MainApplication>().getString(fallbackResId)
    }

    fun observeCurrentUser(): Flow<UserEntity?> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observeCurrentUser(uid)
    }

    /**
     * Profil ekranı için istatistikleri gözlemler.
     * PatientViewModel'deki mantığın benzeri kullanılmıştır.
     */
    fun observePatientStats(): Flow<WorkoutStats> {
        val uid = currentUid
        if (uid.isEmpty()) return flowOf(WorkoutStats())
        
        return combine(
            workoutRepo.observePatientHistory(uid),
            planRepo.observeTasksForPatientHome(uid)
        ) { reports, tasks ->
            val completionStats = mapOf(
                "PENDING" to tasks.count { it.status.lowercase() in listOf("pending", "active") },
                "IN_PROGRESS" to tasks.count { it.status.lowercase() == "in_progress" },
                "COMPLETED" to tasks.count { it.status.lowercase() in listOf("done", "completed") }
            )
            WorkoutStats(
                dailyCalories = emptyList(), // Profil ekranında grafik gerekmiyor
                scoreTrend = emptyList(),
                completionStats = completionStats
            )
        }
    }

    fun updateProfile(updatedUser: UserEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.updateProfile(updatedUser.uid, updatedUser)
            onResult(result.isSuccess)
        }
    }

    fun uploadProfileImage(imageBytes: ByteArray, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.uploadProfileImage(currentUid, imageBytes)
            onResult(result)
        }
    }

    fun sendPasswordReset(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val email = authRepo.currentUserEmail
        if (email.isNullOrEmpty()) {
            onResult(false, getApplication<MainApplication>().getString(R.string.email_not_found_error))
            return
        }
        viewModelScope.launch {
            when (val result = authRepo.sendPasswordResetEmail(email)) {
                is com.example.exerciseformanalyzer.domain.model.AuthResult.Success -> onResult(true, null)
                is com.example.exerciseformanalyzer.domain.model.AuthResult.Error -> onResult(false, localizedAuthError(result.message, R.string.password_reset_failed))
                else -> onResult(false, getApplication<MainApplication>().getString(R.string.unknown_error))
            }
        }
    }

    fun updatePassword(newPassword: String, onResult: (Boolean, String?) -> Unit) {
        if (newPassword.length < 6) {
            onResult(false, getApplication<MainApplication>().getString(R.string.password_min_length_error))
            return
        }
        viewModelScope.launch {
            when (val result = authRepo.updatePassword(newPassword)) {
                is com.example.exerciseformanalyzer.domain.model.AuthResult.Success -> onResult(true, null)
                is com.example.exerciseformanalyzer.domain.model.AuthResult.Error -> onResult(false, localizedAuthError(result.message, R.string.password_update_failed))
                else -> onResult(false, getApplication<MainApplication>().getString(R.string.unknown_error))
            }
        }
    }
}
