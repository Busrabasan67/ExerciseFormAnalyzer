package com.example.exerciseformanalyzer.analysis

import com.example.exerciseformanalyzer.analysis.evaluator.*
import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils

/**
 * Ana analiz pipeline'ı — tüm katmanları birleştiren orkestratör.
 *
 * Akış:
 *   PoseFrame → Takip kalitesi → Açı hesapla → Egzersiz sınıflandır →
 *   Aktif evaluator seç → Form değerlendir → AnalysisResult çıkar
 *
 * Thread-safety: Bu sınıf tek bir iş parçacığından çağrılmalıdır (analiz executor).
 * ViewModel, sonuçları StateFlow üzerinden UI thread'e aktarır.
 */
class AnalysisPipeline {

    private val classifier = ExerciseClassifier()

    // Her egzersiz için ayrı evaluator — kendi durumlarını korurlar
    private val evaluators: Map<ExerciseType, ExerciseEvaluator> = mapOf(
        ExerciseType.SQUAT to SquatEvaluator(),
        ExerciseType.PUSH_UP to PushUpEvaluator(),
        ExerciseType.SIT_UP to SitUpEvaluator(),
        ExerciseType.DUMBBELL_ROW to DumbbellRowEvaluator(),
        ExerciseType.BICEPS_CURL to BicepsCurlEvaluator(),
        ExerciseType.PLANK to PlankEvaluator(),
        ExerciseType.SHOULDER_PRESS to ShoulderPressEvaluator(),
        ExerciseType.LATERAL_RAISE to LateralRaiseEvaluator(),
        ExerciseType.HAMMER_CURL to HammerCurlEvaluator(),
        ExerciseType.TRICEPS_EXTENSION to TricepsExtensionEvaluator(),
        ExerciseType.TRICEPS_KICKBACK to TricepsKickbackEvaluator(),
        ExerciseType.BENT_OVER_ROW to BentOverRowEvaluator(),
        ExerciseType.BENT_OVER_RAISE to BentOverRaiseEvaluator(),
        ExerciseType.CROSSBODY_MOUNTAIN_CLIMBER to MountainClimberEvaluator(),
        ExerciseType.MOUNTAIN_CLIMBER to MountainClimberEvaluator(),
        ExerciseType.RUSSIAN_TWIST to RussianTwistEvaluator(),
        ExerciseType.HEEL_TAP to HeelTapEvaluator(),
        ExerciseType.BICYCLE_CRUNCH to BicycleCrunchEvaluator(),
        ExerciseType.REVERSE_CRUNCH to ReverseCrunchEvaluator(),
        ExerciseType.STRAIGHT_LEG_CRUNCH to StraightLegCrunchEvaluator()
    )

    private var currentExerciseType: ExerciseType = ExerciseType.UNKNOWN
    
    // Son analiz sonucunu public (read-only) yapıyoruz
    var lastAnalysisResult: AnalysisResult? = null
        private set
    
    // Kullanıcının manuel seçtiği egzersiz. Null ise otomatik algılama yapar.
    var targetExercise: ExerciseType? = null

    /**
     * Ana pipeline çağrısı — her kare için çağrılır.
     * @return AnalysisResult — UI'ın ihtiyaç duyduğu tüm bilgiler
     */
    fun process(frame: PoseFrame): AnalysisResult {
        // 1. Kişi görünür mü?
        val isPersonVisible = frame.landmarks.isNotEmpty()

        // 2. Genel takip kalitesini değerlendir
        val trackingQuality = if (isPersonVisible) {
            AngleUtils.evaluateTrackingQuality(frame, PoseLandmarkIndex.SQUAT_CRITICAL)
        } else {
            TrackingQuality.LOST
        }

        // 3. Kişi görünmüyorsa erken dön
        if (!isPersonVisible || trackingQuality == TrackingQuality.LOST) {
            return buildLostResult(frame)
        }

        // 4. Eklem açılarını hesapla
        val angles = AngleUtils.computeJointAngles(frame)

        // 5. Tüm kritik landmarklar karede mi?
        val isInFrame = checkIsInFrame(frame)

        // 6. Egzersizi sınıflandır (Eğer targetExercise belirlendiyse otomatik algılamayı ez!)
        val detectedType = targetExercise ?: classifier.classify(frame, angles)

        // 7. Egzersiz değişti mi?
        //    targetExercise belirlenmişse UNKNOWN'a asla düşme — evaluator'ı kaybetme!
        val resolvedType = if (detectedType == ExerciseType.UNKNOWN) {
            targetExercise ?: currentExerciseType
        } else {
            detectedType
        }
        if (resolvedType != currentExerciseType) {
            currentExerciseType = resolvedType
        }

        // 8. Uygun egzersiz-spesifik kritik landmarkları seç
        val criticalIndices = getCriticalIndices(currentExerciseType)
        val exerciseTrackingQuality = AngleUtils.evaluateTrackingQuality(frame, criticalIndices)

        // 9. Form değerlendirmesi
        val evaluator = evaluators[currentExerciseType]
        val formFeedback = evaluator?.evaluate(frame, angles, exerciseTrackingQuality)
            ?: buildUnknownFeedback()

        // 10. Genel pose güveni
        val poseConfidence = AngleUtils.averageVisibility(frame, criticalIndices)

        val result = AnalysisResult(
            exerciseType = currentExerciseType,
            formFeedback = formFeedback,
            repetitionState = evaluator?.getRepetitionState() ?: RepetitionState(),
            jointAngles = angles,
            poseConfidence = poseConfidence,
            isPersonVisible = true,
            isInFrame = isInFrame,
            trackingQuality = exerciseTrackingQuality
        )

        lastAnalysisResult = result
        return result
    }

