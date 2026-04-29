package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import kotlinx.coroutines.flow.Flow

/**
 * Antrenman planı ve görev yönetimi için domain-layer sözleşmesi.
 */
interface IPlanRepository {

    /** Hastanın bekleyen (PENDING) görevlerini reaktif izler. */
    fun observePendingTasks(patientUid: String): Flow<List<TaskAssignmentEntity>>

    /** Hastanın tüm görev geçmişini reaktif izler. */
    fun observeAllTasks(patientUid: String): Flow<List<TaskAssignmentEntity>>

    /** Uzmanın atadığı görevleri reaktif izler. */
    fun observeTasksByExpert(expertUid: String): Flow<List<TaskAssignmentEntity>>

    /**
     * Uzman tarafından yeni görev ataması oluşturur.
     * Room'a yazar → Firestore'a anlık yükleme dener.
     */
    suspend fun createTaskAssignment(
        expertUid: String,
        patientUid: String,
        title: String,
        note: String,
        dueDate: Long,
        exercises: List<FirestoreExerciseItem>,
        scheduleType: String = "DAILY",
        daysOfWeek: List<Int> = emptyList(),
        autoRepeat: Boolean = false,
        repeatDurationWeeks: Int? = null
    ): Result<Unit>

    /** Görevi tamamlandı olarak işaretler. */
    suspend fun completeTask(taskId: Int, firebaseDocId: String?, reportId: Int)

    /** Firestore'dan hastanın görevlerini çekip Room'a senkronize eder. */
    suspend fun syncTasksForPatient(patientUid: String)
}
