package com.example.exerciseformanalyzer.data.remote

// FirebaseStorageService — Firebase Storage altyapı iskelet sınıfı
//
// Mevcut kullanım: Egzersiz ön izleme video/GIF URL'lerinin saklanması
// Gelecekte: Kullanıcı profil fotoğrafı, paylaşılan rapor görselleri
//
// TODO: Bu sınıf şu an yalnızca altyapı kurulumunu gösteriyor.
//       Gerçek video/görsel yükleme işlemleri Faz 3'te eklenecek.

import android.net.Uri
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await

class FirebaseStorageService {

    private val storage = Firebase.storage

    // Storage yolları — sabitler
    companion object {
        const val EXERCISE_PREVIEWS_PATH = "exercise_previews/"  // Egzersiz ön izleme içerikleri
        const val PROFILE_PHOTOS_PATH    = "profile_photos/"     // Kullanıcı profil fotoğrafları
    }

    /**
     * Egzersiz ön izleme içeriğinin download URL'sini getirir.
     * ExerciseEntity.previewVideoUrl alanına yazılır.
     *
     * @param exerciseDocId Firestore egzersiz döküman ID'si (dosya adı olarak kullanılır)
     * @return Download URL (string) veya null (dosya yoksa)
     */
    suspend fun getExercisePreviewUrl(exerciseDocId: String): String? {
        return try {
            storage.reference
                .child("$EXERCISE_PREVIEWS_PATH$exerciseDocId.mp4")
                .downloadUrl
                .await()
                .toString()
        } catch (e: Exception) {
            // Dosya yoksa null döner — ExerciseEntity.previewVideoUrl null kalır
            null
        }
    }

    /**
     * Kullanıcı profil fotoğrafını Storage'a yükler.
     * TODO: Faz 3'te ProfileScreen ile entegre edilecek.
     *
     * @param userUid   Firebase Auth UID (dosya adı olarak kullanılır)
     * @param imageUri  Yüklenecek görselin lokal URI'si
     * @return Download URL
     */
    suspend fun uploadProfilePhoto(userUid: String, imageUri: Uri): String {
        val ref = storage.reference.child("$PROFILE_PHOTOS_PATH$userUid.jpg")
        ref.putFile(imageUri).await()
        return ref.downloadUrl.await().toString()
    }
}
