package com.example.exerciseformanalyzer.domain.usecase.workout

import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.domain.repository.IWorkoutRepository
import kotlinx.coroutines.flow.Flow

/**
 * Hastanın egzersiz geçmişini gerçek zamanlı izleyen use case'i.
 *
 * Room Flow döner — UI otomatik güncellenir.
 */
class ObservePatientHistoryUseCase(private val workoutRepository: IWorkoutRepository) {

    operator fun invoke(userUid: String): Flow<List<WorkoutReportEntity>> {
        return workoutRepository.observePatientHistory(userUid)
    }
}
