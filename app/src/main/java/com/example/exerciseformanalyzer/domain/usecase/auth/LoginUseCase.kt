package com.example.exerciseformanalyzer.domain.usecase.auth

import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.google.firebase.auth.FirebaseUser

/**
 * Email ve şifre ile giriş yapma use case'i.
 *
 * Tek Sorumluluk: Giriş işlemini başlatmak ve AuthResult döndürmek.
 * Validation (boş alan kontrolü) ViewModel katmanında kalır.
 */
class LoginUseCase(private val authRepository: IAuthRepository) {

    suspend operator fun invoke(email: String, password: String): AuthResult<FirebaseUser> {
        return authRepository.loginWithEmail(email, password)
    }
}
