package com.example.exerciseformanalyzer.data.repository

// WorkoutRepository — Egzersiz kayıt ve okuma için tek giriş noktası
//
// Offline-first veri akışı:
//   1. Egzersiz biter → CalorieCalculator ile kalori hesapla
//   2. WorkoutReportEntity → Room'a yaz (isSynced = false)
//   3. SyncWorker arka planda → Firestore'a yükle → isSynced = true, firebaseDocId dolar
//
// UI (ViewModel) doğrudan DAO veya Firebase'e erişmez; bu repository üzerinden geçer.

import com.example.exerciseformanalyzer.data.local.dao.ExerciseDao
import com.example.exerciseformanalyzer.data.local.dao.TaskAssignmentDao
import com.example.exerciseformanalyzer.data.local.dao.UserDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutPlanDao
import com.example.exerciseformanalyzer.data.local.dao.WorkoutReportDao
import com.example.exerciseformanalyzer.data.local.dao.BadgeDao
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.domain.CalorieCalculator
import com.example.exerciseformanalyzer.domain.model.TaskContext
import com.example.exerciseformanalyzer.domain.repository.IWorkoutRepository
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.model.firestore.FirestoreWorkoutReport
import kotlinx.coroutines.flow.Flow

import com.example.exerciseformanalyzer.domain.repository.IPlanRepository

