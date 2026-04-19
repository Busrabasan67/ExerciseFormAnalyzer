package com.example.exerciseformanalyzer.model

data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 1.0f,
    val presence: Float = 1.0f
)

data class PoseFrame(
    val landmarks: List<Landmark>,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int
) {
    fun landmarkOrNull(index: Int): Landmark? =
        if (index in landmarks.indices) landmarks[index] else null

    fun isVisible(index: Int, minVisibility: Float = 0.5f): Boolean =
        landmarkOrNull(index)?.visibility?.let { it >= minVisibility } ?: false
}

data class JointAngles(
    val leftKneeAngle: Float? = null,
    val rightKneeAngle: Float? = null,
    val leftHipAngle: Float? = null,
    val rightHipAngle: Float? = null,
    val leftElbowAngle: Float? = null,
    val rightElbowAngle: Float? = null,
    val leftShoulderAngle: Float? = null,
    val rightShoulderAngle: Float? = null,
    val torsoInclination: Float? = null,
    val shoulderLevelDiff: Float? = null,
    val hipLevelDiff: Float? = null
)

/** Kullanıcının kameraya göre duruş açısı */
enum class CameraAngle(val displayName: String) {
    FRONT("Önden"),
    SIDE("Yandan"),
    ANGLED("Hafif Çapraz")
}

/** Taranacak vücut profili (yan açıdan bakıldığında sadece bir taraf net görünür) */
enum class BodyProfile {
    FRONTAL, LEFT_SIDE_VISIBLE, RIGHT_SIDE_VISIBLE
}

/** Egzersiz Meta Verisi (Modal ve Yönlendirmeler İçin) */
data class ExerciseMetadata(
    val description: String,
    val preferredAngle: CameraAngle,
    val correctFormRules: List<String>,
    val commonMistakes: List<String>
)

enum class ExerciseType(val displayName: String) {
    UNKNOWN("Algılanıyor..."),
    
    // Alt Vücut
    SQUAT("Squat"),
    HALF_SQUAT("Half Squat"),
    JUMP_SQUAT("Jump Squat"),
    LUNGE("Lunge"),
    REVERSE_LUNGE("Reverse Lunge"),
    BULGARIAN_SPLIT_SQUAT("Bulgarian Split Squat"),
    CALF_RAISE("Calf Raise"),
    GLUTE_BRIDGE("Glute Bridge"),

    // Üst Vücut / Kol
    BICEPS_CURL("Biceps Curl"),
    HAMMER_CURL("Hammer Curl"),
    SHOULDER_PRESS("Shoulder Press"),
    LATERAL_RAISE("Lateral Raise"),
    FRONT_RAISE("Front Raise"),
    BENT_OVER_ROW("Bent-over Row"),
    DUMBBELL_ROW("Dumbbell Row"), // One-arm row ile birleşik
    TRICEPS_EXTENSION("Triceps Extension"),
    UPRIGHT_ROW("Upright Row"),

    // Vücut Ağırlığı / Core
    PUSH_UP("Push-up"),
    KNEE_PUSH_UP("Knee Push-up"),
    PLANK("Plank"),
    MOUNTAIN_CLIMBER("Mountain Climber"),
    SIT_UP("Sit-up"),
    CRUNCH("Crunch"),
    RUSSIAN_TWIST("Russian Twist"),
    BURPEE("Burpee");

