package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.google.firebase.auth.FirebaseUser

/**
 * Kimlik doğrulama için domain-layer sözleşmesi.
 *
 * Bu interface sayesinde:
 *  - ViewModel / Use Case sadece bu abstrakta bağımlıdır
 *  - Concrete impl (AuthRepository) framework'e (Firebase) bağlı kalabilir
 *  - Test ortamında FakeAuthRepository kolayca yazılabilir
 */
interface IAuthRepository {

    /** Şu an oturum açmış kullanıcının UID'si; oturum yoksa null. */
    val currentUid: String?

    /** Şu an oturum açmış kullanıcının e-posta adresi. */
    val currentUserEmail: String?

    /** Firebase oturumunun açık olup olmadığı. */
    val isLoggedIn: Boolean

    /** E-posta doğrulanmış mı? */
    val isEmailVerified: Boolean

    /**
     * Email ve şifre ile yeni hesap oluşturur.
     * Firebase Auth + Firestore profil kaydı + Room cache akışını yönetir.
     */
    suspend fun registerWithEmail(
        fullName: String,
        email: String,
        password: String,
        role: String
    ): AuthResult<FirebaseUser>

    /**
     * Email ve şifre ile giriş yapar.
     * Firebase Auth doğrulama + Room cache güncelleme akışını yönetir.
     */
    suspend fun loginWithEmail(email: String, password: String): AuthResult<FirebaseUser>

    /**
     * Google Sign-In token ile giriş yapar.
     * İlk girişte Firestore'da profil oluşturur.
     */
    suspend fun loginWithGoogle(idToken: String): AuthResult<FirebaseUser>

    /** E-posta doğrulama linki gönderir. */
    suspend fun sendEmailVerification(): AuthResult<Unit>

    /** Kullanıcı bilgilerini yeniler (doğrulama durumunu güncellemek için). */
    suspend fun reloadUser(): AuthResult<Unit>

    /** Oturumu kapatır. */
    fun signOut()

    /**
     * Firestore'daki kullanıcı profilini Room cache'e çeker.
     * Cihaz değişimi veya uygulama yeniden kurulumunda çağrılır.
     */
    suspend fun syncUserProfileFromFirestore(uid: String)
}
