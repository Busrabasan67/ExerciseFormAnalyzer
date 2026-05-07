package com.example.exerciseformanalyzer.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.model.ExerciseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DebugViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MainApplication
    private val authRepo = app.authRepository
    private val workoutRepo = app.workoutRepository
    private val userRepo = app.userRepository
    private val prefsRepo = app.userPreferencesRepository
    private val database = app.database

    private val _logText = MutableStateFlow("Bekleniyor...")
    val logText: StateFlow<String> = _logText

    private val testEmail = "testuser1@example.com"
    private val testPassword = "password123"
    private var tempUid = ""

    fun appendLog(msg: String) {
        _logText.value = msg
    }

    fun testRegister() = viewModelScope.launch {
        appendLog("Registering...")
        when (val result = authRepo.registerWithEmail("Test User", testEmail, testPassword, "PATIENT")) {
            is AuthResult.Success -> {
                tempUid = result.data.uid
                appendLog("Register Success! UID: ${result.data.uid}")
            }
            is AuthResult.Error -> appendLog("Register Error: ${result.message}")
            else -> {}
        }
    }

    fun testLogin() = viewModelScope.launch {
        appendLog("Logging in...")
        when (val result = authRepo.loginWithEmail(testEmail, testPassword)) {
            is AuthResult.Success -> {
                tempUid = result.data.uid
                appendLog("Login Success! UID: ${result.data.uid}")
            }
            is AuthResult.Error -> appendLog("Login Error: ${result.message}")
            else -> {}
        }
    }

    fun testFirestoreWrite() = viewModelScope.launch {
        appendLog("Writing to Firestore...")
        val targetUid = if (tempUid.isNotEmpty()) tempUid else authRepo.currentUid
        if (targetUid == null || targetUid.isEmpty()) {
            appendLog("Firestore Write Error: Girdi yapılmış UID bulunamadı. Lütfen önce Login yap.")
            return@launch
        }
        try {
            // Geçerli bir UserEntity uydur
            val dummyEntity = com.example.exerciseformanalyzer.data.local.entity.UserEntity(
                uid = targetUid,
                fullName = "Test User Updated",
                email = testEmail,
                role = "PATIENT",
                age = 25,
                weightKg = 75f,
                heightCm = 180f,
                diseasesJson = "",
                isSmoker = false,
                isDrinker = false,
                expertUid = "",
                isSynced = false
            )
            val result = userRepo.updateProfile(targetUid, dummyEntity)
            if (result.isSuccess) {
                appendLog("Firestore User Write Success! Profile updated.")
            } else {
                appendLog("Firestore User Write Failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            appendLog("Firestore User Write Crash: ${e.message}")
        }
    }

    fun testFirestoreRead() = viewModelScope.launch {
        appendLog("Reading from Firestore...")
        val targetUid = if (tempUid.isNotEmpty()) tempUid else authRepo.currentUid
        if (targetUid == null || targetUid.isEmpty()) {
            appendLog("Firestore Read Error: UID bulunamadı.")
            return@launch
        }
        try {
            val userProfile = app.firestoreService.getUserProfile(targetUid)
            if (userProfile != null) {
                appendLog("Read Success!\nUID: ${userProfile.uid}\nName: ${userProfile.fullName}\nEmail: ${userProfile.email}")
            } else {
                appendLog("Read Error: Döküman bulunamadı.")
            }
        } catch (e: Exception) {
            appendLog("Read Crash: ${e.message}")
        }
    }

    fun testRoomInsert() = viewModelScope.launch {
        appendLog("Inserting to Room...")
        try {
            val report = WorkoutReportEntity(
                userId = 1,
                userUid = tempUid.ifEmpty { "offline_dummy" },
                exerciseId = 1,
                score = 85,
                reps = 12,
                totalTimeSeconds = 120,
                caloriesBurned = 15f,
                feedback = "Good form",
                isSynced = false
            )
            app.database.workoutReportDao().insertReport(report)
            appendLog("Room Insert Success! Rapor eklendi.")
        } catch (e: Exception) {
            appendLog("Room Insert Error: ${e.message}")
        }
    }

    fun testRoomRead() = viewModelScope.launch {
        appendLog("Reading from Room...")
        try {
            val reports = app.database.workoutReportDao().getUnsyncedReports()
            if (reports.isNotEmpty()) {
                val last = reports.last()
                appendLog("Room Read Success!\nUnsynced Total: ${reports.size}\nLatest Reps: ${last.reps}\nLatest Score: ${last.score}")
            } else {
                appendLog("Room Read Success! Hiç senkronize edilmemiş kayıt yok.")
            }
        } catch (e: Exception) {
            appendLog("Room Read Error: ${e.message}")
        }
    }

    fun testDataStoreSave() = viewModelScope.launch {
        appendLog("Saving to DataStore...")
        try {
            prefsRepo.setDarkMode(true)
            prefsRepo.setLanguage("en")
            appendLog("DataStore Save Success!\nDarkMode=true, Lang=en")
        } catch (e: Exception) {
            appendLog("DataStore Save Error: ${e.message}")
        }
    }

    fun testDataStoreRead() = viewModelScope.launch {
        appendLog("Reading from DataStore...")
        try {
            val isDark = prefsRepo.isDarkMode.first()
            val lang = prefsRepo.language.first()
            appendLog("DataStore Read Success!\nDarkMode=$isDark, Lang=$lang")
        } catch (e: Exception) {
            appendLog("DataStore Read Error: ${e.message}")
        }
    }

    fun testOfflineSave() = viewModelScope.launch {
        appendLog("Offline Save (Room'a yazılacak ama firestore hata / engelleme yapmayacak).")
        try {
            workoutRepo.saveWorkoutResult(
                userUid = if (tempUid.isNotEmpty()) tempUid else "offline_dummy_uid",
                localUserId = 1,
                exerciseType = ExerciseType.SQUAT,
                exerciseId = 1,
                score = 99,
                sessionNewReps = 15,
                sessionNewDurationSec = 60,
                feedback = "Offline Test"
            )
            appendLog("Offline Save Success!\nisSynced=false eklendi. SyncWorker'ı bekliyor.")
        } catch (e: Exception) {
            appendLog("Offline Save Error: ${e.message}")
        }
    }

    fun testManuelSync() = viewModelScope.launch {
        appendLog("Manuel Sync Tetikleniyor...")
        try {
            val unsyncedBefore = app.database.workoutReportDao().getUnsyncedReports().size
            if (unsyncedBefore == 0) {
                appendLog("Sync edilecek rapor yok.")
                return@launch
            }

            // SyncWorker kodunu manuel tetikliyoruz (Servisi beklemeden fonksiyonu çağırıyoruz)
            val reports = app.database.workoutReportDao().getUnsyncedReports()
            var successCount = 0
            for (report in reports) {
                try {
                    val firestoreReport = com.example.exerciseformanalyzer.model.firestore.FirestoreWorkoutReport(
                        userId = report.userUid,
                        exerciseId = report.exerciseId.toString(),
                        score = report.score,
                        reps = report.reps,
                        durationSeconds = report.totalTimeSeconds,
                        caloriesBurned = report.caloriesBurned,
                        feedback = report.feedback ?: ""
                    )
                    val docId = app.firestoreService.uploadWorkoutReport(firestoreReport)
                    app.database.workoutReportDao().markAsSynced(report.id, docId)
                    successCount++
                } catch (e: Exception) {
                    appendLog("Tekil rapor hatası: ${e.message}")
                }
            }

            val unsyncedAfter = app.database.workoutReportDao().getUnsyncedReports().size
            appendLog("Manuel Sync Tamamlandı.\nSynced: $successCount\nFailed (Kalan): $unsyncedAfter")
        } catch (e: Exception) {
            appendLog("Sync Error: ${e.message}")
        }
    }
}
