package com.example.exerciseformanalyzer
//veritabanını ve depoları (Repository) uygulama ilk açıldığında sadece bir kere oluşturup, diğer ekranlara buradan dağıtıyoruz
//eğer her syafadan veritabanına bağlansaydık uygulama çökerdi
import android.app.Application
import com.example.exerciseformanalyzer.data.local.AppDatabase
import com.example.exerciseformanalyzer.data.repository.AuthRepository
import com.example.exerciseformanalyzer.data.repository.WorkoutRepository

class MainApplication : Application() {

    // lazy: Sadece ihtiyaç duyulduğunda oluşturulmasını sağlar (Performans artışı)
    // Veritabanı bağlantısı
    val database by lazy { AppDatabase.getInstance(this) }

    // Uygulamanın her yerinden erişilecek Depolar (Repositories)
    val authRepository by lazy { AuthRepository(database.userDao()) }
    val workoutRepository by lazy {
        WorkoutRepository(
            reportDao = database.workoutReportDao(),
            planDao = database.workoutPlanDao(),
            exerciseDao = database.exerciseDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Uygulama ilk açıldığında yapılacak ekstra ayarlar varsa buraya yazılır
    }
}