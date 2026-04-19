package com.example.exerciseformanalyzer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.exerciseformanalyzer.data.local.dao.*
import com.example.exerciseformanalyzer.data.local.entity.*

// version geçmişi:
//   1 → Başlangıç (UserEntity, ExerciseEntity, WorkoutPlanEntity, WorkoutReportEntity)
//   2 → UserEntity/WorkoutReportEntity genişletildi; TaskAssignmentEntity, GroupEntity, GroupMemberEntity eklendi
//   3 → ExerciseSessionEntity eklendi
//
// TODO: Production öncesi Migration sınıfları yazılmalıdır.
//       Şu an fallbackToDestructiveMigration kullanılıyor (geliştirme için uygun).
@Database(
    entities = [
        UserEntity::class,
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        WorkoutReportEntity::class,
        TaskAssignmentEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExerciseSessionEntity::class  // YENİ — oturum meta verisi
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // Mevcut DAO'lar
    abstract fun userDao(): UserDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun workoutReportDao(): WorkoutReportDao
    abstract fun taskAssignmentDao(): TaskAssignmentDao
    abstract fun groupDao(): GroupDao

    // Yeni DAO
    abstract fun exerciseSessionDao(): ExerciseSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exercise_app_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
