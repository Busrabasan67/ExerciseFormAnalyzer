package com.example.exerciseformanalyzer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.exerciseformanalyzer.data.local.dao.*
import com.example.exerciseformanalyzer.data.local.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

//   1 → Başlangıç (UserEntity, ExerciseEntity, WorkoutPlanEntity, WorkoutReportEntity)
//   2 → UserEntity/WorkoutReportEntity genişletildi; TaskAssignmentEntity, GroupEntity, GroupMemberEntity eklendi
//   3 → ExerciseSessionEntity eklendi
//   4 → TaskAssignmentEntity modifiye edildi (expertUid, title, note, exercisesJson eklendi; eski kolonlar silindi)
//
// TODO: Production öncesi Migration sınıfları yazılmalıdır.
//       Şu an fallbackToDestructiveMigration da duruyor ancak 3->4 geçişi için veri kaybetmemek adına spesifik migration eklendi.
@Database(
    entities = [
        UserEntity::class,
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        WorkoutReportEntity::class,
        TaskAssignmentEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExerciseSessionEntity::class,
        BadgeEntity::class,
        UserBadgeProgressEntity::class
    ],
    version = 6,
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
    abstract fun badgeDao(): BadgeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Yeni tabloyu oluşturuyoruz
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_assignments_new (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `firebaseDocId` TEXT, 
                        `patientUid` TEXT NOT NULL, 
                        `expertUid` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `note` TEXT NOT NULL, 
                        `dueDate` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `exercisesJson` TEXT NOT NULL, 
                        `completedAt` INTEGER, 
                        `linkedReportId` INTEGER, 
                        `isSynced` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Mevcut veriyi eski tablodan yeniye aktarıyoruz, yeni kolonlara default veriler yazıyoruz
                db.execSQL("""
                    INSERT INTO task_assignments_new (
                        id, firebaseDocId, patientUid, expertUid, title, note, dueDate, status, exercisesJson, completedAt, linkedReportId, isSynced
                    )
                    SELECT 
                        id, firebaseDocId, patientUid, '', '', '', dueDate, status, '[]', completedAt, linkedReportId, isSynced
                    FROM task_assignments
                """.trimIndent())

                // Eski tabloyu silip yenisinin ismini güncelliyoruz
                db.execSQL("DROP TABLE task_assignments")
                db.execSQL("ALTER TABLE task_assignments_new RENAME TO task_assignments")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exercise_app_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
