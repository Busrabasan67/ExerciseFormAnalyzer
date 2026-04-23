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

            AdminSystemStats(
                totalUsers = patients,
                totalExperts = experts,
                dailyWorkouts = dailyWorkouts,
                totalCalories = totalCalories,
                activeGroups = groups
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
}
