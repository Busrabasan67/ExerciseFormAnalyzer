package com.example.exerciseformanalyzer.data.repository

import com.example.exerciseformanalyzer.data.local.dao.UserDao
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.remote.FirebaseAuthService
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.domain.model.GoogleAuthResult
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.google.firebase.auth.FirebaseUser

class AuthRepository(
    private val userDao: UserDao,
    private val authService: FirebaseAuthService,
    private val firestoreService: FirestoreService
) : IAuthRepository {

    override val currentUid: String? get() = authService.currentUid
    override val currentUserEmail: String? get() = authService.currentUser?.email
    override val isLoggedIn: Boolean get() = authService.isLoggedIn()
    override val isEmailVerified: Boolean get() = authService.currentUser?.isEmailVerified == true

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
                role = role.uppercase()
            )
            firestoreService.saveUserProfile(firebaseUser.uid, profile)
            cacheUserLocally(firebaseUser.uid, profile)
            AuthResult.Success(firebaseUser)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Kayit sirasinda bilinmeyen hata olustu.")
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): AuthResult<FirebaseUser> {
        return try {
            val firebaseUser = authService.loginWithEmail(email, password)
            syncUserProfileFromFirestore(firebaseUser.uid)
            AuthResult.Success(firebaseUser)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Giris sirasinda bilinmeyen hata olustu.")
        }
    }

    override suspend fun loginWithGoogle(idToken: String): GoogleAuthResult {
        return try {
            val firebaseUser = authService.loginWithGoogle(idToken)
            val existingProfile = firestoreService.getUserProfile(firebaseUser.uid)
            if (existingProfile == null) {
                GoogleAuthResult.RequiresRoleSelection(
                    uid = firebaseUser.uid,
                    fullName = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: ""
                )
            } else {
                cacheUserLocally(firebaseUser.uid, existingProfile)
                GoogleAuthResult.ExistingUser(firebaseUser.uid, existingProfile.role)
            }
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.message ?: "Google girisi sirasinda hata olustu.")
        }
    }

    override suspend fun completeGoogleRegistration(role: String): GoogleAuthResult {
        return try {
            val firebaseUser = authService.currentUser
                ?: return GoogleAuthResult.Error("Google oturumu bulunamadi.")
            val profile = FirestoreUser(
                uid = firebaseUser.uid,
                fullName = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                role = role.uppercase()
            )
            firestoreService.saveUserProfile(firebaseUser.uid, profile)
            cacheUserLocally(firebaseUser.uid, profile)
            GoogleAuthResult.ExistingUser(firebaseUser.uid, profile.role)
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.message ?: "Google kaydi tamamlanamadi.")
        }
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        return try {
            authService.sendEmailVerification()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Dogrulama e-postasi gonderilemedi.")
        }
    }

    override suspend fun reloadUser(): AuthResult<Unit> {
        return try {
            authService.reloadUser()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Kullanici bilgileri guncellenemedi.")
        }
    }

    override fun signOut() {
        authService.signOut()
    }

    override suspend fun updatePassword(newPassword: String): AuthResult<Unit> {
        return try {
            authService.updatePassword(newPassword)
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sifre guncellenemedi.")
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return try {
            authService.sendPasswordReset(email)
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sifre sifirlama e-postasi gonderilemedi.")
        }
    }

    override suspend fun syncUserProfileFromFirestore(uid: String) {
        val profile = firestoreService.getUserProfile(uid) ?: return
        cacheUserLocally(uid, profile)
    }

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
            isSynced = true,
            defaultRestSeconds = profile.defaultRestSeconds,
            profileImageUrl = profile.profileImageUrl,
            gender = profile.gender
        )
        userDao.insertUser(entity)
    }
}
