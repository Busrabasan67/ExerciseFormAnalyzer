package com.example.exerciseformanalyzer.data.local.dao
//Egzersiz Havuzu Komutları
//Admin tarafından sisteme eklenen hareketleri (Squat, Şınav vb.) ve yapay zeka sınır açılarını yönetir.
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.ExerciseEntity

@Dao
interface ExerciseDao {

    // Admin yeni bir egzersiz eklediğinde çalışır.
    // Aynı ID ile gelirse eskisinin üzerine yazar (REPLACE).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    // Geliştirici 3'ün arayüzde egzersizleri listelemesi için tüm havuzu getirir.
    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<ExerciseEntity>

    // Yapay zeka (Geliştirici 1) kamerayı açtığında, hareketin JSON kurallarını çekmek için kullanır.
    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseById(exerciseId: Int): ExerciseEntity?
}