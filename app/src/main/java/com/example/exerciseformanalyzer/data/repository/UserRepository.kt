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
import com.example.exerciseformanalyzer.domain.repository.IUserRepository
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val userDao: UserDao,
    private val firestoreService: FirestoreService
) : IUserRepository {

    /**
     * Kullanıcı profilini Room'dan reaktif dinle (UI için).
     * Room değişince Flow otomatik yeni değer yayar — collectAsState ile kullan.
     */
    override fun observeCurrentUser(uid: String): Flow<UserEntity?> {
        return userDao.observeUserByUid(uid)
    }

    /**
     * Profil bilgisini güncelle.
     * Önce Firestore'a yaz (veri kaybı olmasın), sonra Room cache'i güncelle.
     */
    override suspend fun updateProfile(uid: String, updatedUser: UserEntity): Result<Unit> {
        return try {
            val firestoreProfile = FirestoreUser(
                uid = uid,
                fullName = updatedUser.fullName,
                email = updatedUser.email,
                role = updatedUser.role,
                age = updatedUser.age ?: 0,
                weightKg = updatedUser.weightKg ?: 0f,
                heightCm = updatedUser.heightCm ?: 0f,
                firstName = updatedUser.firstName ?: "",
                lastName = updatedUser.lastName ?: "",
                diseaseInfo = updatedUser.diseaseInfo ?: "",
                hasHernia = updatedUser.hasHernia,
                hasMeniscus = updatedUser.hasMeniscus,
                activityLevel = updatedUser.activityLevel ?: "medium",
                goal = updatedUser.goal ?: "general_health",
                painAreas = updatedUser.painAreasJson?.let {
                    try { org.json.JSONArray(it).let { arr -> List(arr.length()) { i -> arr.getString(i) } } } catch(e:Exception) { emptyList() }
                } ?: emptyList(),
                exerciseLevel = updatedUser.exerciseLevel ?: "beginner",
                diseases = updatedUser.diseasesJson
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                isSmoker = updatedUser.isSmoker,
                isDrinker = updatedUser.isDrinker,
                expertId = updatedUser.expertUid ?: "",
                status = updatedUser.status,
                profileImageUrl = updatedUser.profileImageUrl,
                gender = updatedUser.gender,
                defaultRestSeconds = updatedUser.defaultRestSeconds
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
     * Hastayı e-posta ile Firestore'da ara (sadece PATIENT olanları döndürür).
     */
    override suspend fun findPatientByEmail(email: String): FirestoreUser? {
        return firestoreService.findUserByEmail(email)
    }

    override suspend fun searchPatientsByEmail(query: String): List<FirestoreUser> {
        return firestoreService.searchPatientsByEmail(query)
    }

    /**
     * Uzman, hastayı UID ile kendi listesine ekler.
     */
    override suspend fun linkPatientToExpert(patientUid: String, expertUid: String): Result<Unit> {
        return try {
            firestoreService.linkPatientToExpert(patientUid, expertUid)
            // Room'da hasta kaydı varsa expertUid'yi güncelle, yoksa Firestore'dan çekip kaydet
            val patientProfile = firestoreService.getUserProfile(patientUid)
            if (patientProfile != null) {
                val existing = userDao.getUserByUid(patientUid)
                val entity = UserEntity(
                    id = existing?.id ?: 0,
                    uid = patientProfile.uid,
                    fullName = patientProfile.fullName,
                    email = patientProfile.email,
                    role = patientProfile.role,
                    age = patientProfile.age,
                    weightKg = patientProfile.weightKg,
                    heightCm = patientProfile.heightCm,
                    firstName = patientProfile.firstName,
                    lastName = patientProfile.lastName,
                    diseaseInfo = patientProfile.diseaseInfo,
                    hasHernia = patientProfile.hasHernia,
                    hasMeniscus = patientProfile.hasMeniscus,
                    activityLevel = patientProfile.activityLevel,
                    goal = patientProfile.goal,
                    painAreasJson = org.json.JSONArray(patientProfile.painAreas).toString(),
                    exerciseLevel = patientProfile.exerciseLevel,
                    diseasesJson = patientProfile.diseases.joinToString(","),
                    isSmoker = patientProfile.isSmoker,
                    isDrinker = patientProfile.isDrinker,
                    expertUid = expertUid,
                    isSynced = true,
                    status = patientProfile.status
                )
                userDao.insertUser(entity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removePatientFromExpert(patientId: String, expertId: String): Result<Unit> {
        return try {
            // 1. Firestore'da bağı kopar
            firestoreService.unlinkPatientFromExpert(patientId)
            // 2. İstekleri temizle
            firestoreService.markRequestsAsRemoved(expertId, patientId)
            
            // 3. Room'da güncelle (expertUid'yi boşalt)
            val existing = userDao.getUserByUid(patientId)
            if (existing != null) {
                userDao.updateUser(existing.copy(expertUid = ""))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isDoctorPatientRelationActive(doctorId: String, patientId: String): Boolean {
        return try {
            firestoreService.checkRelationActive(doctorId, patientId)
        } catch (e: Exception) {
            false
        }
    }

    /** BAĞLANTI İSTEKLERİ - YENİ MODÜL */
    override suspend fun sendConnectionRequest(patient: FirestoreUser, doctor: UserEntity): Result<Unit> {
        return try {
            val request = com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest(
                doctorId = doctor.uid,
                doctorName = doctor.fullName,
                patientId = patient.uid,
                patientName = patient.fullName,
                patientEmail = patient.email,
                status = "pending"
            )
            firestoreService.sendConnectionRequest(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPendingRequests(patientId: String): List<com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest> {
        return try {
            firestoreService.getPendingRequestsForPatient(patientId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getSentRequestsByDoctor(doctorId: String): List<com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest> {
        return try {
            firestoreService.getSentRequestsByDoctor(doctorId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** İsteği yanıtla ve gerekirse bağı kur. */
    override suspend fun respondToConnectionRequest(
        requestId: String,
        status: String,
        patientUid: String,
        expertUid: String,
        patientName: String,
        patientEmail: String
    ): Result<Unit> {
        return try {
            if (status == "ACCEPTED") {
                firestoreService.acceptConnectionRequestTransaction(
                    requestId, expertUid, patientUid, patientName, patientEmail
                )
            } else {
                firestoreService.updateRequestStatus(requestId, status)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uzmanın hasta listesini getir — ExpertDashboard için.
     */
    override fun observePatients(expertUid: String): Flow<List<UserEntity>> {
        return userDao.observePatientsByExpert(expertUid)
    }

    /**
     * Firestore'daki profili Room cache'e çek (cihaz değişimi / uygulama yeniden kurulumu).
     * AuthRepository.syncUserProfileFromFirestore ile aynı mantık — burası profile özel.
     */
    suspend fun refreshUserFromFirestore(uid: String) {
        val profile = firestoreService.getUserProfile(uid) ?: return
        val existing = userDao.getUserByUid(uid)
        val entity = UserEntity(
            id = existing?.id ?: 0,
            uid = uid,
            fullName = profile.fullName,
            email = profile.email,
            role = profile.role,
            age = profile.age,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            firstName = profile.firstName,
            lastName = profile.lastName,
            diseaseInfo = profile.diseaseInfo,
            hasHernia = profile.hasHernia,
            hasMeniscus = profile.hasMeniscus,
            activityLevel = profile.activityLevel,
            goal = profile.goal,
            painAreasJson = org.json.JSONArray(profile.painAreas).toString(),
            exerciseLevel = profile.exerciseLevel,
            diseasesJson = profile.diseases.joinToString(","),
            isSmoker = profile.isSmoker,
            isDrinker = profile.isDrinker,
            expertUid = profile.expertId,
            isSynced = true,
            status = profile.status,
            profileImageUrl = profile.profileImageUrl,
            gender = profile.gender,
            defaultRestSeconds = profile.defaultRestSeconds
        )
        userDao.insertUser(entity)
    }

    /**
     * Uzmanın bağlı hastalarını Firestore'dan çekip Room'a yazar.
     * Bu sayede ExpertDashboard açıldığında hastalar güncel kalır.
     */
    override suspend fun syncPatientsForExpert(expertUid: String) {
        try {
            val patients = firestoreService.getPatientsByExpert(expertUid)
            patients.forEach { profile ->
                val existing = userDao.getUserByUid(profile.uid)
                val entity = UserEntity(
                    id = existing?.id ?: 0,
                    uid = profile.uid,
                    fullName = profile.fullName,
                    email = profile.email,
                    role = profile.role,
                    age = profile.age,
                    weightKg = profile.weightKg,
                    heightCm = profile.heightCm,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    diseaseInfo = profile.diseaseInfo,
                    hasHernia = profile.hasHernia,
                    hasMeniscus = profile.hasMeniscus,
                    activityLevel = profile.activityLevel,
                    goal = profile.goal,
                    painAreasJson = org.json.JSONArray(profile.painAreas).toString(),
                    exerciseLevel = profile.exerciseLevel,
                    diseasesJson = profile.diseases.joinToString(","),
                    isSmoker = profile.isSmoker,
                    isDrinker = profile.isDrinker,
                    expertUid = profile.expertId, // = expertUid
                    isSynced = true,
                    status = profile.status,
                    profileImageUrl = profile.profileImageUrl,
                    gender = profile.gender,
                    defaultRestSeconds = profile.defaultRestSeconds
                )
                userDao.insertUser(entity)
            }
        } catch (e: Exception) {
            // Hata olursa (offline vb.) sadece olanları kullan
        }
    }

    /**
     * Hasta kendi hesabına girdiğinde bağlı olduğu uzmanı Firestore'dan çekip cache'leyebiliriz.
     */
    override suspend fun syncExpertProfileLocally(expertUid: String) {
        if (expertUid.isNotEmpty()) {
            refreshUserFromFirestore(expertUid)
        }
    }

    override suspend fun uploadProfileImage(uid: String, imageBytes: ByteArray): Result<String> {
        return try {
            val storageRef = Firebase.storage.reference.child("profile_images/$uid.jpg")
            storageRef.putBytes(imageBytes).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            // 1. Firestore'u güncelle
            firestoreService.updateProfileImageUrl(uid, downloadUrl)
            
            // 2. Room cache'i güncelle
            val existing = userDao.getUserByUid(uid)
            if (existing != null) {
                userDao.updateUser(existing.copy(profileImageUrl = downloadUrl))
            }
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
