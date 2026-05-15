package com.example.exerciseformanalyzer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageUtils {

    suspend fun processImage(
        context: Context,
        uri: Uri,
        targetWidth: Int = 1024,
        targetHeight: Int = 576,
        quality: Int = 80
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 1. Get image dimensions without loading into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }

            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, targetWidth * 2, targetHeight * 2)
            options.inJustDecodeBounds = false

            // 3. Read orientation from EXIF
            val inputStreamForExif = context.contentResolver.openInputStream(uri)
            val exif = inputStreamForExif?.use { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL

            // 4. Decode Bitmap with sampling
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = inputStream?.use { BitmapFactory.decodeStream(it, null, options) }

            if (originalBitmap == null) return@withContext null

            // 5. Rotate bitmap based on EXIF
            val rotatedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
                else -> originalBitmap
            }

            // 6. Scale and Crop to center (Aspect Ratio aware)
            val sourceWidth = rotatedBitmap.width
            val sourceHeight = rotatedBitmap.height
            
            val scaleX = targetWidth.toFloat() / sourceWidth
            val scaleY = targetHeight.toFloat() / sourceHeight
            val scale = Math.max(scaleX, scaleY)
            
            val scaledWidth = scale * sourceWidth
            val scaledHeight = scale * sourceHeight
            
            val left = (targetWidth - scaledWidth) / 2
            val top = (targetHeight - scaledHeight) / 2
            
            val targetRect = android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight)
            val scaledBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(scaledBitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(rotatedBitmap, null, targetRect, paint)
            
            // Clean up intermediate bitmaps if they were created
            if (rotatedBitmap != originalBitmap) originalBitmap.recycle()
            // Note: scaledBitmap will be compressed then returned as bytes, so we'll recycle it after compression
            
            // 7. Compress
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            scaledBitmap.recycle()
            if (rotatedBitmap != scaledBitmap) rotatedBitmap.recycle()

            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun createTempUri(context: Context, prefix: String = "upload_"): Uri {
        val tempFile = File.createTempFile(prefix, ".jpg", context.cacheDir).apply {
            deleteOnExit()
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
}
