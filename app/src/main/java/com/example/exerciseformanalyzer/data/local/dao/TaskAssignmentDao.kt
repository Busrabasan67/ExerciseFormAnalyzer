package com.example.exerciseformanalyzer.data.local.dao

// TaskAssignmentDao — Görev yönetimi sorguları
// PatientDashboard ekranının görev listesini ve
// TaskMarkMissedWorker'ın süre aşımı kontrolünü bu DAO sağlar.
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskAssignmentDao {

    // Uzman görev atadığında çalışır; Firestore'dan senkronize dönen veri varsa üzerine yazar
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskAssignmentEntity): Long

    @Update
    suspend fun updateTask(task: TaskAssignmentEntity)

    @Query("SELECT * FROM task_assignments WHERE firebaseDocId = :docId LIMIT 1")
    suspend fun getTaskByFirebaseDocId(docId: String): TaskAssignmentEntity?

    @Query("SELECT * FROM task_assignments WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Int): TaskAssignmentEntity?

    @Query("SELECT * FROM task_assignments WHERE patientUid = :patientUid AND (status = 'PENDING' OR status = 'IN_PROGRESS')")
    suspend fun getPendingTasksForPatientSync(patientUid: String): List<TaskAssignmentEntity>

    // Hastanın bekleyen aktif görevlerini listele — PatientDashboardScreen
    @Query("SELECT * FROM task_assignments WHERE patientUid = :patientUid AND (status = 'PENDING' OR status = 'IN_PROGRESS') ORDER BY dueDate ASC")
    fun observePendingTasksForPatient(patientUid: String): Flow<List<TaskAssignmentEntity>>

    // Hastanın tüm görevlerini getir (tamamlananlar dahil — geçmiş için)
    @Query("SELECT * FROM task_assignments WHERE patientUid = :patientUid ORDER BY dueDate DESC")
    fun observeAllTasksForPatient(patientUid: String): Flow<List<TaskAssignmentEntity>>

    // Uzman ekranında verilen tüm görevlerin durumunu izlemek için
    @Query("SELECT * FROM task_assignments WHERE expertUid = :expertUid ORDER BY dueDate DESC")
    fun observeTasksByExpert(expertUid: String): Flow<List<TaskAssignmentEntity>>

    // TaskMarkMissedWorker bu sorguyu periyodik olarak çalıştırır:
    // Süresi geçmiş ama hâlâ PENDING olan görevleri bul
    @Query("SELECT * FROM task_assignments WHERE status = 'PENDING' AND dueDate < :nowMs")
    suspend fun getExpiredPendingTasks(nowMs: Long): List<TaskAssignmentEntity>

    // Görevi DONE olarak işaretle (hasta tamamlayınca)
    @Query("UPDATE task_assignments SET status = 'DONE', completedAt = :completedAt, linkedReportId = :reportId WHERE id = :taskId")
    suspend fun markTaskAsDone(taskId: Int, completedAt: Long, reportId: Int?)

    // Görevi MISSED olarak işaretle (TaskMarkMissedWorker çağırır)
    @Query("UPDATE task_assignments SET status = 'MISSED' WHERE id = :taskId")
    suspend fun markTaskAsMissed(taskId: Int)

    @Query("UPDATE task_assignments SET status = 'removed' WHERE id = :taskId")
    suspend fun markTaskAsRemoved(taskId: Int)

    @Query("DELETE FROM task_assignments WHERE id = :taskId")
    suspend fun deleteTask(taskId: Int)

    // Senkronizasyon için
    @Query("SELECT * FROM task_assignments WHERE isSynced = 0")
    suspend fun getUnsyncedTasks(): List<TaskAssignmentEntity>

    @Query("UPDATE task_assignments SET isSynced = 1, firebaseDocId = :docId WHERE id = :taskId")
    suspend fun markTaskAsSynced(taskId: Int, docId: String)

    @Query("UPDATE task_assignments SET status = 'inactive' WHERE expertUid = :doctorId AND patientUid = :patientId AND (status = 'PENDING' OR status = 'IN_PROGRESS')")
    suspend fun deactivateTasksByDoctor(doctorId: String, patientId: String)
}