    private fun buildLostResult(frame: PoseFrame) = AnalysisResult(
        exerciseType = currentExerciseType,
        formFeedback = FormFeedback(
            isCorrect = false,
            score = 0,
            primaryError = null,
            feedbackMessage = "Kadraja girin",
            confidence = 0f
        ),
        repetitionState = evaluators[currentExerciseType]?.getRepetitionState() ?: RepetitionState(),
        jointAngles = JointAngles(),
        poseConfidence = 0f,
        isPersonVisible = false,
        isInFrame = false,
        trackingQuality = TrackingQuality.LOST
    )

    private fun buildUnknownFeedback() = FormFeedback(
        isCorrect = false,
        score = 0,
        primaryError = null,
        feedbackMessage = "Egzersiz algılanıyor...",
        confidence = 0f
    )

    /**
     * Temel vücut landmarklarının tamamının karede görünür olup olmadığını kontrol eder.
     */
    private fun checkIsInFrame(frame: PoseFrame): Boolean {
        val keyIndices = setOf(
            PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.LEFT_HIP, PoseLandmarkIndex.RIGHT_HIP
        )
        return keyIndices.all { frame.isVisible(it, AnalysisConstants.MIN_LANDMARK_VISIBILITY) }
    }

    private fun getCriticalIndices(type: ExerciseType): Set<Int> = when (type) {
        ExerciseType.SQUAT,
        ExerciseType.HALF_SQUAT,
        ExerciseType.JUMP_SQUAT,
        ExerciseType.LUNGE,
        ExerciseType.REVERSE_LUNGE,
        ExerciseType.BULGARIAN_SPLIT_SQUAT,
        ExerciseType.CALF_RAISE,
        ExerciseType.GLUTE_BRIDGE -> PoseLandmarkIndex.SQUAT_CRITICAL

        ExerciseType.PUSH_UP,
        ExerciseType.KNEE_PUSH_UP,
        ExerciseType.PLANK,
        ExerciseType.BURPEE -> PoseLandmarkIndex.PUSH_UP_CRITICAL

        ExerciseType.MOUNTAIN_CLIMBER,
        ExerciseType.CROSSBODY_MOUNTAIN_CLIMBER -> PoseLandmarkIndex.MOUNTAIN_CLIMBER_CRITICAL

        ExerciseType.SIT_UP,
        ExerciseType.CRUNCH,
        ExerciseType.HEEL_TAP,
        ExerciseType.BICYCLE_CRUNCH,
        ExerciseType.REVERSE_CRUNCH,
        ExerciseType.STRAIGHT_LEG_CRUNCH -> PoseLandmarkIndex.CORE_CRITICAL

        ExerciseType.RUSSIAN_TWIST -> PoseLandmarkIndex.RUSSIAN_TWIST_CRITICAL

        ExerciseType.DUMBBELL_ROW,
        ExerciseType.BENT_OVER_ROW,
        ExerciseType.SHOULDER_PRESS,
        ExerciseType.LATERAL_RAISE,
        ExerciseType.FRONT_RAISE,
        ExerciseType.UPRIGHT_ROW -> PoseLandmarkIndex.DUMBBELL_ROW_CRITICAL

        ExerciseType.BICEPS_CURL,
        ExerciseType.HAMMER_CURL,
        ExerciseType.TRICEPS_EXTENSION,
        ExerciseType.TRICEPS_KICKBACK -> PoseLandmarkIndex.BICEPS_CURL_CRITICAL

        ExerciseType.BENT_OVER_RAISE -> PoseLandmarkIndex.DUMBBELL_ROW_CRITICAL

        ExerciseType.UNKNOWN -> PoseLandmarkIndex.SQUAT_CRITICAL
    }

    fun reset() {
        classifier.reset()
        evaluators.values.forEach { it.reset() }
        // targetExercise belirlenmişse sıfır sonrası hemen doğru evaluator'a kilitlen
        currentExerciseType = targetExercise ?: ExerciseType.UNKNOWN
        lastAnalysisResult = null
    }
}
