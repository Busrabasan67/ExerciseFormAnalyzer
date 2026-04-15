package com.example.exerciseformanalyzer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.exerciseformanalyzer.data.local.dao.*
import com.example.exerciseformanalyzer.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        WorkoutReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // DAO'lara erişim
    abstract fun userDao(): UserDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun workoutReportDao(): WorkoutReportDao

    // getInstance() metodunu buraya ekliyoruz
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exercise_app_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}