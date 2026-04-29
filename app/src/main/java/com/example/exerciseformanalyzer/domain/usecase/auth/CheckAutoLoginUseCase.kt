package com.example.exerciseformanalyzer.domain.usecase.auth

import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.example.exerciseformanalyzer.domain.repository.IUserRepository
import com.example.exerciseformanalyzer.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Otomatik oturum açma kontrolü use case'i.
 *
 * Uygulama Splash ekranında çağrılır.
 * "Beni hatırla" tercihine ve 3 günlük süreye göre oturumu devam ettirir veya kapatır.
 *
 * @return AutoLoginResult: devam et / farklı rol ile dashboard / giriş ekranına dön
 */
class CheckAutoLoginUseCase(
    private val authRepository: IAuthRepository,
    private val userRepository: IUserRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    sealed class AutoLoginResult {
        /** Oturum geçerli, kullanıcının rolü ile dashboard'a git. */
        data class LoggedIn(val uid: String, val role: String) : AutoLoginResult()
        /** Oturum yok veya süresi dolmuş — giriş ekranına yönlendir. */
        object NotLoggedIn : AutoLoginResult()
    }

    suspend operator fun invoke(): AutoLoginResult {
        val uid = authRepository.currentUid ?: return AutoLoginResult.NotLoggedIn
        if (!authRepository.isLoggedIn) return AutoLoginResult.NotLoggedIn

        val rememberMe = preferencesRepository.rememberMe.first()
        val timestamp = preferencesRepository.loginTimestamp.first()
        val daysPassed = (System.currentTimeMillis() - timestamp) / (1000L * 60 * 60 * 24)

        return if (rememberMe && daysPassed < 3) {
            // Firestore'dan güncel profili çek
            authRepository.syncUserProfileFromFirestore(uid)
            // Room'dan kullanıcı rolünü al
            val user = userRepository.observeCurrentUser(uid).first()
            if (user != null) {
                AutoLoginResult.LoggedIn(uid, user.role.uppercase())
            } else {
                AutoLoginResult.NotLoggedIn
            }
        } else {
            authRepository.signOut()
            AutoLoginResult.NotLoggedIn
        }
    }
}
