package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import kotlinx.coroutines.flow.Flow

/**
 * Antrenman plan ve grev ynetimi iin domain-layer szlemesi.
 */
interface IPlanRepository {

    /** Hastann bekleyen (PENDING) grevlerini reaktif izler. */
    fun observePendingTasks(patientUid: String): Flow<List<TaskAssignmentEntity>>

    /** Hastann tm grev gemiini reaktif izler. */
    fun observeAllTasks(patientUid: String): Flow<List<TaskAssignmentEntity>>

    /** Uzmann atad grevleri reaktif izler. */
    fun observeTasksByExpert(expertUid: String): Flow<List<TaskAssignmentEntity>>

    /**
     * Uzman tarafndan yeni grev atamas oluturur.
     * Room'a yazar  Firestore'a anlk ykleme dener.
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

    /** Grevi tamamland olarak iaretler. */
    suspend fun completeTask(taskId: Int, firebaseDocId: String?, reportId: Int)

    /** Firestore'dan hastann grevlerini ekip Room'a senkronize eder. */
    suspend fun syncTasksForPatient(patientUid: String)

    /** Bir uzmann belirli bir hastaya atad tm aktif grevleri pasife eker. */
    suspend fun deactivateDoctorTasks(doctorId: String, patientId: String): Result<Unit>

    // Grev lerleme Metotlar
    fun getPeriodKey(scheduleType: String): String
    suspend fun getTaskProgress(taskId: String, periodKey: String, patientUid: String): TaskProgressEntity
    suspend fun updateTaskProgress(progress: TaskProgressEntity)
    fun observeTaskProgress(taskId: String, periodKey: String): Flow<TaskProgressEntity?>

    suspend fun updateExerciseProgress(
        firebaseTaskId: String,
        patientUid: String,
        exerciseType: String,
        periodKey: String,
        completedSets: Int,
        totalSets: Int
    )
}
