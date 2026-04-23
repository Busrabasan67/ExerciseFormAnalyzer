package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    // Yeni kullanıcı ekler; aynı email gelirse üzerine yazar (Firebase sync'ten dönen veri)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("UPDATE users SET xp = :xp, level = :level, streak = :streak, lastExerciseDate = :lastDate WHERE uid = :uid")
    suspend fun updateUserGamification(uid: String, xp: Int, level: Int, streak: Int, lastDate: String)

    // Kullanıcı profili güncellendiğinde (profil düzenleme ekranı)
    @Update
    suspend fun updateUser(user: UserEntity)

    // Email ile kullanıcı bul — Lokal giriş kontrolü için
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    // Firebase UID ile kullanıcı bul — Firebase Auth giriş sonrası cache kontrolü
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUserByUid(uid: String): UserEntity?

    // Mevcut giriş yapan kullanıcıyı akış olarak izle (profil ekranı için reaktif)
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun observeUserByUid(uid: String): Flow<UserEntity?>

    // Uzmanın hasta listesi — ExpertDashboard ekranı için
    @Query("SELECT * FROM users WHERE role = 'PATIENT' AND expertUid = :expertUid")
    fun observePatientsByExpert(expertUid: String): Flow<List<UserEntity>>

    // Tüm hastalar — Admin paneli için
    @Query("SELECT * FROM users WHERE role = 'PATIENT'")
    suspend fun getAllPatients(): List<UserEntity>

    // Firestore'a henüz senkronize edilmemiş kullanıcıları getir (SyncWorker)
    @Query("SELECT * FROM users WHERE isSynced = 0")
    suspend fun getUnsyncedUsers(): List<UserEntity>

    // Senkronizasyon tamamlandı işareti
    @Query("UPDATE users SET isSynced = 1 WHERE uid = :uid")
    suspend fun markUserAsSynced(uid: String)
}