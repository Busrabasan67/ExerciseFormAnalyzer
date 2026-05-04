package com.example.exerciseformanalyzer.domain

import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.model.firestore.FirestoreExerciseItem

data class RecommendedPlan(
    val title: String,
    val note: String,
    val exercises: List<FirestoreExerciseItem>,
    val hasInjuryWarning: Boolean = false
)

object RecommendationHelper {
    fun generatePlan(user: UserEntity): RecommendedPlan {
        val exercises = mutableListOf<FirestoreExerciseItem>()
        var injuryWarning = false

        val goal = user.goal ?: "general_health"
        val hasHernia = user.hasHernia
        val hasMeniscus = user.hasMeniscus
        val painAreas = try { 
            org.json.JSONArray(user.painAreasJson ?: "[]").let { arr -> List(arr.length()) { i -> arr.getString(i) } }
        } catch(e: Exception) { emptyList<String>() }

        val exerciseLevel = user.exerciseLevel ?: "beginner"
        val activityLevel = user.activityLevel ?: "medium"

        // Determine Sets, Reps, Rest based on Goal
        val (targetSets, targetReps, targetSeconds, restTime) = when (goal) {
            "lose_weight" -> Quadruple(3, 15, 45, 45)
            "gain_muscle" -> Quadruple(4, 10, 60, 90)
            "rehab" -> Quadruple(2, 12, 30, 60)
            else -> Quadruple(3, 12, 60, 60)
        }

        // 1. Lower Body / Core
        if (hasHernia) {
            injuryWarning = true
            // Hernia logic: No heavy load. Core focus.
            exercises.add(createExercise(ExerciseType.SQUAT, 2, 10, 0, restTime + 30, "EASY")) // Light squat or skip
        } else if (hasMeniscus || painAreas.any { it.contains("diz", ignoreCase = true) }) {
            injuryWarning = true
            // Meniscus: skip jumping, skip deep squat
            // exercises.add(createExercise(ExerciseType.SQUAT, targetSets, targetReps, 0, restTime, "EASY")) 
            // In reality we might suggest 'Wall Sit' but let's stick to existing types
        } else {
            exercises.add(createExercise(ExerciseType.SQUAT, targetSets, targetReps, 0, restTime, "MEDIUM"))
        }

        // 2. Upper Body
        exercises.add(createExercise(ExerciseType.PUSH_UP, targetSets, targetReps, 0, restTime, if (exerciseLevel == "beginner") "EASY" else "MEDIUM"))
        
        // Add new exercises based on level
        if (exerciseLevel != "beginner") {
            exercises.add(createExercise(ExerciseType.SHOULDER_PRESS, targetSets, targetReps, 0, restTime, "MEDIUM"))
            exercises.add(createExercise(ExerciseType.HAMMER_CURL, targetSets, targetReps, 0, restTime, "MEDIUM"))
            exercises.add(createExercise(ExerciseType.LATERAL_RAISE, targetSets, targetReps, 0, restTime, "MEDIUM"))
        } else {
            exercises.add(createExercise(ExerciseType.BICEPS_CURL, targetSets, targetReps, 0, restTime, "EASY"))
        }

        // 3. Back / Triceps
        if (exerciseLevel == "advanced") {
            exercises.add(createExercise(ExerciseType.BENT_OVER_ROW, targetSets, targetReps, 0, restTime, "HARD"))
            exercises.add(createExercise(ExerciseType.TRICEPS_EXTENSION, targetSets, targetReps, 0, restTime, "MEDIUM"))
        }

        // 4. Core
        exercises.add(createExercise(ExerciseType.SIT_UP, targetSets, targetReps, 0, restTime, "MEDIUM"))
        exercises.add(createExercise(ExerciseType.PLANK, 3, 0, targetSeconds, restTime, "MEDIUM"))

        // Adjust based on exerciseLevel
        if (exerciseLevel == "beginner") {
            return RecommendedPlan(
                title = "Başlangıç Seviye Program",
                note = "Sağlık durumunuza göre optimize edilmiş temel egzersiz programı.",
                exercises = exercises.map { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) },
                hasInjuryWarning = injuryWarning
            )
        }

        return RecommendedPlan(
            title = "Size Özel Antrenman",
            note = "Profil verilerinize ve hedeflerinize göre oluşturulmuş kişisel program.",
            exercises = exercises,
            hasInjuryWarning = injuryWarning
        )
    }

    private fun createExercise(
        type: ExerciseType, 
        sets: Int, 
        reps: Int, 
        seconds: Int, 
        rest: Int,
        difficulty: String
    ): FirestoreExerciseItem {
        return FirestoreExerciseItem(
            exerciseType = type.name,
            targetType = if (seconds > 0) "DURATION" else "REPS",
            targetReps = if (reps > 0) reps else null,
            targetDurationSeconds = if (seconds > 0) seconds else null,
            sets = sets,
            restTimeSeconds = rest,
            difficulty = difficulty,
            status = "PENDING"
        )
    }

    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
