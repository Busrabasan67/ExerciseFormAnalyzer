package com.example.exerciseformanalyzer.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutPlanEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = (application as MainApplication).authRepository
    private val userRepo = (application as MainApplication).userRepository
    private val planRepo = (application as MainApplication).planRepository
    private val workoutRepo = (application as MainApplication).workoutRepository

    val currentUid: String get() = authRepo.currentUid ?: ""

    fun observeCurrentUser(): Flow<UserEntity?> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observeCurrentUser(uid)
    }

    // Patient Specific
    fun observeMyTasks(): Flow<List<TaskAssignmentEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return planRepo.observePendingTasks(uid)
    }

    fun observeMyReports(): Flow<List<WorkoutReportEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return workoutRepo.observePatientHistory(uid)
    }

    // Expert Specific
    fun observeMyPatients(): Flow<List<UserEntity>> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyFlow()
        return userRepo.observePatients(uid)
    }
}
