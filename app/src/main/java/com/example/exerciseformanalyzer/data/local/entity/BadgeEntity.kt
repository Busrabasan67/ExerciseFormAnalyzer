package com.example.exerciseformanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "badge_definitions")
data class BadgeEntity(
    @PrimaryKey val firebaseDocId: String,
    val name: String,
    val description: String,
    val iconUrl: String,
    val type: String, // SYSTEM, QUEST, DOCTOR
    val category: String,
    val targetValue: Int,
    val xpReward: Int,
    val createdBy: String,
    val isSynced: Boolean = true
)

@Entity(tableName = "user_badge_progress")
data class UserBadgeProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val badgeId: String,
    val currentProgress: Int,
    val targetValue: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long?,
    val isSynced: Boolean = false
)