    fun getMetadata(): ExerciseMetadata {
        return when (this) {
            SQUAT -> ExerciseMetadata(
                description = "Alt vücut kaslarını geliştiren temel egzersiz.",
                preferredAngle = CameraAngle.ANGLED,
                correctFormRules = listOf("Ayaklar omuz genişliğinde", "Göğüs dik, bel düz", "Dizler 90° bükülmeli"),
                commonMistakes = listOf("Dizleri içeri kapatmak (Valgus)", "Aşırı öne eğilmek", "Yeterince derine inmemek")
            )
            PUSH_UP -> ExerciseMetadata(
                description = "Göğüs, omuz ve arka kol kaslarını çalıştırır.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Eller göğüs hizasında", "Vücut dümdüz bir tahta gibi olmalı", "Dirsekler vücuda 45° açıda bükülmeli"),
                commonMistakes = listOf("Kalçayı düşürmek", "Kalçayı çok kaldırmak", "Yarım inmek")
            )
            SIT_UP -> ExerciseMetadata(
                description = "Karın kaslarını (core) hedefler.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Ayak tabanları yerde", "Kontrollü kalkış ve iniş", "Boynu zorlamadan göğsü dizlere yaklaştırma"),
                commonMistakes = listOf("Boynu ellerle çekiştirmek", "Hızla yere düşerek inmek", "Beli yerden fazla koparmak")
            )
            DUMBBELL_ROW -> ExerciseMetadata(
                description = "Sırt ve kanat kaslarını izole eder.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Gövde yataya yakın 45° eğik", "Sırt düz", "Dirsek vücuda teğet geçmeli"),
                commonMistakes = listOf("Kambur durmak", "Ağırlığı çekerken gövdeyi aşırı döndürmek", "Omuzu kulağa çekmek (Shrug)")
            )
            BICEPS_CURL -> ExerciseMetadata(
                description = "Pazu kaslarını geliştirir.",
                preferredAngle = CameraAngle.FRONT,
                correctFormRules = listOf("Dik durun", "Üst kollar vücuda yapışık ve sabit", "Kontrollü çekiş ve iniş"),
                commonMistakes = listOf("Belden ivme/momentum almak", "Dirsekleri öne doğru savurmak", "Hızlıca aşağı bırakmak")
            )
            LUNGE -> ExerciseMetadata(
                description = "Bacak ve kalça kaslarını izole eder.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Dik durun", "Öndeki diz ayak ucunu geçmemeli", "Arkadaki diz yere yaklaşmalı"),
                commonMistakes = listOf("Öne doğru aşırı eğilmek", "Dengesiz adım atmak")
            )
            PLANK -> ExerciseMetadata(
                description = "Tüm core bölgesini statik çalıştırır.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Dirsekler omuz hizasında", "Vücut düz bir çizgi halinde", "Karın sıkı"),
                commonMistakes = listOf("Kalçayı düşürmek", "Kalçayı yukarı dikmek", "Boynu aşağı sarkıtmak")
            )
            // Diğerleri için örnek default yapı (Kısaltmak için default verildi, her hareket özel ayarlarına göre genişletilebilir)
            else -> ExerciseMetadata(
                description = "$displayName egzersizi.",
                preferredAngle = CameraAngle.ANGLED,
                correctFormRules = listOf("Hareket formuna dikkat edin", "Kas kontrolünü sağlayın"),
                commonMistakes = listOf("Kontrolü kaybetmek", "Formu bozacak kadar hızlı yapmak")
            )
        }
    }
}

enum class RepetitionPhase {
    IDLE, TOP, GOING_DOWN, BOTTOM, GOING_UP, RAISED
}

data class RepetitionState(
    val count: Int = 0,
    val phase: RepetitionPhase = RepetitionPhase.IDLE,
    val lastPhaseChangeMs: Long = 0L
)

data class FormFeedback(
    val isCorrect: Boolean,
    val score: Int,
    val primaryError: String?,
    val secondaryErrors: List<String> = emptyList(),
    val feedbackMessage: String,
    val confidence: Float
)

data class AnalysisResult(
    val exerciseType: ExerciseType,
    val formFeedback: FormFeedback,
    val repetitionState: RepetitionState,
    val jointAngles: JointAngles,
    val poseConfidence: Float,
    val isPersonVisible: Boolean,
    val isInFrame: Boolean,
    val trackingQuality: TrackingQuality,
    val activeProfile: BodyProfile = BodyProfile.FRONTAL
)

enum class TrackingQuality {
    GOOD, FAIR, POOR, LOST
}

/** Egzersiz özeti modeli */
data class WorkoutSummary(
    val exercise: ExerciseType,
    val totalReps: Int,
    val durationSeconds: Long,
    val accuracyPercentage: Int,
    val mostCommonError: String?,
    val caloriesBurned: Float = 0f
)
