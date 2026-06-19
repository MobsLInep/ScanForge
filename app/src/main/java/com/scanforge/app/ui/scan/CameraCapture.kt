package com.scanforge.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts a JPEG [ImageProxy] from `ImageCapture` into upright JPEG bytes, baking in the reported
 * rotation so downstream OpenCV/storage never has to reason about EXIF orientation.
 */
fun ImageProxy.toUprightJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val raw = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw

    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val rotated = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(rotation.toFloat()) },
        true,
    )
    return ByteArrayOutputStream().use { out ->
        rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
        if (rotated != bitmap) rotated.recycle()
        bitmap.recycle()
        out.toByteArray()
    }
}
