package com.example.exerciseformanalyzer.data.repository

import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.model.AdminSystemStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminRepository(private val firestoreService: FirestoreService) {

    suspend fun getSystemStats(): AdminSystemStats = withContext(Dispatchers.IO) {
        try {
            val patients = firestoreService.getUserCountByRole("PATIENT")
            val experts = firestoreService.getUserCountByRole("EXPERT")
            val dailyWorkouts = firestoreService.getDailyWorkoutCount()
            val totalCalories = firestoreService.getTotalCaloriesBurned()
            val groups = firestoreService.getActiveGroupCount()

            val detailedStats = firestoreService.getAdminDetailedStats()

            AdminSystemStats(
                totalUsers = patients,
                totalExperts = experts,
                dailyWorkouts = dailyWorkouts,
                totalCalories = totalCalories,
                activeGroups = groups,
                roleDistribution = detailedStats["roleDistribution"] as? Map<String, Int> ?: emptyMap(),
                workoutTrend = detailedStats["workoutTrend"] as? List<Pair<String, Int>> ?: emptyList(),
                exercisePopularity = detailedStats["exercisePopularity"] as? List<Pair<String, Int>> ?: emptyList()
            )
        } catch (e: Exception) {
            AdminSystemStats() // Hata durumunda boş dön
        }
    }

    suspend fun getAllUsers(): List<com.example.exerciseformanalyzer.model.firestore.FirestoreUser> = withContext(Dispatchers.IO) {
        try {
            firestoreService.getAllUsers()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateUserRole(uid: String, newRole: String) = withContext(Dispatchers.IO) {
        try {
            firestoreService.updateUserRole(uid, newRole)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateUserStatus(uid: String, newStatus: String) = withContext(Dispatchers.IO) {
        try {
            firestoreService.updateUserStatus(uid, newStatus)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getBadgeDefinitions(): List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition>> = withContext(Dispatchers.IO) {
        try {
            firestoreService.getBadgeDefinitions()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createBadgeDefinition(badge: com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition): String? = withContext(Dispatchers.IO) {
        try {
            firestoreService.createBadgeDefinition(badge)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteBadgeDefinition(badgeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestoreService.deleteBadgeDefinition(badgeId)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateBadgeDefinition(badgeId: String, updates: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            firestoreService.updateBadgeDefinition(badgeId, updates)
            true
        } catch (e: Exception) {
            false
        }
    }
    suspend fun evaluateBadgeRetroactively(badgeId: String, badge: com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition) = withContext(Dispatchers.IO) {
        try {
            firestoreService.evaluateBadgeRetroactively(badgeId, badge)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Retroaktif rozet hatası: ${e.message}")
        }
    }

    suspend fun getAllGroups(): List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroup> = withContext(Dispatchers.IO) {
        try {
            firestoreService.getAllGroupsAdmin()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteGroup(groupId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestoreService.deleteGroupAdmin(groupId)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            firestoreService.updateGroupSettings(groupId, settings)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getGroupMembers(groupId: String): List<com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember> = withContext(Dispatchers.IO) {
        try {
            firestoreService.getGroupMembers(groupId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addMemberToGroup(groupId: String, userId: String, role: String) = withContext(Dispatchers.IO) {
        try {
            firestoreService.addMemberToGroup(groupId, userId, role)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeMemberFromGroup(groupId: String, userId: String) = withContext(Dispatchers.IO) {
        try {
            firestoreService.removeMemberFromGroup(groupId, userId)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateMemberRole(groupId: String, userId: String, newRole: String) = withContext(Dispatchers.IO) {
        try {
            firestoreService.updateMemberRole(groupId, userId, newRole)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun changeGroupCreator(groupId: String, newCreatorId: String) = withContext(Dispatchers.IO) {
        try {
            firestoreService.changeGroupCreator(groupId, newCreatorId)
            true
        } catch (e: Exception) {
            false
        }
    }
}
