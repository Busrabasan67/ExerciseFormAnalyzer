package com.example.exerciseformanalyzer.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX kurulumu ve yaşam döngüsü yönetimi.
 * Preview + ImageAnalysis use case'lerini birlikte bağlar.
 *
 * Frame analiz stratejisi: STRATEGY_KEEP_ONLY_LATEST kullanılır.
 * Bu sayede analiz yavaşsa eski kareler düşürülür (backpressure koruması).
 *
 * [onFrameAvailable]: Her analiz karesi için Bitmap ve timestamp ms döner.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAvailable: (bitmap: Bitmap, timestampMs: Long) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT

    /**
     * Kamerayı başlatır ve PreviewView'e bağlar.
     * [previewView] Compose içindeki AndroidView'de kullanılan PreviewView referansı.
     */
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Kamera başlatma hatası: ${e.message}")
                onError("Kamera başlatılamadı: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // Preview use case — ekranda canlı görüntü
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // ImageAnalysis use case — frame analizi
        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            Log.d(TAG, "Kamera use case'leri bağlandı.")
        } catch (e: Exception) {
            Log.e(TAG, "Use case bağlama hatası: ${e.message}")
            onError("Kamera bağlanamadı: ${e.message}")
        }
    }

    /**
     * Her ImageProxy karesini:
     * 1. Döndürme matrisini uygulayarak düzeltir
     * 2. Bitmap'e çevirir
     * 3. Callback ile iletir
     * 4. close() ile proxy'yi serbest bırakır (sızıntı önleme)
     */
    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L  // ns → ms

        val correctedBitmap = rotateBitmap(bitmap, rotationDegrees.toFloat())
        imageProxy.close()  // Her zaman close() çağrılmalı

        onFrameAvailable(correctedBitmap, timestampMs)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
        if (degrees == 0f && !isFront) return bitmap

        val matrix = Matrix()
        matrix.postRotate(degrees)
        if (isFront) {
            // Ön kamerada MediaPipe'ın elde edeceği landmarkların
            // ekrandaki ayna görüntüsü ile aynı yönde çalışması için X eksenini ters çeviriyoruz.
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Ön/arka kamera arasında geçiş yapar. */
    fun toggleCamera(previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
            CameraSelector.LENS_FACING_BACK
        else
            CameraSelector.LENS_FACING_FRONT
        bindCameraUseCases(previewView)
    }

    /** Executor ve kamera kaynaklarını serbest bırakır. */
    fun shutdown() {
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
        Log.d(TAG, "CameraManager kapatıldı.")
    }
}
