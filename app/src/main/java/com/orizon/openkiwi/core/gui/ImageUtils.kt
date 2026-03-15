package com.orizon.openkiwi.core.gui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val TAG = "ImageUtils"

    fun toSoftwareBitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.config != Bitmap.Config.HARDWARE) return bitmap
        val soft = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(soft)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return soft
    }

    fun bitmapToBase64(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 70): String {
        val soft = toSoftwareBitmap(bitmap)
        val out = ByteArrayOutputStream()
        soft.compress(format, quality, out)
        val bytes = out.toByteArray()
        Log.d(TAG, "base64 size: ${bytes.size} bytes (${bitmap.width}x${bitmap.height} q=$quality)")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun bitmapToBase64Url(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 70): String {
        val b64 = bitmapToBase64(bitmap, format, quality)
        val mime = when (format) {
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.PNG -> "image/png"
            else -> "image/jpeg"
        }
        return "data:$mime;base64,$b64"
    }

    fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val soft = toSoftwareBitmap(bitmap)
        val w = soft.width; val h = soft.height
        if (w <= maxSize && h <= maxSize) return soft
        val ratio = minOf(maxSize.toFloat() / w, maxSize.toFloat() / h)
        return Bitmap.createScaledBitmap(soft, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
