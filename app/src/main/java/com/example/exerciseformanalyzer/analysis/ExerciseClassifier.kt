package com.example.exerciseformanalyzer.analysis

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils

/**
 * Egzersiz sınıflandırıcı — gelen PoseFrame geçmişinden hangi egzersizin yapıldığını tahmin eder.
 *
 * Yaklaşım: Kural tabanlı heuristik sınıflandırma.
 * - Vücut pozisyonu (dik mi, yatay mı, 45° eğik mi?)
 * - Aktif eklemler (diz mi, dirsek mi?)
 * - Hareket yönü (yukarı-aşağı mı, ileri-geri mi?)
 *
 * Geçmiş pencere (CLASSIFIER_HISTORY_SIZE frame) üzerinden çoğunluk oylaması yapılır.
 * Bu sayede tek kare gürültüsü sınıflandırmayı bozmaz.
 *
 * NOT: Daha gelişmiş bir sistemde bu katman bir ML modeli ile değiştirilebilir.
 */
class ExerciseClassifier {

    private val classificationHistory = ArrayDeque<ExerciseType>(AnalysisConstants.CLASSIFIER_HISTORY_SIZE)

    /**
     * Yeni bir kare sınıflandırır ve geçmiş öbekten çoğunluk egzersizini döndürür.
     */
    fun classify(frame: PoseFrame, angles: JointAngles): ExerciseType {
        val instantType = classifyInstant(frame, angles)
        addToHistory(instantType)
        return getMajorityType()
    }

    /**
     * Tek kare için anlık sınıflandırma.
     * Vücut duruşuna göre kural tabanlı karar ağacı.
     */
    private fun classifyInstant(frame: PoseFrame, angles: JointAngles): ExerciseType {
        if (frame.landmarks.isEmpty()) return ExerciseType.UNKNOWN

        val torso = angles.torsoInclination ?: return ExerciseType.UNKNOWN

        // --- Gövde duruşuna göre ön eleme ---

        return when {
            // Plank (önkol) tespiti: yatay gövde ve önkol yerde
            isPlankPosition(frame, angles, torso) -> ExerciseType.PLANK

            // Şınav pozisyonu: gövde yatay (~80-90°) + dirsek aktif + ayakların yerde olması
            isPushUpPosition(frame, angles, torso) -> ExerciseType.PUSH_UP

            // Heel Tap: gövde yatay + omuzlar hafif kalkık + yanlara esneme
            isHeelTapPosition(frame, angles, torso) -> ExerciseType.HEEL_TAP

            // Bicycle Crunch: gövde yatay + dizler aktif makas hareketi
            isBicycleCrunchPosition(frame, angles, torso) -> ExerciseType.BICYCLE_CRUNCH

            // Reverse Crunch: gövde yatay + bacaklar göğse çekiliyor
            isReverseCrunchPosition(frame, angles, torso) -> ExerciseType.REVERSE_CRUNCH

            // Straight Leg Crunch: gövde yatay + bacaklar 90 derece dik
            isStraightLegCrunchPosition(frame, angles, torso) -> ExerciseType.STRAIGHT_LEG_CRUNCH

            // Mekik pozisyonu: gövde yatay veya kalkıyor + kalça açısı değişiyor
            isSitUpPosition(frame, angles, torso) -> ExerciseType.SIT_UP

            // Russian Twist: gövde hafif eğik + omuzlar rotasyonda
            isRussianTwistPosition(frame, angles, torso) -> ExerciseType.RUSSIAN_TWIST

            // Mountain Climber: şınav pozisyonu + dizlerin aktif hareketi
            isMountainClimberPosition(frame, angles, torso) -> ExerciseType.MOUNTAIN_CLIMBER

            // Dumbbell row: gövde 45° eğik + tek kol hareketi
            isDumbbellRowPosition(frame, angles, torso) -> ExerciseType.DUMBBELL_ROW

            // Biceps Curl: dik gövde, dirsekler sırta yapışık
            isBicepsCurlPosition(frame, angles, torso) -> ExerciseType.BICEPS_CURL

            // Shoulder Press: dik gövde, dirsekler havada (omuz hizasında veya üstünde), eller yukarıda
            isShoulderPressPosition(frame, angles, torso) -> ExerciseType.SHOULDER_PRESS

            // Lateral Raise: dik gövde, omuz açısı artmış, dirsekler düz/hafif bükülü
            isLateralRaisePosition(frame, angles, torso) -> ExerciseType.LATERAL_RAISE

            // Hammer Curl: dik gövde, dirsekler vücuda yapışık (Biceps curl ile aynı duruş, farklı tutuş - biz şimdilik duruşu yakalıyoruz)
            isBicepsCurlPosition(frame, angles, torso) -> ExerciseType.BICEPS_CURL

            // Triceps Extension: dik gövde, kollar başın üstünde
            isTricepsExtensionPosition(frame, angles, torso) -> ExerciseType.TRICEPS_EXTENSION

            // Triceps Kickback / Bent Over Row / Raise: gövde eğik
            isBentOverPosition(frame, angles, torso) -> {
                when {
                    isTricepsKickbackMovement(frame, angles) -> ExerciseType.TRICEPS_KICKBACK
                    isBentOverRaiseMovement(frame, angles) -> ExerciseType.BENT_OVER_RAISE
                    else -> ExerciseType.BENT_OVER_ROW
                }
            }

            // Squat: dik gövde + aktif diz fleksiyonu
            isSquatPosition(frame, angles, torso) -> ExerciseType.SQUAT


            else -> ExerciseType.UNKNOWN
        }
    }

