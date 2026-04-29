package com.example.exerciseformanalyzer.domain.usecase.auth

import com.example.exerciseformanalyzer.domain.model.AuthResult
import com.example.exerciseformanalyzer.domain.repository.IAuthRepository
import com.google.firebase.auth.FirebaseUser

/**
 * Yeni hesap kayıt use case'i.
 *
 * Tek Sorumluluk: Firebase Auth + Firestore + Room akışını başlatmak.
 */
class RegisterUseCase(private val authRepository: IAuthRepository) {

    suspend operator fun invoke(
        fullName: String,
        email: String,
        password: String,
        role: String
    ): AuthResult<FirebaseUser> {
        return authRepository.registerWithEmail(fullName, email, password, role)
    }
}
