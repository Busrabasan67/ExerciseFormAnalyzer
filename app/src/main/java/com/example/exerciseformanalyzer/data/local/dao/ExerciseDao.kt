package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    // Tüm egzersizleri tek seferlik getir (AI modülü için)
    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<ExerciseEntity>

    // Tüm egzersizleri reaktif dinle (gelecekte egzersiz listesi ekranı için)
    @Query("SELECT * FROM exercises")
    fun observeAllExercises(): Flow<List<ExerciseEntity>>

    // AI motoru kuralları getirirken tek egzersiz çeker
    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseById(exerciseId: Int): ExerciseEntity?

    // Firestore'dan senkronize edilen egzersizler için (docId ile çakışma kontrolü)
    @Query("SELECT * FROM exercises WHERE firebaseDocId = :docId LIMIT 1")
    suspend fun getExerciseByFirebaseDocId(docId: String): ExerciseEntity?
}