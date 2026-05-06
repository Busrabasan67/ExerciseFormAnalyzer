package com.example.exerciseformanalyzer.domain.usecase.plan

import com.example.exerciseformanalyzer.domain.repository.IPlanRepository
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem

/**
 * Uzmanın hastaya görev atama use case'i.
 *
 * Room + Firestore yazma akışını başlatır.
 */
class AssignTaskUseCase(private val planRepository: IPlanRepository) {

    suspend operator fun invoke(
        expertUid: String,
        patientUid: String,
        patientName: String,
        title: String,
        note: String,
        dueDate: Long,
        exercises: List<FirestoreExerciseItem>,
        scheduleType: String = "DAILY",
        daysOfWeek: List<Int> = emptyList(),
        autoRepeat: Boolean = false,
        repeatDurationWeeks: Int? = null
    ): Result<Unit> {
        return planRepository.createTaskAssignment(
            expertUid = expertUid,
            patientUid = patientUid,
            patientName = patientName,
            title = title,
            note = note,
            dueDate = dueDate,
            exercises = exercises,
            scheduleType = scheduleType,
            daysOfWeek = daysOfWeek,
            autoRepeat = autoRepeat,
            repeatDurationWeeks = repeatDurationWeeks
        )
    }
}
