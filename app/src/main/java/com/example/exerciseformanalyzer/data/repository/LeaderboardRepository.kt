package com.example.exerciseformanalyzer.data.repository

import com.example.exerciseformanalyzer.domain.repository.ILeaderboardRepository
import com.example.exerciseformanalyzer.model.LeaderboardMetric
import com.example.exerciseformanalyzer.model.LeaderboardPeriod
import com.example.exerciseformanalyzer.model.WorkoutStats
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.model.firestore.FirestoreWorkoutReport
import com.example.exerciseformanalyzer.model.firestore.FirestoreTaskAssignment
import com.example.exerciseformanalyzer.model.firestore.FirestoreActivity
import com.example.exerciseformanalyzer.model.firestore.FirestoreUserBadgeProgress
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class LeaderboardEntry(
    val userId: String,
    val fullName: String,
    val value: Float,
    val rank: Int = 0,
    val isMe: Boolean = false
)

class LeaderboardRepository(private val firestoreService: FirestoreService) : ILeaderboardRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun getRankings(
        period: LeaderboardPeriod,
        metric: LeaderboardMetric,
        currentUid: String,
        groupId: String?,
        customRange: Pair<Long, Long>?
    ): List<LeaderboardEntry> {
        return when (period) {
            LeaderboardPeriod.ALL_TIME -> {
                if (metric == LeaderboardMetric.XP) {
                    if (groupId != null) {
                        // Grup içi XP sıralaması için özel agregasyon veya filtreleme
                        // Prototip için: aggregateReports metodunu XP metriğiyle kullanabiliriz
                        // Veya sadece o grubun üyelerini Users tablosundan filtreleyebiliriz.
                        // En doğru yol: Grup üyelerini alıp Users tablosunda "IN" sorgusu yapmak.
                        val groupMemberIds = db.collection("group_members")
                            .whereEqualTo("groupId", groupId)
                            .get().await()
                            .documents.mapNotNull { it.getString("userId") }
                        
                        if (groupMemberIds.isEmpty()) return emptyList()

                        // Firestore "whereIn" limiti 10-30 arasıdır. Daha fazlası için agregasyon lazım.
                        // Basitlik için aggregateReports'u çağıralım (Workout bazlı XP hesaplar) 
                        // veya Users tablosundan çekip bellekte filtreleyelim.
                        val snapshot = db.collection("users")
                            .whereEqualTo("role", "PATIENT")
                            .orderBy("xp", Query.Direction.DESCENDING)
                            .get().await()
                        
                        snapshot.documents
                            .mapNotNull { it.toObject(FirestoreUser::class.java) }
                            .filter { it.uid in groupMemberIds }
                            .mapIndexed { index, user ->
                                LeaderboardEntry(
                                    userId = user.uid,
                                    fullName = user.fullName,
                                    value = user.xp.toFloat(),
                                    rank = index + 1,
                                    isMe = user.uid == currentUid
                                )
                            }
                    } else {
                        // Global XP sıralaması
                        val query = db.collection("users")
                            .whereEqualTo("role", "PATIENT")
                            .orderBy("xp", Query.Direction.DESCENDING)
                            .limit(50)
                        
                        val snapshot = query.get().await()
                        snapshot.documents.mapIndexed { index, doc ->
                            val user = doc.toObject(FirestoreUser::class.java)
                            LeaderboardEntry(
                                userId = user?.uid ?: "",
                                fullName = user?.fullName ?: "Adsız",
                                value = user?.xp?.toFloat() ?: 0f,
                                rank = index + 1,
                                isMe = user?.uid == currentUid
                            )
                        }
                    }
                } else {
                    // Kalori bazlı tüm zamanlar (veya diğer metrikler) için Workout raporlarını topla
                    aggregateReports(null, null, metric, currentUid, groupId)
                }
            }
            LeaderboardPeriod.DAILY -> aggregateReports(getStartOfDay(), null, metric, currentUid, groupId)
            LeaderboardPeriod.WEEKLY -> aggregateReports(getStartOfLast7Days(), null, metric, currentUid, groupId)
            LeaderboardPeriod.MONTHLY -> aggregateReports(getStartOfLast30Days(), null, metric, currentUid, groupId)
            LeaderboardPeriod.CUSTOM -> {
                if (customRange != null) {
                    aggregateReports(Date(customRange.first), Date(customRange.second), metric, currentUid, groupId)
                } else emptyList()
            }
        }
    }

    private suspend fun aggregateReports(
        startDate: Date?,
        endDate: Date?,
        metric: LeaderboardMetric,
        currentUid: String,
        groupId: String?
    ): List<LeaderboardEntry> {
        var query = db.collection("workout_reports").limit(500) as Query
        
        startDate?.let { query = query.whereGreaterThanOrEqualTo("timestamp", it) }
        endDate?.let { query = query.whereLessThanOrEqualTo("timestamp", it) }

        val snapshot = query.get().await()
        val reports = snapshot.documents.mapNotNull { it.toObject(FirestoreWorkoutReport::class.java) }
        
        // Grup filtresi varsa üyeleri al
        val groupMemberIds = if (groupId != null) {
             db.collection("group_members")
                .whereEqualTo("groupId", groupId)
                .get().await()
                .documents.mapNotNull { it.getString("userId") }
        } else null

        // Grupla ve topla
        val userTotals = reports
            .filter { report -> groupMemberIds == null || report.userId in groupMemberIds }
            .groupBy { it.userId }
            .mapValues { entry ->
                when (metric) {
                    LeaderboardMetric.CALORIES -> entry.value.sumOf { it.caloriesBurned.toDouble() }.toFloat()
                    LeaderboardMetric.XP -> entry.value.sumOf { it.score.toDouble() / 10 }.toFloat() // Örnek puanlama
                    else -> entry.value.sumOf { it.caloriesBurned.toDouble() }.toFloat()
                }
            }
            .toList()
            .sortedByDescending { it.second }
            .take(50)

        // Kullanıcı isimlerini çek (Performance için cache veya batch fetch lazım)
        // Şimdilik basitlik için her kullanıcıyı ayrı çekiyoruz (Batch fetch daha iyi olur)
        return userTotals.mapIndexed { index, pair ->
            val userProfile = firestoreService.getUserProfile(pair.first)
            LeaderboardEntry(
                userId = pair.first,
                fullName = userProfile?.fullName ?: "Bilinmiyor",
                value = pair.second,
                rank = index + 1,
                isMe = pair.first == currentUid
            )
        }
    }

    private fun getStartOfDay(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun getStartOfLast7Days(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return cal.time
    }

    private fun getStartOfLast30Days(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        return cal.time
    }

    /**
     * Kullanıcının dashboard istatistiklerini canlı olarak Firestore'dan toplar.
     */
    override suspend fun getPatientStats(uid: String): WorkoutStats {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val last30Days = calendar.time
        
        // 1. Raporları çek (Son 30 gün)
        val reportsSnapshot = db.collection("workout_reports")
            .whereEqualTo("userId", uid)
            .whereGreaterThanOrEqualTo("timestamp", last30Days)
            .get().await()
        
        val reports = reportsSnapshot.documents.mapNotNull { it.toObject(FirestoreWorkoutReport::class.java) }
            .sortedBy { it.timestamp }

        // 2. Görevleri çek
        val tasksSnapshot = db.collection("task_assignments")
            .whereEqualTo("patientId", uid)
            .get().await()
        
        val tasks = tasksSnapshot.documents.mapNotNull { it.toObject(FirestoreTaskAssignment::class.java) }

        // 3. Günlük Kalori Agregasyonu (Son 7 gün, boş günleri 0 ile doldur)
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyMap = mutableMapOf<String, Float>()
        
        // Son 7 günü 0 ile ilklendir
        for (i in 6 downTo 0) {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }.time
            dailyMap[sdf.format(date)] = 0f
        }
        
        // Raporları ekle
        reports.forEach { report ->
            report.timestamp?.let { ts ->
                val dayKey = sdf.format(ts)
                if (dailyMap.containsKey(dayKey)) {
                    dailyMap[dayKey] = (dailyMap[dayKey] ?: 0f) + report.caloriesBurned
                }
            }
        }
        
        val dailyCalories = dailyMap.toList()

        // 4. Skor Trendi (Son 20 egzersiz)
        val scoreTrend = reports.takeLast(20).mapIndexed { index, report ->
            index.toFloat() to report.score.toFloat()
        }

        // 5. Görev Tamamlama Durumu
        val completionStats = tasks.groupBy { it.status }.mapValues { it.value.size }

        return WorkoutStats(
            dailyCalories = dailyCalories,
            scoreTrend = scoreTrend,
            completionStats = completionStats
        )
    }

    override suspend fun getRecentActivities(limit: Long): List<FirestoreActivity> {
        return firestoreService.getRecentActivities(limit)
    }

    override suspend fun getGlobalLeaderboard(limit: Long): List<FirestoreUser> {
        return firestoreService.getGlobalLeaderboard(limit)
    }

    override suspend fun getUserBadges(userId: String): List<FirestoreUserBadgeProgress> {
        return firestoreService.getUserBadges(userId)
    }
}
