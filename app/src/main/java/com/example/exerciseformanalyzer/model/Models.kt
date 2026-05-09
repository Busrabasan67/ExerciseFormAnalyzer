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
    BURPEE("Burpee"),
    TRICEPS_KICKBACK("Triceps Kickback"),
    BENT_OVER_RAISE("Bent-over Raise"),
    
    // Yeni Eklenenler (Karın ve Stabilite)
    CROSSBODY_MOUNTAIN_CLIMBER("Crossbody Mountain Climber"),
    HEEL_TAP("Heel Tap"),
    BICYCLE_CRUNCH("Bicycle Crunch"),
    REVERSE_CRUNCH("Reverse Crunch"),
    STRAIGHT_LEG_CRUNCH("Straight Leg Crunch");

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
            HAMMER_CURL -> ExerciseMetadata(
                description = "Biceps ve ön kol kaslarını geliştirir.",
                preferredAngle = CameraAngle.FRONT,
                correctFormRules = listOf("Dirsekler vücuda sabit", "Avuç içleri birbirine bakmalı", "Sadece ön kol hareket etmeli"),
                commonMistakes = listOf("Dirseklerin öne gitmesi", "Vücut sallanması (Momentum)", "Bileklerin bükülmesi")
            )
            SHOULDER_PRESS -> ExerciseMetadata(
                description = "Omuz ve arka kol kaslarını hedefler.",
                preferredAngle = CameraAngle.FRONT,
                correctFormRules = listOf("Sırt dik", "Dirsekler 90° civarında", "Kollar tam uzatılmalı"),
                commonMistakes = listOf("Bel çukurunun artması", "Dirseklerin çok dışa açılması", "Yarım tekrar yapmak")
            )
            LATERAL_RAISE -> ExerciseMetadata(
                description = "Omuzun yan başlarını izole eder.",
                preferredAngle = CameraAngle.FRONT,
                correctFormRules = listOf("Dik durun", "Kollar omuz hizasına kadar açılmalı", "Hafif dirsek bükümü korunmalı"),
                commonMistakes = listOf("Ağırlığı çok yukarı kaldırmak", "Gövdeden ivme almak", "Omuz yerine trapez kullanmak")
            )
            TRICEPS_EXTENSION -> ExerciseMetadata(
                description = "Arka kol (triceps) kaslarını geliştirir.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Üst kol sabit", "Sadece dirsek açılmalı", "Tam hareket aralığı"),
                commonMistakes = listOf("Dirseklerin yanlara açılması", "Boynun öne gitmesi", "Kolu tam uzatmamak")
            )
            TRICEPS_KICKBACK -> ExerciseMetadata(
                description = "Arka kolu izole eden bir egzersiz.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Gövde öne eğik ve sabit", "Üst kol yere paralel", "Kol tam arkaya açılmalı"),
                commonMistakes = listOf("Kolun sallanması", "Gövdenin dikleşmesi", "Eksik açılma")
            )
            BENT_OVER_ROW -> ExerciseMetadata(
                description = "Sırt kaslarını kalınlaştıran temel çekiş.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Dizler hafif bükülü", "Sırt düz ve yere yakın", "Dirsekler geriye çekilmeli"),
                commonMistakes = listOf("Sırtın yuvarlanması", "Ağırlığı göğse çekmek", "Boynun aşağı düşmesi")
            )
            BENT_OVER_RAISE -> ExerciseMetadata(
                description = "Arka omuz ve üst sırtı hedefler.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Gövde öne eğik", "Kollar yana genişçe açılmalı", "Kürek kemikleri sıkıştırılmalı"),
                commonMistakes = listOf("Momentum kullanmak", "Dirsekleri aşırı bükmek", "Sırt pozisyonunu bozmak")
            )
            CROSSBODY_MOUNTAIN_CLIMBER -> ExerciseMetadata(
                description = "Gövde stabilitesini ve oblikleri (yan karın) hedefleyen dinamik hareket.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Eller omuz hizasında", "Vücut dümdüz bir tahta gibi", "Dizler çapraz dirseğe çekilmeli"),
                commonMistakes = listOf("Kalçayı yukarı kaldırmak", "Belin aşağı çökmesi", "Hızlı ve kontrolsüz hareket")
            )
            RUSSIAN_TWIST -> ExerciseMetadata(
                description = "Gövde rotasyonu ile oblikleri çalıştıran etkili core hareketi.",
                preferredAngle = CameraAngle.FRONT,
                correctFormRules = listOf("Sırt dik tutulmalı", "Gövde omuzlarla birlikte dönmeli", "Ayaklar dengeli"),
                commonMistakes = listOf("Sırtın kamburlaşması", "Sadece kolların hareket etmesi", "Boyun zorlanması")
            )
            HEEL_TAP -> ExerciseMetadata(
                description = "Yan karın (oblik) kaslarını izole eden bir hareket.",
                preferredAngle = CameraAngle.FRONT,
                correctFormRules = listOf("Kürek kemikleri hafif havada", "Sadece yanlara esneyerek topuklara dokunulmalı", "Boyun serbest"),
                commonMistakes = listOf("Boynun kasılması", "Yeterince yana esnememek", "Sırtın tamamen yere yapışık olması")
            )
            BICYCLE_CRUNCH -> ExerciseMetadata(
                description = "Karın bölgesinin tamamını ve çapraz kasları çalıştırır.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Omuz çapraz dize yaklaşmalı", "Uzatılan bacak yere yakın ve gergin", "Yavaş ve kontrollü"),
                commonMistakes = listOf("Bacağın yere değmesi", "Dirsekleri çekiştirmek", "Boynu zorlamak")
            )
            REVERSE_CRUNCH -> ExerciseMetadata(
                description = "Alt karın kaslarını hedefleyen etkili bir hareket.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Kalça karın kaslarıyla yerden kalkmalı", "Ayaklar momentumla savrulmamalı", "İnişte bel yerde kalmalı"),
                commonMistakes = listOf("Momentum (savurma) kullanmak", "Beli yerden kaldırmak", "Boyundan destek almak")
            )
            STRAIGHT_LEG_CRUNCH -> ExerciseMetadata(
                description = "Üst karın ve bacak esnekliğini birleştiren hareket.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Bacaklar 90 derece dik", "Sadece üst gövde parmak uçlarına yükselmeli", "Bacaklar sabit"),
                commonMistakes = listOf("Bacakların sallanması", "Boynu çekiştirmek", "Yetersiz yükselme")
            )
            MOUNTAIN_CLIMBER -> ExerciseMetadata(
                description = "Tüm vücudu çalıştıran yüksek yoğunluklu core hareketi.",
                preferredAngle = CameraAngle.SIDE,
                correctFormRules = listOf("Eller omuz altında", "Sırt düz", "Dizler göğse çekilmeli"),
                commonMistakes = listOf("Kalçayı havaya dikmek", "Omuzların öne kayması")
            )
            // Diğerleri için örnek default yapı
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

