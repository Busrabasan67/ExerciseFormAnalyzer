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
import com.example.exerciseformanalyzer.data.repository.AdminRepository
import com.example.exerciseformanalyzer.data.repository.AuthRepository
import com.example.exerciseformanalyzer.data.repository.GroupRepository
import com.example.exerciseformanalyzer.data.repository.LeaderboardRepository
import com.example.exerciseformanalyzer.data.repository.PlanRepository
import com.example.exerciseformanalyzer.data.repository.UserRepository
import com.example.exerciseformanalyzer.data.repository.WorkoutRepository
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.example.exerciseformanalyzer.domain.repository.IGroupRepository
import com.example.exerciseformanalyzer.domain.repository.ILeaderboardRepository
import com.example.exerciseformanalyzer.domain.repository.IPlanRepository
import com.example.exerciseformanalyzer.domain.repository.IUserRepository
import com.example.exerciseformanalyzer.domain.repository.IWorkoutRepository
import com.example.exerciseformanalyzer.domain.usecase.auth.CheckAutoLoginUseCase
import com.example.exerciseformanalyzer.domain.usecase.auth.LoginUseCase
import com.example.exerciseformanalyzer.domain.usecase.auth.LogoutUseCase
import com.example.exerciseformanalyzer.domain.usecase.auth.RegisterUseCase
import com.example.exerciseformanalyzer.domain.usecase.plan.AssignTaskUseCase
import com.example.exerciseformanalyzer.domain.usecase.plan.SyncTasksUseCase
import com.example.exerciseformanalyzer.domain.usecase.user.GetCurrentUserUseCase
import com.example.exerciseformanalyzer.domain.usecase.user.UpdateProfileUseCase
import com.example.exerciseformanalyzer.domain.usecase.workout.ObservePatientHistoryUseCase
import com.example.exerciseformanalyzer.domain.usecase.workout.SaveWorkoutResultUseCase
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

    // ── REPOSITORY'LER (Interface türüyle expose edildi — test edilebilir) ──────
    val authRepository: IAuthRepository by lazy {
        AuthRepository(
            userDao = database.userDao(),
            authService = firebaseAuthService,
            firestoreService = firestoreService
        )
    }

    val workoutRepository: IWorkoutRepository by lazy {
        WorkoutRepository(
            reportDao = database.workoutReportDao(),
            taskDao = database.taskAssignmentDao(),
            exerciseDao = database.exerciseDao(),
            userDao = database.userDao(),
            badgeDao = database.badgeDao(),
            firestoreService = firestoreService
        )
    }

    val userRepository: IUserRepository by lazy {
        UserRepository(
            userDao = database.userDao(),
            firestoreService = firestoreService
        )
    }

    val planRepository: IPlanRepository by lazy {
        PlanRepository(
            planDao = database.workoutPlanDao(),
            taskDao = database.taskAssignmentDao(),
            firestoreService = firestoreService
        )
    }

    val leaderboardRepository: ILeaderboardRepository by lazy {
        LeaderboardRepository(
            firestoreService = firestoreService
        )
    }

    val groupRepository: IGroupRepository by lazy {
        GroupRepository(
            groupDao = database.groupDao(),
            firestoreService = firestoreService
        )
    }

    // AdminRepository — domain interface'i henüz yok (Admin dashboard basit, sonra eklenecek)
    val adminRepository by lazy {
        AdminRepository(
            firestoreService = firestoreService
        )
    }

    // ── USE CASE'LER — ViewModel'ler bunları kullanır ──────────────────────────
    val loginUseCase by lazy { LoginUseCase(authRepository) }
    val registerUseCase by lazy { RegisterUseCase(authRepository) }
    val logoutUseCase by lazy { LogoutUseCase(authRepository) }
    val checkAutoLoginUseCase by lazy {
        CheckAutoLoginUseCase(authRepository, userRepository, userPreferencesRepository)
    }
    val saveWorkoutResultUseCase by lazy { SaveWorkoutResultUseCase(workoutRepository) }
    val observePatientHistoryUseCase by lazy { ObservePatientHistoryUseCase(workoutRepository) }
    val getCurrentUserUseCase by lazy { GetCurrentUserUseCase(userRepository) }
    val updateProfileUseCase by lazy { UpdateProfileUseCase(userRepository) }
    val assignTaskUseCase by lazy { AssignTaskUseCase(planRepository) }
    val syncTasksUseCase by lazy { SyncTasksUseCase(planRepository) }


    override fun onCreate() {
        super.onCreate()

        // Firebase SDK başlatma
        // NOT: google-services.json olmadan bu satır crash verir!
        // google-services.json'ı app/ klasörüne ekleyip tekrar build alın.
        FirebaseApp.initializeApp(this)

        // WorkManager — Periyodik senkronizasyon ayarı
        // Sadece internet bağlantısı varken çalışır
        scheduleSyncWorker()
        scheduleTaskMarkMissedWorker()
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

    private fun scheduleTaskMarkMissedWorker() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // Çok acil değil, pil bitikse çalışmasın
            .build()

        val checkRequest = PeriodicWorkRequestBuilder<com.example.exerciseformanalyzer.worker.TaskMarkMissedWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "task_mark_missed_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            checkRequest
        )
    }
}