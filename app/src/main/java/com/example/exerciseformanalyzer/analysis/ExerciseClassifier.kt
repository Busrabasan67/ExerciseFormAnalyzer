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
            // Şınav pozisyonu: gövde yatay (~80-90°) + dirsek aktif + ayakların yerde olması
            isPushUpPosition(frame, angles, torso) -> ExerciseType.PUSH_UP

            // Mekik pozisyonu: gövde yatay veya kalkıyor + kalça açısı değişiyor
            isSitUpPosition(frame, angles, torso) -> ExerciseType.SIT_UP

            // Dumbbell row: gövde 45° eğik + tek kol hareketi
            isDumbbellRowPosition(frame, angles, torso) -> ExerciseType.DUMBBELL_ROW

            // Biceps Curl: dik gövde, düz dizler ve aktif dirsek hareketi
            isBicepsCurlPosition(frame, angles, torso) -> ExerciseType.BICEPS_CURL

            // Squat: dik gövde + aktif diz fleksiyonu
            isSquatPosition(frame, angles, torso) -> ExerciseType.SQUAT


            else -> ExerciseType.UNKNOWN
        }
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
     * Squat pozisyon tespiti:
     * - Gövde görece dik (<35°)
     * - Diz bükülüyor
     */
    private fun isSquatPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso > 50f) return false  // Gövde çok eğik (push-up veya row olabilir)

        val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
        // Diz bükülüyor VEYA zaten bükülü
        return kneeAngle < 170f
    }

    /**
     * Biceps Curl pozisyon tespiti:
     * - Gövde oldukça dik (<35°)
     * - Dizler düz (~170°+)
     * - Dirsek bükülü (aktif hareket halindeyse tanınmasını kolaylaştırır)
     */
    private fun isBicepsCurlPosition(frame: PoseFrame, angles: JointAngles, torso: Float): Boolean {
        if (torso > 35f) return false // Hafif eğilmeye izin var ama fazla olmamalı

        val kneeAngle = angles.leftKneeAngle ?: angles.rightKneeAngle ?: return false
        if (kneeAngle < 160f) return false // Diz çok bükülüyorsa squat veya squat hazırlığı olabilir

        val elbowAngle = angles.leftElbowAngle ?: angles.rightElbowAngle ?: return false
        // Ayaktasınız ve en az bir dirsek hareket ediyor (170'ten daha kapalı)
        return elbowAngle < 170f
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

    fun reset() {
        classificationHistory.clear()
    }
}
