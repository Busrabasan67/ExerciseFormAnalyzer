package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem
import kotlinx.coroutines.flow.Flow

/**
 * Antrenman plan ve görev yönetimi için domain-layer sözleşmesi.
 */
interface IPlanRepository {

    /** Hastanın bekleyen (PENDING) görevlerini reaktif izler. */
    fun observePendingTasks(patientUid: String): Flow<List<TaskAssignmentEntity>>

    /** Hastanın tüm görev geçmişini reaktif izler. */
    fun observeAllTasks(patientUid: String): Flow<List<TaskAssignmentEntity>>

    /** Uzmanın atadığı görevleri reaktif izler (geriye dönük uyumluluk). */
    fun observeTasksByExpert(expertUid: String): Flow<List<TaskAssignmentEntity>>

    /**
     * UZMAN TAKİP EKRANI — sadece bu uzmanın oluşturduğu görevleri döner.
     * Status filtresi UYGULANMAZ. Tüm görevler görünür.
     * UI katmanında filtreleme yapılır.
     */
    fun observeTasksForDoctorTracking(doctorId: String): Flow<List<TaskAssignmentEntity>>

    /**
     * HASTA ANA EKRANI — sadece bu hastaya atanmış görevleri döner.
     * doctorId kontrolü YAPILMAZ. patientId ile sorgular.
     * Filtreleme, zaman ve period mantığı ViewModel'de yapılır.
     */
    fun observeTasksForPatientHome(patientId: String): Flow<List<TaskAssignmentEntity>>

    /**
     * Uzman tarafından yeni görev ataması oluşturur.
     * Room'a yazar → Firestore'a anlık yükleme dener.
     */
    suspend fun createTaskAssignment(
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
    ): Result<Unit>

    /** Mevcut bir görevi günceller. */
    suspend fun updateTaskAssignment(
        taskId: Int,
        firebaseDocId: String?,
        expertUid: String,
        patientUid: String,
        patientName: String,
        title: String,
        note: String,
        dueDate: Long,
        exercises: List<FirestoreExerciseItem>,
        scheduleType: String,
        daysOfWeek: List<Int>,
        autoRepeat: Boolean,
        repeatDurationWeeks: Int?
    ): Result<Unit>

    /** Görevi tamamen siler (Room + Firestore). */
    suspend fun deleteTaskAssignment(taskId: Int, firebaseDocId: String?): Result<Unit>

    /** Görevi tamamlandı olarak işaretler. */
    suspend fun completeTask(taskId: Int, firebaseDocId: String?, reportId: Int)

    /** Firestore'dan hastanın görevlerini çekip Room'a senkronize eder. */
    suspend fun syncTasksForPatient(patientUid: String)

    /** Firestore'dan uzmanın atadığı görevleri çekip Room'a senkronize eder. */
    suspend fun syncTasksForExpert(expertUid: String)

    /** Firestore'dan hastanın görevlerini canlı izler ve Room'a yazar. */
    fun observeAndSyncTasksForPatient(patientUid: String): Flow<Unit>

    /** Firestore'dan uzmanın görevlerini canlı izler ve Room'a yazar. */
    fun observeAndSyncTasksForExpert(expertUid: String): Flow<Unit>

    /** Bir uzmanın belirli bir hastaya atadığı tüm aktif görevleri pasife çeker. */
    suspend fun deactivateDoctorTasks(doctorId: String, patientId: String): Result<Unit>

    suspend fun removeTaskFromHome(taskId: Int, firebaseDocId: String?): Result<Unit>

    suspend fun hideTaskFromPatientHistory(taskId: Int): Result<Unit>

    // Görev İlerleme Metotları
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
        totalSets: Int,
        taskExerciseCount: Int
    )
}
