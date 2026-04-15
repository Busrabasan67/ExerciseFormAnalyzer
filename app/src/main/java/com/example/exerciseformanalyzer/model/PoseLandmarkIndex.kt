package com.example.exerciseformanalyzer.model

/**
 * MediaPipe Pose Landmarker'ın 33 landmark indeksini sabit olarak tanımlar.
 * Resmi kaynak: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
 *
 * Bu nesne, magic number kullanımını önlemek için tüm indeks eşlemelerini içerir.
 */
object PoseLandmarkIndex {
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    /** Squat analizi için gereken kritik landmarklar */
    val SQUAT_CRITICAL = setOf(
        LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE,
        LEFT_ANKLE, RIGHT_ANKLE,
        LEFT_SHOULDER, RIGHT_SHOULDER
    )

    /** Push-up analizi için gereken kritik landmarklar */
    val PUSH_UP_CRITICAL = setOf(
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST,
        LEFT_HIP, RIGHT_HIP,
        LEFT_ANKLE, RIGHT_ANKLE
    )

    /** Sit-up analizi için gereken kritik landmarklar */
    val SIT_UP_CRITICAL = setOf(
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE
    )

    /** Dumbbell row analizi için gereken kritik landmarklar */
    val DUMBBELL_ROW_CRITICAL = setOf(
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST,
        LEFT_HIP, RIGHT_HIP
    )

    /** Biceps curl analizi için gereken kritik landmarklar */
    val BICEPS_CURL_CRITICAL = setOf(
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST,
        LEFT_HIP, RIGHT_HIP // Gövde eğimini ölçmek için kalça da lazım
    )
}