    /**
     * Plank pozisyon tespiti (Önkol Plank):
     * - Gövde yatay (~60° - 110°)
     * - Omuzlar dirsekten belirgin şekilde yukarıda
     * - Dirsek ve bilek yaklaşık aynı yatay hizada (önkollar yerde)
     * - Ayaklar yerde/görünür
     */
    private fun isPlankPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso < 60f) return false

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)
        val lWrist = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_WRIST)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rElbow = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        val rWrist = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_WRIST)

        val isLeftVisible = lShoulder != null && lElbow != null && lWrist != null
        val isRightVisible = rShoulder != null && rElbow != null && rWrist != null

        if (!isLeftVisible && !isRightVisible) return false

        val shoulder: Landmark
        val elbow: Landmark
        val wrist: Landmark

        // Görünürlüğe göre baskın kolu seç (Yandan hangisi kameraya yakınsa)
        if (isLeftVisible && (!isRightVisible || lElbow!!.visibility >= rElbow!!.visibility)) {
            shoulder = lShoulder!!
            elbow = lElbow!!
            wrist = lWrist!!
        } else {
            shoulder = rShoulder!!
            elbow = rElbow!!
            wrist = rWrist!!
        }

        // 1. Omuz, dirsekten belirgin şekilde yüksekte olmalı (y değeri düşük olmalı)
        if (shoulder.y > elbow.y - 0.03f) return false

        // 2. Dirsek ve bilek yaklaşık aynı Y hizasında olmalı (Önkollar yerde)
        val forearmVertDist = Math.abs(elbow.y - wrist.y)
        val upperArmVertDist = Math.abs(shoulder.y - elbow.y)

        // Eğer bilek, dirseğin çok altındaysa (şınavdaki gibi yerdeyken dirsek havadaysa) plank değildir
        if (forearmVertDist > upperArmVertDist * 0.7f) return false

        // 3. Ayakların pozisyonu yere yakın olmalı (Push up kontrolüyle benzer)
        val feetVisible = frame.isVisible(PoseLandmarkIndex.LEFT_ANKLE) || frame.isVisible(PoseLandmarkIndex.RIGHT_ANKLE)

        return feetVisible
    }

    /**
     * Şınav pozisyon tespiti:
     * - Gövde ~70-90° eğik (neredeyse yatay)
     * - Her iki dirsek görünür ve aktif
     * - Kalça, omuz ile ayak bileği arasında hizalı
     */
    private fun isPushUpPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso < 60f) return false  // Gövde çok dik

        val elbowAngle = angles.leftElbowAngle ?: angles.rightElbowAngle ?: return false
        if (elbowAngle > 170f) return false  // Dirsek pasif

        // Ayakların görünür olması şınav pozisyonuna işaret eder
        val feetVisible = frame.isVisible(PoseLandmarkIndex.LEFT_ANKLE) ||
                frame.isVisible(PoseLandmarkIndex.RIGHT_ANKLE)

        return feetVisible
    }

    /**
     * Mekik pozisyon tespiti:
     * - Gövde büyük değişim gösteriyor (farklı karelerde farklı açı)
     * - Diz bükülü (~90°), kalça hareketi belirgin
     */
    private fun isSitUpPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
        // Diz bükülü (60-120°) + gövde neredeyse yatay veya kalkıyor
        return kneeAngle in 50f..130f && torso > 30f
    }

    /**
     * Russian Twist tespiti:
     * - Gövde ~30-60° eğik (oturma pozisyonu)
     * - Dizler bükülü
     * - Omuzlar birbirine yakın (rotasyon perspektifi)
     */
    private fun isRussianTwistPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso < 20f || torso > 65f) return false
        val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
        return kneeAngle < 140f
    }

    /**
     * Mountain Climber tespiti:
     * - Şınav pozisyonuna benzer gövde açısı
     * - Dizlerden biri kalçaya çok yakın (çekilmiş)
     */
    private fun isMountainClimberPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso < 65f) return false
        val lKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val rKnee = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_KNEE)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)
        
        // En az bir diz kalçaya yakınsa (y ekseninde veya x ekseninde çekilmişse)
        val lDist = if (lKnee != null && lHip != null) Math.abs(lKnee.x - lHip.x) else 1.0f
        val rDist = if (rKnee != null && rHip != null) Math.abs(rKnee.x - rHip.x) else 1.0f
        
        return lDist < 0.25f || rDist < 0.25f
    }

    /**
     * Dumbbell row pozisyon tespiti:
     * - Gövde 30-60° eğik (atletik "bowing" pozisyonu)
     * - Bir kol aşağı-yukarı hareket ediyor
     * - Diz hafifçe bükülü (destek pozisyonu)
     */
    private fun isDumbbellRowPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso < 30f || torso > 65f) return false  // Gövde açısı 30-65° arası

        // Bir taraf dirsek belirgin şekilde bükülü, diğer taraf daha açık
        val lElbow = angles.leftElbowAngle
        val rElbow = angles.rightElbowAngle

        if (lElbow != null && rElbow != null) {
            val diff = Math.abs(lElbow - rElbow)
            return diff > 30f  // Asimetrik dirsek açısı = tek kol hareketi
        }
        return false
    }

    /**
     * Lateral Raise (Yana Açış) tespiti:
     * - Gövde oldukça dik (> 60°)
     * - Omuz açısı > 35° (kollar gövdeden ayrılıyor demek)
     * - Dirsekler nispeten düz (>120°). Shoulder Press gibi bükük olmamalı.
     * - Bilek ile dirsek yaklaşık aynı yüksekliktedir.
     */
    private fun isLateralRaisePosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso > 60f) return false

        val shoulderAngle = angles.leftShoulderAngle ?: angles.rightShoulderAngle ?: return false
        val elbowAngle = angles.leftElbowAngle ?: angles.rightElbowAngle ?: return false

        // Kollar gövdenin yanında asılıyken omuz açısı düşüktür. Yukarı kalktığında Lateral Raise başlar.
        if (shoulderAngle < 35f) return false // Vücuda çok bitişik, hareket başlamamış veya başka hareket

        // Dirsek düz veya hafif bükülü olmalı. Row veya curl gibi katlanmış olamaz.
        if (elbowAngle < 120f) return false 

        return true
    }

    /**
     * Squat pozisyon tespiti:
     * - Gövde görece dik (<50°) — önden görünümde eşik biraz gevşetilir
     * - Her iki diz görünüyor (frontal) VEYA baskın diz görünüyor (side)
     * - Diz bükülüyor
     *
     * Önden görünümde: sol VE sağ diz koordinatları her ikisi de görünürse frontal squat.
     * Yandan görünümde: tek taraf görünür, klasik lateral analiz.
     */
    private fun isSquatPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        val bodyProfile = AngleUtils.detectBodyProfile(frame)
        val isFrontal = bodyProfile == BodyProfile.FRONTAL

        // Gövde eğimi eşiği: önden görünümde projeksiyon hatası olabileceğinden biraz geniş tut
        val maxTorso = if (isFrontal) 58f else 50f
        if (torso > maxTorso) return false

        return if (isFrontal) {
            // Önden: her iki diz de görünür mü?
            val lKneeVisible = frame.isVisible(PoseLandmarkIndex.LEFT_KNEE)
            val rKneeVisible = frame.isVisible(PoseLandmarkIndex.RIGHT_KNEE)
            val lHipVisible  = frame.isVisible(PoseLandmarkIndex.LEFT_HIP)
            val rHipVisible  = frame.isVisible(PoseLandmarkIndex.RIGHT_HIP)

            if (!(lKneeVisible || rKneeVisible)) return false
            if (!(lHipVisible || rHipVisible)) return false

            // En az bir diz açısı mevcut ve bükülüyor
            val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
            kneeAngle <= 180f
        } else {
            // Yandan: baskın (tek görünür) tarafın diz açısı
            val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
            kneeAngle <= 180f
        }
    }

    /**
     * Biceps Curl tespiti:
     * - Gövde dik (~70-90° arası)
     * - Dirsekler vücudun yanında sabit (omuz y değeri dirsek y değerinden oldukça küçük, yani dirsek kalçaya daha yakın)
     * - Bilek, dirsekten yukarı doğru hareketli
     */
    private fun isBicepsCurlPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso > 60f) return false // Çok eğik duruyorsa curl değildir

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER) ?: return false
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW) ?: return false
        
        // Curl yaparken dirsek aşağıda omuzun çok altında olur (kalçaya yakındır)
        if (lElbow.y < lShoulder.y + 0.1f) return false 

        val elbowAngle = angles.leftElbowAngle ?: angles.rightElbowAngle ?: return false
        // Dirsek ne tamamen açık ne tamamen kilitli
        return elbowAngle in 30f..180f
    }

    /**
     * Shoulder Press tespiti:
     * - Gövde dik (~60-90° arası, oturarak veya ayakta)
     * - Dirsekler oldukça havada (omuz hizasında veya üzerinde)
     * - Bilekler dirseğin üzerinde (yukarı doğru itiş)
     */
    private fun isShoulderPressPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso > 50f) return false // Eğik durularak omuz presi yapılmaz

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)
        val lWrist = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_WRIST)

        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rElbow = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        val rWrist = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_WRIST)

        // En az bir kol güvenilir şekilde görülmeli
        val isLeftVisible = lShoulder != null && lElbow != null && lWrist != null
        val isRightVisible = rShoulder != null && rElbow != null && rWrist != null

        if (!isLeftVisible && !isRightVisible) return false

        val shoulder = if (isLeftVisible) lShoulder!! else rShoulder!!
        val elbow = if (isLeftVisible) lElbow!! else rElbow!!
        val wrist = if (isLeftVisible) lWrist!! else rWrist!!

        // Dirsek Y ekseninde (Y aşağı doğrudur) omuzun etrafında bir yerde olmalıdır.
        // Biceps Curl'ün aksine dirsek kalçaya yapışık (çok aşağıda) değildir.
        if (elbow.y > shoulder.y + 0.2f) return false // Dirsek çok aşağıda, bu shoulder press başlangıcı olamaz

        // Bilek, dirseğin üstünde veya onunla yaklaşık aynı hizada olmalıdır (yere doğru değil yukarıda)
        if (wrist.y > elbow.y + 0.05f) return false // Bilek sarkmışsa (örn omuz silkme) omuz presi değildir

        return true
    }

    private fun addToHistory(type: ExerciseType) {
        if (classificationHistory.size >= AnalysisConstants.CLASSIFIER_HISTORY_SIZE) {
            classificationHistory.removeFirst()
        }
        classificationHistory.addLast(type)
    }

    /**
     * Geçmiş penceredeki en sık görülen egzersiz tipini döndürür.
     * CLASSIFIER_CONFIDENCE_THRESHOLD oranında çoğunluk yoksa UNKNOWN döner.
     */
    private fun getMajorityType(): ExerciseType {
        if (classificationHistory.isEmpty()) return ExerciseType.UNKNOWN

        val counts = classificationHistory.groupingBy { it }.eachCount()
        val maxEntry = counts.maxByOrNull { it.value } ?: return ExerciseType.UNKNOWN

        val ratio = maxEntry.value.toFloat() / classificationHistory.size
        return if (ratio >= AnalysisConstants.CLASSIFIER_CONFIDENCE_THRESHOLD)
            maxEntry.key
        else ExerciseType.UNKNOWN
    }

    private fun isTricepsExtensionPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso > 45f) return false
        val shoulderAngle = angles.leftShoulderAngle ?: angles.rightShoulderAngle ?: return false
        return shoulderAngle > 140f // Kollar havada
    }

    private fun isBentOverPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        return torso in 30f..70f
    }

    private fun isTricepsKickbackMovement(frame: PoseFrame, angles: JointAngles): Boolean {
        val shoulderAngle = angles.leftShoulderAngle ?: angles.rightShoulderAngle ?: return false
        return shoulderAngle < 35f // Kol vücuda yakın
    }

    private fun isHeelTapPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        // Gövde yataya yakın (30-80°) ve dizler bükülü (ayaklar yere yakın)
        val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
        return torso > 30f && kneeAngle < 120f
    }

    private fun isBicycleCrunchPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        // Gövde yataya yakın ve dizlerden biri çekili diğeri uzatılmış (asimetrik kalça açısı)
        val lHip = angles.leftHipAngle ?: 180f
        val rHip = angles.rightHipAngle ?: 180f
        return torso > 30f && Math.abs(lHip - rHip) > 30f
    }

    private fun isReverseCrunchPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        // Gövde yatay ve en az bir bacak göğse çekilmiş (dar kalça açısı)
        val minHip = minOf(angles.leftHipAngle ?: 180f, angles.rightHipAngle ?: 180f)
        return torso > 40f && minHip < 90f
    }

    private fun isStraightLegCrunchPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        // Gövde yatay ve bacaklar dik (kalça açısı ~90°)
        val lHip = angles.leftHipAngle ?: 180f
        val rHip = angles.rightHipAngle ?: 180f
        return torso > 30f && (lHip in 70f..110f || rHip in 70f..110f)
    }

    private fun isBentOverRaiseMovement(frame: PoseFrame, angles: JointAngles): Boolean {
        val shoulderAngle = angles.leftShoulderAngle ?: angles.rightShoulderAngle ?: return false
        return shoulderAngle > 40f // Kollar açılıyor
    }

    fun reset() {
        classificationHistory.clear()
    }
}
