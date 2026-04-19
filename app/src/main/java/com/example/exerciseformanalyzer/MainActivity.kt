package com.example.exerciseformanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.exerciseformanalyzer.ui.CameraPreviewScreen
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.PermissionScreen
import com.example.exerciseformanalyzer.ui.theme.ExerciseFormAnalyzerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/**
 * Uygulamanın tek Activity'si — Compose + MVVM mimarisi.
 *
 * Sorumlulukları:
 * 1. Edge-to-edge tam ekran ayarı
 * 2. Kamera izin akışı (Accompanist Permissions)
 * 3. ViewModel bağlantısı
 * 4. Compose içerik ayarı
 */
class MainActivity : ComponentActivity() {

    // ViewModel Activity yaşam döngüsüne bağlı
    //private val viewModel: MainViewModel by viewModels()

    private val viewModel: MainViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //oluşturduğumuz tablolar veri eklemedn gözükmediği için onları
        // zorunlu olarak oluşturmamızı sağladım sonradan yorum satırına aldım
//        val app = application as MainApplication
//        lifecycleScope.launch {
//            // Veritabanına boş bir sorgu atarak onu uyanmaya zorluyoruz
//            app.database.userDao().getAllPatients()
//        }


        //tabloları oluşturuken içine veri ekelyip görmek için yazılan kod
//        val app = application as MainApplication
//        lifecycleScope.launch {
//            // 1. Önce veritabanında kullanıcı var mı diye bakıyoruz
//            val currentUsers = app.database.userDao().getAllPatients()
//
//            // 2. Eğer liste boşsa, otomatik olarak seni eklesin
//            if (currentUsers.isEmpty()) {
//                val newUser = com.example.exerciseformanalyzer.data.local.entity.UserEntity(
//                    fullName = "Zeynep Can",
//                    email = "zeynep@duzce.edu.tr",
//                    passwordHash = "123456", // Şimdilik basit bir şifre
//                    role = "STUDENT",
//                    isSynced = false
//                )
//                app.database.userDao().insertUser(newUser)
//                android.util.Log.d("DB_TEST", "Kullanıcı başarıyla eklendi!")
//            }
//        }
        setContent {
            ExerciseFormAnalyzerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExerciseAnalyzerApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ExerciseAnalyzerApp(viewModel: MainViewModel) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewScreen(viewModel = viewModel)
    } else {
        PermissionScreen(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
        )
    }
}

