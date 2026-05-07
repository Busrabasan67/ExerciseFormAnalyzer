package com.example.exerciseformanalyzer.domain.usecase.workout

import com.example.exerciseformanalyzer.domain.model.TaskContext
import com.example.exerciseformanalyzer.domain.repository.IWorkoutRepository
import com.example.exerciseformanalyzer.model.ExerciseType

/**
 * Egzersiz seansını kaydetme use case'i.
 *
 * Room + Firestore + XP/Streak güncelleme + sosyal akış akışını başlatır.
 * Tüm bu iş mantığı WorkoutRepository.saveWorkoutResult içinde kapsüllüdür.
 */
class SaveWorkoutResultUseCase(private val workoutRepository: IWorkoutRepository) {

    suspend operator fun invoke(
        userUid: String,
        localUserId: Int,
        exerciseType: ExerciseType,
        exerciseId: Int = 1,
        score: Int,
        sessionNewReps: Int,
        sessionNewDurationSec: Long,
        feedback: String?,
        taskContext: TaskContext? = null
    ) {
        workoutRepository.saveWorkoutResult(
            userUid = userUid,
            localUserId = localUserId,
            exerciseType = exerciseType,
            exerciseId = exerciseId,
            score = score,
            sessionNewReps = sessionNewReps,
            sessionNewDurationSec = sessionNewDurationSec,
            feedback = feedback,
            taskContext = taskContext
        )
    }
}
