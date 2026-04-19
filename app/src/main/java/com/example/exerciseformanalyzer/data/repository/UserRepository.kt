package com.example.exerciseformanalyzer.data.repository

// UserRepository — Kullanıcı profili yönetimi
//
// Sorumluluğu:
//   - Profil okuma/güncelleme (Room cache + Firestore)
//   - Uzman-hasta eşleşmesi
//   - Profil verisi senkronizasyonu
//
// Veri akışı:
//   Okuma → Önce Room (hızlı, offline), internet varsa Firestore'dan güncelle
//   Yazma → Önce Firestore, başarılıysa Room cache'i güncelle

import com.example.exerciseformanalyzer.data.local.dao.UserDao
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.flow.Flow

class UserRepository(
    private val userDao: UserDao,
    private val firestoreService: FirestoreService
) {

    /**
     * Kullanıcı profilini Room'dan reaktif dinle (UI için).
     * Room değişince Flow otomatik yeni değer yayar — collectAsState ile kullan.
     */
    fun observeCurrentUser(uid: String): Flow<UserEntity?> {
        return userDao.observeUserByUid(uid)
    }

    /**
     * Profil bilgisini güncelle.
     * Önce Firestore'a yaz (veri kaybı olmasın), sonra Room cache'i güncelle.
     */
    suspend fun updateProfile(uid: String, updatedUser: UserEntity): Result<Unit> {
        return try {
            val firestoreProfile = FirestoreUser(
                uid = uid,
                fullName = updatedUser.fullName,
                email = updatedUser.email,
                role = updatedUser.role,
                age = updatedUser.age ?: 0,
                weightKg = updatedUser.weightKg ?: 0f,
                heightCm = updatedUser.heightCm ?: 0f,
                diseases = updatedUser.diseasesJson
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                isSmoker = updatedUser.isSmoker,
                isDrinker = updatedUser.isDrinker,
                expertId = updatedUser.expertUid ?: ""
            )
            firestoreService.saveUserProfile(uid, firestoreProfile)

            // Firestore başarılıysa Room cache'i güncelle (isSynced = true)
            userDao.updateUser(updatedUser.copy(isSynced = true))
            Result.success(Unit)
        } catch (e: Exception) {
            // Firestore erişilemiyorsa (offline) Room'a yaz, sync beklesin
            userDao.updateUser(updatedUser.copy(isSynced = false))
            Result.failure(e)
        }
    }

    /**
     * Uzman, hastayı UID ile kendi listesine ekler.
     */
    suspend fun linkPatientToExpert(patientUid: String, expertUid: String): Result<Unit> {
        return try {
            firestoreService.linkPatientToExpert(patientUid, expertUid)
            // Room'da hasta kaydı varsa expertUid'yi güncelle
            val patientEntity = userDao.getUserByUid(patientUid)
            patientEntity?.let {
                userDao.updateUser(it.copy(expertUid = expertUid, isSynced = true))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uzmanın hasta listesini getir — ExpertDashboard için.
     */
    fun observePatients(expertUid: String): Flow<List<UserEntity>> {
        return userDao.observePatientsByExpert(expertUid)
    }

    /**
     * Firestore'daki profili Room cache'e çek (cihaz değişimi / uygulama yeniden kurulumu).
     * AuthRepository.syncUserProfileFromFirestore ile aynı mantık — burası profile özel.
     */
    suspend fun refreshUserFromFirestore(uid: String) {
        val profile = firestoreService.getUserProfile(uid) ?: return
        val entity = UserEntity(
            uid = uid,
            fullName = profile.fullName,
            email = profile.email,
            role = profile.role,
            age = profile.age,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            diseasesJson = profile.diseases.joinToString(","),
            isSmoker = profile.isSmoker,
            isDrinker = profile.isDrinker,
            expertUid = profile.expertId,
            isSynced = true
        )
        userDao.insertUser(entity)
    }
}
