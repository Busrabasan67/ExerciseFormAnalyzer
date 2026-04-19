package com.example.exerciseformanalyzer.data.remote

// FirebaseAuthService — Firebase Authentication işlemleri
// Email/şifre ve Google Sign-In akışlarını yönetir.
// Repository katmanı bu servisi kullanır; UI doğrudan bağlanmaz.
//
// KURULUM NOTU: Bu sınıf çalışmadan önce:
//   1. Firebase Console'da proje oluşturulmalı
//   2. google-services.json dosyası app/ klasörüne eklenmelidir
//   3. Firebase Console > Authentication > Sign-in methods'dan
//      Email/Password ve Google etkinleştirilmelidir

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthService {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Şu an giriş yapmış kullanıcı (null ise giriş yapılmamış)
    val currentUser: FirebaseUser? get() = auth.currentUser

    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Email ve şifre ile yeni hesap oluşturur.
     * Başarılı olursa FirebaseUser döner; hata olursa exception fırlatır.
     * AuthRepository bu exception'ı yakalayıp Result<> olarak sarar.
     */
    suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: throw Exception("Kayıt başarısız: Kullanıcı oluşturulamadı.")
    }

    /**
     * Email ve şifre ile giriş yapar.
     * Başarılı olursa FirebaseUser döner; giriş hatasında exception fırlatır.
     */
    suspend fun loginWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: throw Exception("Giriş başarısız: Kullanıcı bulunamadı.")
    }

    /**
     * Google Sign-In akışından dönen idToken ile Firebase girişi tamamlar.
     * Bu metodu çağırmadan önce Google Sign-In Activity sonucundan idToken alınmalıdır.
     * AuthViewModel içinde Google Sign-In launcher kullanılacak.
     */
    suspend fun loginWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: throw Exception("Google girişi başarısız.")
    }

    /**
     * Şifre unutma — kullanıcının emailine sıfırlama linki gönderir.
     */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /**
     * Oturumu kapatır.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Kullanıcı giriş durumunu kontrol et — Splash ekranında kullanılır.
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null
}
