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
        UserBadgeProgressEntity::class,
        TaskProgressEntity::class
    ],
    version = 14,
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
    abstract fun taskProgressDao(): TaskProgressDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN firstName TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN lastName TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN diseaseInfo TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN hasHernia INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN hasMeniscus INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN activityLevel TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN goal TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN painAreasJson TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN exerciseLevel TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN defaultRestSeconds INTEGER NOT NULL DEFAULT 90")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_assignments ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_progress` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `taskId` TEXT NOT NULL, 
                        `patientUid` TEXT NOT NULL, 
                        `periodKey` TEXT NOT NULL, 
                        `progressJson` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `lastUpdatedAt` INTEGER NOT NULL, 
                        `isSynced` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exercise_app_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
