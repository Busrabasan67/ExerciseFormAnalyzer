package com.example.exerciseformanalyzer.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.exerciseformanalyzer.model.Landmark
import com.example.exerciseformanalyzer.model.PoseFrame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * MediaPipe Pose Landmarker'ı saran katman.
 * CameraX'ten gelen Bitmap karelerini alır ve pose landmarklarına dönüştürür.
 *
 * Kurulum notları:
 * - Model dosyası (pose_landmarker_lite.task veya full/heavy) assets/ klasörüne konulmalıdır.
 * - GPU Delegate kullanılabiliyorsa otomatik olarak tercih edilir.
 * - LIVE_STREAM modu kamera akışı için uygundur; sonuçlar callback ile döner.
 *
 * Model indirme:
 *   https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
 *   → app/src/main/assets/pose_landmarker_lite.task olarak kaydedin
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val onResult: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        private const val MODEL_FILE = "pose_landmarker_lite.task"
        private const val MAX_PERSONS = 1
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
    }

    private var poseLandmarker: PoseLandmarker? = null

    /**
     * MediaPipe Pose Landmarker'ı başlatır.
     * GPU delegate önce denenir; başarısız olursa CPU'ya düşer.
     */
    fun setup() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .setDelegate(Delegate.GPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumPoses(MAX_PERSONS)
                .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, image -> handleResult(result, image) }
                .setErrorListener { error -> onError(error.message ?: "MediaPipe hatası") }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "PoseLandmarker GPU delegate ile başlatıldı.")
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate başarısız, CPU'ya düşülüyor: ${e.message}")
            setupWithCpu()
        }
    }

    private fun setupWithCpu() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .setDelegate(Delegate.CPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumPoses(MAX_PERSONS)
                .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, image -> handleResult(result, image) }
                .setErrorListener { error -> onError(error.message ?: "MediaPipe hatası") }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "PoseLandmarker CPU ile başlatıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "PoseLandmarker başlatılamadı: ${e.message}")
            onError("Pose algılayıcı başlatılamadı: ${e.message}")
        }
    }

    /**
     * Bir Bitmap kareyi MediaPipe'a gönderir (asenkron — LIVE_STREAM modu).
     * Her çağrıda timestamp monoton artmalıdır.
     */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        val landmarker = poseLandmarker ?: run {
            Log.w(TAG, "PoseLandmarker henüz başlatılmadı.")
            return
        }
        try {
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "detectAsync hatası: ${e.message}")
        }
    }

    /**
     * MediaPipe sonucunu uygulama modelimize (PoseFrame) dönüştürür.
     * Sonuç boşsa (kişi yok) boş landmark listesiyle PoseFrame üretilir.
     */
    private fun handleResult(result: PoseLandmarkerResult, image: MPImage) {
        val timestampMs = result.timestampMs()
        val imageWidth = image.width
        val imageHeight = image.height

        if (result.landmarks().isEmpty()) {
            // Karede kişi yok
            onResult(
                PoseFrame(
                    landmarks = emptyList(),
                    timestampMs = timestampMs,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight
                )
            )
            return
        }

        // İlk kişinin landmarklarını al (MAX_PERSONS = 1)
        val rawLandmarks = result.landmarks()[0]
        val worldLandmarks = result.worldLandmarks()
            .getOrNull(0)

        val landmarks = rawLandmarks.mapIndexed { index, lm ->
            Landmark(
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                visibility = lm.visibility().orElse(0f),
                presence = lm.presence().orElse(0f)
            )
        }

        onResult(
            PoseFrame(
                landmarks = landmarks,
                timestampMs = timestampMs,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )
        )
    }

    /** Kaynakları serbest bırakır — lifecycle'da onDestroy'da çağrılmalı. */
    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        Log.d(TAG, "PoseLandmarker kapatıldı.")
    }

    val isReady: Boolean get() = poseLandmarker != null
}
