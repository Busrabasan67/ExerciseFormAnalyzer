package com.example.exerciseformanalyzer.data.local.dao

import androidx.room.*
import com.example.exerciseformanalyzer.data.local.entity.BadgeEntity
import com.example.exerciseformanalyzer.data.local.entity.UserBadgeProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge_definitions")
    fun observeAllBadges(): Flow<List<BadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadges(badges: List<BadgeEntity>)

    @Query("SELECT * FROM user_badge_progress WHERE userId = :userId")
    fun observeUserProgress(userId: String): Flow<List<UserBadgeProgressEntity>>

    @Query("SELECT * FROM user_badge_progress WHERE userId = :userId AND badgeId = :badgeId")
    suspend fun getProgress(userId: String, badgeId: String): UserBadgeProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: UserBadgeProgressEntity)

    @Query("DELETE FROM user_badge_progress WHERE userId = :userId AND badgeId = :badgeId")
    suspend fun deleteProgress(userId: String, badgeId: String)
}
