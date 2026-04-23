plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Firebase servislerini etkinleştirmek için gerekli plugin.
    // google-services.json dosyasını projeye ekledikten sonra bu satır aktif olacak.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.exerciseformanalyzer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.exerciseformanalyzer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Assets klasörüne MediaPipe model dosyasının eklenmesi için
    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // MediaPipe Tasks Vision - Pose Landmarker
    implementation(libs.mediapipe.tasks.vision)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Android Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


// --- VERİTABANI VE GÜVENLİK KÜTÜPHANELERİ ---
    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Şifre Hashleme (BCrypt) — Lokal auth için (Firebase Auth gelince opsiyonel)
    implementation("org.mindrot:jbcrypt:0.4")

    // Arka Plan Senkronizasyonu (WorkManager) — SyncWorker + TaskMarkMissedWorker
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- FİREBASE ---
    // Firebase BOM: tüm Firebase kütüphanelerinin versiyonunu tek yerden yönetir.
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

    // Firebase Authentication — Email/şifre ve Google Sign-In
    implementation("com.google.firebase:firebase-auth-ktx")

    // Google Sign-In (Firebase Auth Google ile giriş için gerekli)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Cloud Firestore — Kullanıcı profilleri, planlar, gruplar, raporlar
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Firebase Storage — Egzersiz ön izleme video/simülasyon içerikleri (altyapı)
    implementation("com.google.firebase:firebase-storage-ktx")

    // Coroutines için Google Play Services desteği (Firebase auth task'larını await() ile kullanmak için)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // --- JET PACK ---
    // Jetpack DataStore (Preferences) — Tema, dil gibi basit ayarlar için
    // NOT: Room yerine DataStore tercih sebebi: Tema/dil ayarı ilişkisel yapı gerektirmez;
    //      key-value olarak saklanmalı. DataStore, SharedPreferences'ın tip güvenli, coroutine uyumlu modern alternatifi.
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Compose Navigation — Ekranlar arası geçiş (Login → Dashboard → Kamera)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- GRAFİKLER ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- BİLDİRİMLER (FCM) ---
    implementation("com.google.firebase:firebase-messaging-ktx")
}
