package com.example.exerciseformanalyzer.data.repository

// AuthRepository — Kimlik doğrulama işlemleri için tek giriş noktası (IAuthRepository implementasyonu)
//
// Veri akışı:
//   Firebase Auth (kimlik doğrulama) → Firestore (profil kayıt) → Room (lokal cache)
//
// ViewModel bu repository'yi kullanır; UI Firebase SDK'ya doğrudan erişmez.

import com.example.exerciseformanalyzer.data.local.dao.UserDao
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.remote.FirebaseAuthService
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.google.firebase.auth.FirebaseUser

class AuthRepository(
    private val userDao: UserDao,
    private val authService: FirebaseAuthService,
    private val firestoreService: FirestoreService
) : IAuthRepository {

    // Şu an giriş yapmış kullanıcının UID'si
    override val currentUid: String? get() = authService.currentUid
    override val currentUserEmail: String? get() = authService.currentUser?.email
    override val isLoggedIn: Boolean get() = authService.isLoggedIn()
    override val isEmailVerified: Boolean get() = authService.currentUser?.isEmailVerified == true

    /**
     * Email ve şifre ile yeni kayıt.
     * 1. Firebase Auth'ta hesap oluştur
     * 2. Firestore'a profil kaydet
     * 3. Room cache'e yaz (isSynced = true çünkü Firestore'a gitti)
     */
    override suspend fun registerWithEmail(
        fullName: String,
        email: String,
        password: String,
        role: String
    ): AuthResult<FirebaseUser> {
        return try {
            val firebaseUser = authService.registerWithEmail(email, password)

            val profile = FirestoreUser(
                uid = firebaseUser.uid,
                fullName = fullName,
                email = email,
                role = role
            )
            firestoreService.saveUserProfile(firebaseUser.uid, profile)

            // Lokal cache'e yaz
            cacheUserLocally(firebaseUser.uid, profile)

            AuthResult.Success(firebaseUser)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Kayıt sırasında bilinmeyen hata oluştu.")
        }
    }

    /**
     * Email ve şifre ile giriş.
     * 1. Firebase Auth ile doğrula
     * 2. Firestore'dan profili çek
     * 3. Room cache'i güncelle
     */
    override suspend fun loginWithEmail(email: String, password: String): AuthResult<FirebaseUser> {
        return try {
            val firebaseUser = authService.loginWithEmail(email, password)
            syncUserProfileFromFirestore(firebaseUser.uid)
            AuthResult.Success(firebaseUser)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Giriş sırasında bilinmeyen hata oluştu.")
        }
    }

    /**
     * Google Sign-In ile giriş.
     * Google Activity'den dönen idToken burada işlenir.
     */
    override suspend fun loginWithGoogle(idToken: String): AuthResult<FirebaseUser> {
        return try {
            val firebaseUser = authService.loginWithGoogle(idToken)

            // Firestore'da profil var mı kontrol et; yoksa oluştur
            val existingProfile = firestoreService.getUserProfile(firebaseUser.uid)
            if (existingProfile == null) {
                val newProfile = FirestoreUser(
                    uid = firebaseUser.uid,
                    fullName = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    role = "PATIENT"  // Google ile ilk girişte default rol
                )
                firestoreService.saveUserProfile(firebaseUser.uid, newProfile)
                cacheUserLocally(firebaseUser.uid, newProfile)
            } else {
                cacheUserLocally(firebaseUser.uid, existingProfile)
            }

            AuthResult.Success(firebaseUser)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Google girişi sırasında hata oluştu.")
        }
    }

    /**
     * Oturumu kapatır ve lokal UID cache'ini temizlemez
     * (uygulama offline açılabilmeli; clearUserCache ayrı çağrılabilir).
     */
    override fun signOut() {
        authService.signOut()
    }

    /**
     * Firestore'daki kullanıcı profilini Room'a yazarak lokal cache'i günceller.
     * Cihaz değişimi veya uygulama yeniden kurulumunda çağrılır.
     */
    override suspend fun syncUserProfileFromFirestore(uid: String) {
        val profile = firestoreService.getUserProfile(uid) ?: return
        cacheUserLocally(uid, profile)
    }

    // --- PRIVATE ---

    private suspend fun cacheUserLocally(uid: String, profile: FirestoreUser) {
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
            isSynced = true  // Firestore'dan geldiği için senkronize
        )
        userDao.insertUser(entity) // OnConflictStrategy.REPLACE ile günceller
    }
}