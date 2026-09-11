package com.nexlink.social.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Prepare an image for sending — §14.5.1, §14.5.3.
 *
 * Three jobs, and each is a requirement rather than an optimisation:
 *
 * 1. **Downscale.** §14.5.3 caps images at 4 MB before upload. A modern phone
 *    camera produces 8–15 MB files; sending those raw would hit §25.2's 100 MB
 *    ceiling with a handful of photos and burn the recipient's data.
 * 2. **Strip EXIF.** A photo straight from the camera carries GPS coordinates,
 *    the device model and the exact capture time. §1.3 lists location sharing as
 *    a non-goal; silently shipping it inside every photo would make that a lie.
 *    Re-encoding the bitmap drops all of it, except the orientation we re-apply
 *    deliberately.
 * 3. **Honour orientation.** Dropping EXIF means dropping the orientation tag
 *    too, so it has to be baked into the pixels or every portrait photo arrives
 *    sideways.
 */
object ImagePrep {

    private const val MAX_DIMENSION = 2048
    private const val TARGET_BYTES = 4 * 1024 * 1024
    private const val MIN_QUALITY = 60

    data class Prepared(val file: File, val width: Int, val height: Int, val mimeType: String)

    fun prepare(context: Context, uri: Uri): Result<Prepared> = runCatching {
        val rotation = readOrientation(context, uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)

        var bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read that image.")

        if (rotation != 0) bitmap = rotate(bitmap, rotation)

        // Re-encode at descending quality until it fits. §14.5.3's cap is about
        // what the recipient downloads, not what the sender happens to have.
        val out = File(context.cacheDir, "send-${System.currentTimeMillis()}.jpg")
        var quality = 90
        while (true) {
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            if (out.length() <= TARGET_BYTES || quality <= MIN_QUALITY) break
            quality -= 10
        }
        Prepared(out, bitmap.width, bitmap.height, "image/jpeg")
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (w / s > MAX_DIMENSION || h / s > MAX_DIMENSION) s *= 2
        return s
    }

    private fun readOrientation(context: Context, uri: Uri): Int =
        runCatching {
            context.contentResolver.openInputStream(uri).use { stream ->
                when (ExifInterface(stream!!).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        }.getOrDefault(0)

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
