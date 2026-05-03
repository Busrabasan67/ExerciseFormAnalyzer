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
    override suspend fun getPatientStats(uid: String, startDate: Date?, endDate: Date?): WorkoutStats {
        val cal = Calendar.getInstance()
        
        val finalEndDate = (endDate ?: Date()).let {
            cal.time = it
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            cal.time
        }
        
        val finalStartDate = (startDate ?: Calendar.getInstance().apply {
            time = finalEndDate
            add(Calendar.DAY_OF_YEAR, -30)
        }.time).let {
            cal.time = it
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.time
        }
        
        // 1. Raporları çek (İndeks hatasını önlemek için sadece userId ile çekip bellekte filtreliyoruz)
        val reportsSnapshot = db.collection("workout_reports")
            .whereEqualTo("userId", uid)
            .get().await()
        
        val allReports = reportsSnapshot.documents.mapNotNull { it.toObject(FirestoreWorkoutReport::class.java) }
        
        val reports = allReports.filter { report ->
            val ts = report.timestamp ?: return@filter false
            (ts.after(finalStartDate) || isSameDay(ts, finalStartDate)) && 
            (ts.before(finalEndDate) || isSameDay(ts, finalEndDate))
        }.sortedBy { it.timestamp }

        // 2. Görevleri çek (Tüm zamanlar veya bu aralıkta bitenler?)
        // Şimdilik genel durum için hepsini çekiyoruz, ama isterseniz bu da tarihe göre filtrelenebilir.
        val tasksSnapshot = db.collection("task_assignments")
            .whereEqualTo("patientId", uid)
            .get().await()
        
        val tasks = tasksSnapshot.documents.mapNotNull { it.toObject(FirestoreTaskAssignment::class.java) }

        // 3. Günlük Kalori Agregasyonu
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyMap = LinkedHashMap<String, Float>()
        
        // Aralıktaki her günü 0 ile ilklendir
        cal.time = finalStartDate
        while (cal.time.before(finalEndDate) || isSameDay(cal.time, finalEndDate)) {
            dailyMap[sdf.format(cal.time)] = 0f
            cal.add(Calendar.DAY_OF_YEAR, 1)
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

        // 4. Skor Trendi (Seçili aralıktaki son 50 egzersiz)
        val scoreTrend = reports.takeLast(50).mapIndexed { index, report ->
            index.toFloat() to report.score.toFloat()
        }

        // 5. Görev Tamamlama Durumu (Normalize edilmiş anahtarlar)
        val completionStats = mutableMapOf(
            "PENDING" to 0,
            "IN_PROGRESS" to 0,
            "COMPLETED" to 0
        )
        
        tasks.forEach { task ->
            val s = task.status.orEmpty().lowercase()
            when {
                s in listOf("completed", "done", "finished") -> {
                    completionStats["COMPLETED"] = (completionStats["COMPLETED"] ?: 0) + 1
                }
                s in listOf("in_progress", "started") -> {
                    completionStats["IN_PROGRESS"] = (completionStats["IN_PROGRESS"] ?: 0) + 1
                }
                s in listOf("pending", "active", "assigned", "waiting") -> {
                    completionStats["PENDING"] = (completionStats["PENDING"] ?: 0) + 1
                }
            }
        }

        // 6. Recent Reports (En son 10 rapor, yeniden eskiye)
        val recentReports = reports.takeLast(10).reversed()

        // 7. Exercise Analysis (Egzersiz Bazlı Form Analizi)
        val exerciseAnalysis = reports.groupBy { it.exerciseName }
            .map { (name, exReports) ->
                val totalReps = exReports.sumOf { it.reps }
                val avgScore = if (exReports.isNotEmpty()) exReports.map { it.score }.average().toFloat() else 0f
                val mistakes = exReports.mapNotNull { it.feedback }.filter { it.isNotBlank() && it.length > 5 }.distinct().take(3)
                com.example.exerciseformanalyzer.model.ExerciseAnalysis(
                    exerciseName = name.ifEmpty { "Bilinmeyen" },
                    totalReps = totalReps,
                    avgScore = avgScore,
                    commonMistakes = mistakes
                )
            }.sortedByDescending { it.totalReps }

        // 8. Progress Delta (Gelişim Kıyaslaması)
        val progressDelta = if (reports.size >= 4) {
            val half = reports.size / 2
            val firstHalfAvg = reports.take(half).map { it.score }.average().toFloat()
            val secondHalfAvg = reports.drop(half).map { it.score }.average().toFloat()
            if (firstHalfAvg > 0) ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100f else null
        } else null

        // 9. Risk Warnings (Risk Analizi ve Uyarı Sistemi)
        val riskWarnings = mutableListOf<String>()
        exerciseAnalysis.filter { it.avgScore < 50f && it.totalReps > 15 }.forEach {
            riskWarnings.add("${it.exerciseName} formunda kronik hata tespit edildi (Ort. Skor: %${it.avgScore.toInt()}). Sakatlık riski yüksek.")
        }
        if (reports.isNotEmpty() && reports.takeLast(3).size == 3 && reports.takeLast(3).all { it.score < 40 }) {
            riskWarnings.add("Son 3 antrenmanda genel form çok düşük. Fiziksel yorgunluk veya ağrı olabilir.")
        }

        return WorkoutStats(
            dailyCalories = dailyCalories,
            scoreTrend = scoreTrend,
            completionStats = completionStats,
            recentReports = recentReports,
            exerciseAnalysis = exerciseAnalysis,
            progressDelta = progressDelta,
            riskWarnings = riskWarnings
        )
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
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
