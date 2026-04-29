package com.example.exerciseformanalyzer.domain.usecase.user

import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.domain.repository.IUserRepository

/**
 * Kullanıcı profili güncelleme use case'i.
 *
 * Firestore'a yazar, başarılıysa Room cache'i günceller.
 * Offline'da Room'a isSynced=false olarak kaydeder; SyncWorker halleder.
 */
class UpdateProfileUseCase(private val userRepository: IUserRepository) {

    suspend operator fun invoke(uid: String, updatedUser: UserEntity): Result<Unit> {
        return userRepository.updateProfile(uid, updatedUser)
    }
}