/** Liderlik tablosu zaman aralığı */
enum class LeaderboardPeriod(val displayName: String) {
    DAILY("Günlük"),
    WEEKLY("Haftalık"),
    MONTHLY("Aylık"),
    ALL_TIME("Tüm Zamanlar"),
    CUSTOM("Özel")
}

/** Liderlik tablosu ölçüm birimi */
enum class LeaderboardMetric(val displayName: String) {
    CALORIES("Kalori"),
    XP("Puan (XP)"),
    LEVEL("Seviye")
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

data class ExerciseAnalysis(
    val exerciseName: String,
    val totalReps: Int,
    val avgScore: Float,
    val commonMistakes: List<String>
)

/** Kapsamlı İstatistik Modeli (Dashboard ve Analiz için) */
data class WorkoutStats(
    val dailyCalories: List<Pair<String, Float>> = emptyList(),
    val scoreTrend: List<Pair<Float, Float>> = emptyList(), 
    val completionStats: Map<String, Int> = emptyMap(),
    // Yeni eklenen profesyonel özellikler
    val recentReports: List<com.example.exerciseformanalyzer.model.firestore.FirestoreWorkoutReport> = emptyList(),
    val exerciseAnalysis: List<ExerciseAnalysis> = emptyList(),
    val progressDelta: Float? = null, // Form skorunun önceki döneme göre değişimi (%)
    val riskWarnings: List<String> = emptyList() // Düşük skor / tehlikeli hareket uyarıları
)

data class AdminSystemStats(
    val totalUsers: Int = 0,
    val totalExperts: Int = 0,
    val dailyWorkouts: Int = 0,
    val totalCalories: Float = 0f,
    val activeGroups: Int = 0,
    val roleDistribution: Map<String, Int> = emptyMap(),
    val workoutTrend: List<Pair<String, Int>> = emptyList(),
    val exercisePopularity: List<Pair<String, Int>> = emptyList()
)
