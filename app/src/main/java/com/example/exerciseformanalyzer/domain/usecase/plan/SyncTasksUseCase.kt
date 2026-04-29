package com.example.exerciseformanalyzer.domain.usecase.plan

import com.example.exerciseformanalyzer.domain.repository.IPlanRepository

/**
 * Firestore'dan hastanın görevlerini çekip Room'a senkronize eden use case'i.
 *
 * Dashboard açıldığında ve SyncWorker tarafından çağrılır.
 */
class SyncTasksUseCase(private val planRepository: IPlanRepository) {

    suspend operator fun invoke(patientUid: String) {
        planRepository.syncTasksForPatient(patientUid)
    }
}