class WorkoutRepository(
    private val reportDao: WorkoutReportDao,
    private val taskDao: TaskAssignmentDao,
    private val exerciseDao: ExerciseDao,
    private val userDao: UserDao,
    private val badgeDao: BadgeDao,
    private val firestoreService: FirestoreService,
    private val planRepository: IPlanRepository
) : IWorkoutRepository {

    /**
     * Egzersiz tamamlandığında çağrılır.
     * Kalori hesaplanır → Room'a yazılır → Firestore'a anlık yükleme denenir.
     * İnternet yoksa isSynced = false kalır; SyncWorker sonra tekrar dener.
     *
     * @param userUid     Firebase Auth UID (boş olabilir, offline kullanım için)
     * @param localUserId Room'daki lokal kullanıcı ID'si (geriye uyumluluk)
     * @param taskContext Görev bağlamlı seans için taskId + exerciseIndex. Serbest egzersizde null.
     */
    override suspend fun saveWorkoutResult(
        userUid: String,
        localUserId: Int,
        exerciseType: ExerciseType,
        exerciseId: Int,
        score: Int,
        reps: Int,
        durationSeconds: Long,
        feedback: String?,
        taskContext: TaskContext?
    ) {
        // 1. Kullanıcı ağırlığını Room'dan çek (profil varsa MET daha doğru sonuç verir)
        val weightKg = if (userUid.isNotEmpty()) {
            userDao.getUserByUid(userUid)?.weightKg ?: 70f
        } else {
            70f
        }

        // 2. Kalori hesapla (MET formülü)
        val calories = CalorieCalculator.calculateForSession(
            exerciseType = exerciseType,
            weightKg = weightKg,
            durationSeconds = durationSeconds
        )

        // 2.1 XP Hesapla (Faz 4)
        val user = userDao.getUserByUid(userUid)
        val multiplier = user?.xpMultiplier ?: 1.0f
        val earnedXp = ((reps + (durationSeconds / 10).toInt()) * multiplier).toInt()
        val totalXp = (user?.xp ?: 0) + earnedXp
        
        // Seviye hesapla (Sadeleşmiş: Her 1000 XP bir seviye)
        val newLevel = (totalXp / 1000) + 1
        
        // Seri (Streak) Mantığı
        val today = java.time.LocalDate.now().toString()
        val lastDate = user?.lastExerciseDate
        val newStreak = when {
            lastDate == today -> user.streak
            lastDate == java.time.LocalDate.now().minusDays(1).toString() -> user.streak + 1
            else -> 1
        }
        
        if (userUid.isNotEmpty()) {
            userDao.updateUserGamification(userUid, totalXp, newLevel, newStreak, today)
        }

        // 3. Room'a yaz — ÖNCE lokal, senkronizasyon sonra
        val report = WorkoutReportEntity(
            userId = localUserId,
            userUid = userUid,
            exerciseId = exerciseId,
            exerciseName = exerciseType.displayName,
            score = score,
            reps = reps,
            totalTimeSeconds = durationSeconds.toInt(),
            caloriesBurned = calories,
            feedback = feedback,
            isSynced = false
        )
        reportDao.insertReport(report)

        // 4. Görev durumu güncellemesi — görev bağlamlı seans ise
        // KÖK NEDEN: önceki kod exerciseType.name string karşılaştırması ile tüm
        // pending task'ları tarıyordu — belirsiz ve güvenilmez.
        // YENİ: taskId + exerciseIndex ile kesin olarak doğru item bulunur.
        if (userUid.isNotEmpty() && taskContext != null) {
            try {
                val task = taskDao.getTaskById(taskContext.taskId)
                if (task != null) {
                    val arr = org.json.JSONArray(task.exercisesJson)
                    if (taskContext.exerciseIndex < arr.length()) {
                        val exObj = arr.getJSONObject(taskContext.exerciseIndex)
                        val currentStatus = exObj.optString("status", "PENDING")

                        if (currentStatus != "COMPLETED") {
                            val tType = taskContext.targetType
                            val prevActualReps = exObj.optInt("actualReps", 0)
                            val prevActualDur = exObj.optInt("actualDurationSeconds", 0)
                            val targetSets = exObj.optInt("sets", 1)

                            val newCompletedSets = taskContext.completedSets
                            val isCompleted = newCompletedSets >= targetSets

                            val sessionReps = maxOf(0, reps - taskContext.repsDoneInCurrentSet)
                            val sessionDur = maxOf(0, durationSeconds.toInt() - taskContext.durDoneInCurrentSet)

                            val newActualReps = prevActualReps + sessionReps
                            val newActualDur = prevActualDur + sessionDur

                            exObj.put("actualReps", newActualReps)
                            exObj.put("actualDurationSeconds", newActualDur)
                            exObj.put("completedSets", newCompletedSets)
                            exObj.put("status", if (isCompleted) "COMPLETED" else "IN_PROGRESS")
                            if (isCompleted) {
                                exObj.put("completedAt", System.currentTimeMillis())
                            }
                        }

                        // Tüm item'lar tamamlandı mı?
                        var allDone = true
                        var anyProgress = false
                        for (i in 0 until arr.length()) {
                            val s = arr.getJSONObject(i).optString("status", "PENDING")
                            if (s != "COMPLETED") allDone = false
                            if (s == "IN_PROGRESS" || s == "COMPLETED") anyProgress = true
                        }
                        val newTaskStatus = when {
                            allDone    -> "COMPLETED"
                            anyProgress -> "IN_PROGRESS"
                            else       -> "PENDING"
                        }

                        val updatedTask = task.copy(
                            exercisesJson = arr.toString(),
                            status = newTaskStatus,
                            completedAt = if (allDone) System.currentTimeMillis() else task.completedAt,
                            isSynced = false
                        )
                        taskDao.updateTask(updatedTask)
                        
                        // 4.1 Modern Dashboard (TaskProgressEntity) gncellemesi
                        if (!task.firebaseDocId.isNullOrEmpty()) {
                            val pKey = planRepository.getPeriodKey(taskContext.scheduleType)
                            planRepository.updateExerciseProgress(
                                firebaseTaskId = task.firebaseDocId!!,
                                patientUid = userUid,
                                exerciseType = taskContext.exerciseType,
                                periodKey = pKey,
                                completedSets = taskContext.completedSets, // Doğru değer WorkoutViewModel'dan geliyor
                                totalSets = exObj.optInt("sets", 1),
                                taskExerciseCount = arr.length()
                            )
                        }
                        
                        android.util.Log.d("WorkoutRepository",
                            "Grev gncellendi: id=${task.id} status=$newTaskStatus exerciseIdx=${taskContext.exerciseIndex}")

                        // Firestore'a anında itmeyi dene (başarısız olursa SyncWorker yapar)
                        if (!task.firebaseDocId.isNullOrEmpty()) {
                            try {
                                val exercisesList = mutableListOf<com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem>()
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    exercisesList.add(
                                        com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem(
                                            exerciseType = obj.optString("exerciseType"),
                                            targetType = obj.optString("targetType"),
                                            targetReps = if (obj.has("targetReps")) obj.getInt("targetReps") else null,
                                            targetDurationSeconds = if (obj.has("targetDurationSeconds")) obj.getInt("targetDurationSeconds") else null,
                                            actualReps = if (obj.has("actualReps")) obj.getInt("actualReps") else null,
                                            actualDurationSeconds = if (obj.has("actualDurationSeconds")) obj.getInt("actualDurationSeconds") else null,
                                            sets = obj.optInt("sets", 1),
                                            completedSets = obj.optInt("completedSets", 0),
                                            restTimeSeconds = if (obj.has("restTimeSeconds") && !obj.isNull("restTimeSeconds")) obj.getInt("restTimeSeconds") else null,
                                            difficulty = obj.optString("difficulty", "MEDIUM"),
                                            category = obj.optString("category", "STRENGTH"),
                                            status = obj.optString("status")
                                        )
                                    )
                                }
                                firestoreService.updateTaskStatus(
                                    taskDocId = task.firebaseDocId,
                                    status = newTaskStatus,
                                    completedAt = if (allDone) System.currentTimeMillis() else null,
                                    exercises = exercisesList
                                )
                                // Başarılıysa synced olarak işaretle
                                taskDao.updateTask(updatedTask.copy(isSynced = true))
                            } catch (e: Exception) {
                                android.util.Log.w("WorkoutRepository",
                                    "Görev Firestore sync hatası, SyncWorker deneyecek: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutRepository", "Görev update hatası: ${e.message}")
            }
        }

        // 5. Raporu Firestore'a anlık yükle
        if (userUid.isNotEmpty()) {
            try {
                val firestoreReport = FirestoreWorkoutReport(
                    userId = userUid,
                    exerciseId = exerciseId.toString(),
                    exerciseName = exerciseType.displayName,
                    score = score,
                    reps = reps,
                    durationSeconds = durationSeconds.toInt(),
                    caloriesBurned = calories,
                    feedback = feedback ?: ""
                )
                firestoreService.uploadWorkoutReport(firestoreReport)

                // 6. Sosyal Akışa Aktivite Ekle (Faz 4)
                val activity = com.example.exerciseformanalyzer.model.firestore.FirestoreActivity(
                    userId = userUid,
                    userName = user?.fullName ?: "Kullanıcı",
                    activityType = "WORKOUT",
                    description = "${exerciseType.displayName} antrenmanını tamamladı!",
                    statistics = mapOf(
                        "calories" to "${calories.toInt()} kcal",
                        "duration" to "${durationSeconds / 60}:${durationSeconds % 60}",
                        "reps" to "$reps"
                    )
                )
                firestoreService.createActivity(activity)

                // 7. Rozet İlerlemesini Güncelle
                // Gerçek uygulamada BadgeManager.evaluate(userId, exerciseType, reps) çağrılır.
                // Prototip: Antrenman sayacı rozeti için basit bir stub ilerleme kaydedelim
                if (userUid.isNotEmpty()) {
                    val progress = badgeDao.getProgress(userUid, "first_workout")
                    if (progress == null) {
                        badgeDao.updateProgress(
                            com.example.exerciseformanalyzer.data.local.entity.UserBadgeProgressEntity(
                                userId = userUid,
                                badgeId = "first_workout",
                                currentProgress = 1,
                                targetValue = 1,
                                isUnlocked = true,
                                unlockedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("WorkoutRepository", "Anlık sync başarısız, SyncWorker bekliyor: ${e.message}")
            }
        }
    }

    /**
     * Hastanın geçmiş raporlarını gerçek zamanlı izle (Flow — UI otomatik güncellenir).
     */
    override fun observePatientHistory(userUid: String): Flow<List<WorkoutReportEntity>> {
        return reportDao.observeReportsByUid(userUid)
    }

    /**
     * Lokal ID bazlı geçmiş (geriye uyumluluk).
     */
    override suspend fun getPatientHistory(userId: Int): List<WorkoutReportEntity> {
        return reportDao.getReportsByUser(userId)
    }

    /**
     * AI motoru açılırken egzersiz kurallarını getirmek için kullanılır.
     */
    override suspend fun getExerciseRules(exerciseId: Int) = exerciseDao.getExerciseById(exerciseId)
}