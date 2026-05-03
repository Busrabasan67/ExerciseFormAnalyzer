package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.repository.LeaderboardEntry
import com.example.exerciseformanalyzer.model.LeaderboardMetric
import com.example.exerciseformanalyzer.model.LeaderboardPeriod
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreActivity
import com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser

import java.util.Date

/**
 * Liderlik tablosu ve istatistik işlemleri için domain-layer sözleşmesi.
 */
interface ILeaderboardRepository {

    /**
     * Belirtilen dönem ve metriğe göre sıralama listesi döner.
     *
     * @param period    Zaman aralığı (Günlük / Haftalık / Aylık / Tüm Zamanlar)
     * @param metric    Sıralama kriteri (Kalori / XP / Seviye)
     * @param currentUid Kullanıcının kendi satırını işaretlemek için
     * @param groupId   Sadece belirli grubun üyeleri arasında sırala; null = global
     * @param customRange CUSTOM periyod için (başlangıç, bitiş) milisaniye çifti
     */
    suspend fun getRankings(
        period: LeaderboardPeriod,
        metric: LeaderboardMetric,
        currentUid: String,
        groupId: String? = null,
        customRange: Pair<Long, Long>? = null
    ): List<LeaderboardEntry>

    /**
     * Belirli bir hastanın antrenman istatistiklerini Firestore'dan toplar.
     * Uzman → hasta detay ekranında kullanılır.
     */
    suspend fun getPatientStats(uid: String, startDate: Date? = null, endDate: Date? = null): WorkoutStats

    suspend fun getRecentActivities(limit: Long = 20): List<FirestoreActivity>
    
    suspend fun getGlobalLeaderboard(limit: Long = 50): List<FirestoreUser>
    
    suspend fun getUserBadges(userId: String): List<FirestoreUserBadgeProgress>
}
