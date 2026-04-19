package com.example.exerciseformanalyzer

// MainApplication — Uygulama yaşam döngüsünün en üst noktası.
// Firebase, Room ve DataStore gibi ağır bağımlılıklar burada lazy olarak başlatılır.
// Lazy: İlk kullanımda oluşturulur — uygulama açılış süresini yavaşlatmaz.

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.exerciseformanalyzer.data.local.AppDatabase
import com.example.exerciseformanalyzer.data.preferences.UserPreferencesRepository
import com.example.exerciseformanalyzer.data.remote.FirebaseAuthService
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.data.repository.AuthRepository
import com.example.exerciseformanalyzer.data.repository.WorkoutRepository
import com.example.exerciseformanalyzer.worker.SyncWorker
import com.google.firebase.FirebaseApp
import java.util.concurrent.TimeUnit

class MainApplication : Application() {

    // Lokal veritabanı
    val database by lazy { AppDatabase.getInstance(this) }

    // Firebase servisleri
    val firebaseAuthService by lazy { FirebaseAuthService() }
    val firestoreService by lazy { FirestoreService() }

    // DataStore — Tema, dil ve oturum cache'i
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }

    // Repository'ler — ViewModel'lerin bağlandığı katman
    val authRepository by lazy {
        AuthRepository(
            userDao = database.userDao(),
            authService = firebaseAuthService,
            firestoreService = firestoreService
        )
    }

    val workoutRepository by lazy {
        WorkoutRepository(
            reportDao = database.workoutReportDao(),
            planDao = database.workoutPlanDao(),
            exerciseDao = database.exerciseDao(),
            userDao = database.userDao(),
            firestoreService = firestoreService
        )
    }

    val userRepository by lazy {
        com.example.exerciseformanalyzer.data.repository.UserRepository(
            userDao = database.userDao(),
            firestoreService = firestoreService
        )
    }

    val planRepository by lazy {
        com.example.exerciseformanalyzer.data.repository.PlanRepository(
            planDao = database.workoutPlanDao(),
            taskDao = database.taskAssignmentDao(),
            firestoreService = firestoreService
        )
    }


    override fun onCreate() {
        super.onCreate()

        // Firebase SDK başlatma
        // NOT: google-services.json olmadan bu satır crash verir!
        // google-services.json'ı app/ klasörüne ekleyip tekrar build alın.
        FirebaseApp.initializeApp(this)

        // WorkManager — Periyodik senkronizasyon ayarı
        // Sadece internet bağlantısı varken çalışır
        scheduleSyncWorker()
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Sadece internette
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,            // Her 15 dakikada bir kontrol et
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // KEEP: Uygulama kapatılıp açılsa bile aynı Worker devam eder (duplicate oluşmaz)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}