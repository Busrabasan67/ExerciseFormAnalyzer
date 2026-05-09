package com.example.exerciseformanalyzer.ui.social

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import com.example.exerciseformanalyzer.data.repository.LeaderboardEntry
import com.example.exerciseformanalyzer.model.LeaderboardMetric
import com.example.exerciseformanalyzer.model.LeaderboardPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    private val leaderboardRepo = (application as MainApplication).leaderboardRepository
    private val authRepo = (application as MainApplication).authRepository

    private val _rankings = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val rankings: StateFlow<List<LeaderboardEntry>> = _rankings.asStateFlow()

    private var fetchJob: kotlinx.coroutines.Job? = null

    private val _selectedPeriod = MutableStateFlow(LeaderboardPeriod.ALL_TIME)
    val selectedPeriod: StateFlow<LeaderboardPeriod> = _selectedPeriod.asStateFlow()

    private val _selectedMetric = MutableStateFlow(LeaderboardMetric.XP)
    val selectedMetric: StateFlow<LeaderboardMetric> = _selectedMetric.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customDateRange: StateFlow<Pair<Long, Long>?> = _customDateRange.asStateFlow()

    private var currentGroupId: String? = null

    init {
        loadRankings()
    }

    fun setPeriod(period: LeaderboardPeriod) {
        _selectedPeriod.value = period
        if (period != LeaderboardPeriod.CUSTOM) {
            loadRankings(currentGroupId)
        }
    }

    fun setMetric(metric: LeaderboardMetric) {
        _selectedMetric.value = metric
        loadRankings(currentGroupId)
    }

    fun setCustomRange(start: Long, end: Long) {
        _customDateRange.value = start to end
        loadRankings(currentGroupId)
    }

    fun loadRankings(groupId: String? = null) {
        // Parametre olarak gelmediyse mevcut olanı kullan
        val targetGroupId = groupId ?: currentGroupId
        if (groupId != null) currentGroupId = groupId
        
        val uid = authRepo.currentUid ?: return
        
        fetchJob?.cancel() // Önceki isteği iptal et (Race condition önlemi)
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = leaderboardRepo.getRankings(
                    period = _selectedPeriod.value,
                    metric = _selectedMetric.value,
                    currentUid = uid,
                    groupId = targetGroupId,
                    customRange = _customDateRange.value
                )
                // Veri geldiğinde hala aynı grubun verisini mi bekliyoruz? (Çift koruma)
                if (targetGroupId == currentGroupId) {
                    _rankings.value = data
                }
            } catch (e: Exception) {
                if (targetGroupId == currentGroupId) {
                    _rankings.value = emptyList()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _myBadgeProgress = MutableStateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress>>(emptyList())
    val myBadgeProgress: StateFlow<List<com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress>> = _myBadgeProgress.asStateFlow()

    private val _badgeDefinitions = MutableStateFlow<List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition>>>(emptyList())
    val badgeDefinitions: StateFlow<List<Pair<String, com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition>>> = _badgeDefinitions.asStateFlow()

    fun fetchMyBadges() {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            try {
                val defs = leaderboardRepo.getBadgeDefinitions()
                _badgeDefinitions.value = defs
                
                val progress = leaderboardRepo.getUserBadges(uid)
                _myBadgeProgress.value = progress
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
