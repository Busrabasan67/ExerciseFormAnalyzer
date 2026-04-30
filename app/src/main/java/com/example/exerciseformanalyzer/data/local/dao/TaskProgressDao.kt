package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.exerciseformanalyzer.data.local.entity.TaskProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: TaskProgressEntity): Long

    @Update
    suspend fun updateProgress(progress: TaskProgressEntity)

    @Query("SELECT * FROM task_progress WHERE taskId = :taskId AND periodKey = :periodKey LIMIT 1")
    suspend fun getProgress(taskId: String, periodKey: String): TaskProgressEntity?

    @Query("SELECT * FROM task_progress WHERE patientUid = :patientUid")
    fun observeAllProgress(patientUid: String): Flow<List<TaskProgressEntity>>

    @Query("SELECT * FROM task_progress WHERE taskId = :taskId AND periodKey = :periodKey LIMIT 1")
    fun observeProgress(taskId: String, periodKey: String): Flow<TaskProgressEntity?>

    @Query("SELECT * FROM task_progress WHERE isSynced = 0")
    suspend fun getUnsyncedProgress(): List<TaskProgressEntity>

    @Query("UPDATE task_progress SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)
}
