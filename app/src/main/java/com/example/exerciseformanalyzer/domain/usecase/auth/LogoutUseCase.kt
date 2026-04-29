package com.example.exerciseformanalyzer.domain.usecase.auth

import com.example.exerciseformanalyzer.domain.repository.IAuthRepository

/**
 * Oturum kapatma use case'i.
 */
class LogoutUseCase(private val authRepository: IAuthRepository) {

    operator fun invoke() {
        authRepository.signOut()
    }
}
