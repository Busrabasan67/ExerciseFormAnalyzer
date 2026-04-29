package com.example.exerciseformanalyzer.domain.usecase.user

import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.domain.repository.IUserRepository
import kotlinx.coroutines.flow.Flow

/**
 * Mevcut kullanıcıyı Room'dan reaktif olarak izleyen use case'i.
 */
class GetCurrentUserUseCase(private val userRepository: IUserRepository) {

    operator fun invoke(uid: String): Flow<UserEntity?> {
        return userRepository.observeCurrentUser(uid)
    }
}
