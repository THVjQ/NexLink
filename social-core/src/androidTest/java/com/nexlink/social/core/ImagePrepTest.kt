package com.nexlink.social.core

import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §14.5.1 — a photo must not carry its EXIF to the recipient.
 *
 * §1.3 lists location sharing as a non-goal. A camera photo carries GPS
 * coordinates, the device model and the capture time, so shipping one unmodified
 * would make that non-goal false in the most literal way — and silently, because
 * nothing in the UI mentions it.
 *
 * This test builds a JPEG that definitely has GPS and a device model, runs it
 * through [ImagePrep], and asserts both are gone.
 */
@RunWith(AndroidJUnit4::class)
class ImagePrepTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun jpegWithExif(): File {
        val bmp = android.graphics.Bitmap.createBitmap(3000, 2000, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawColor(android.graphics.Color.CYAN)
        val f = File(ctx.cacheDir, "exif-src.jpg")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }

        ExifInterface(f.absolutePath).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "33/1,52/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "151/1,12/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_MODEL, "SecretPhoneModel")
            setAttribute(ExifInterface.TAG_MAKE, "SecretMake")
            saveAttributes()
        }
        return f
    }

    @Test fun exifIsStrippedAndImageIsDownscaled() {
        val src = jpegWithExif()

        // Precondition: the source really does carry what we claim to remove.
        val before = ExifInterface(src.absolutePath)
        assertNotNull("precondition: source has GPS", before.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals("SecretPhoneModel", before.getAttribute(ExifInterface.TAG_MODEL))

        val prepared = ImagePrep.prepare(ctx, android.net.Uri.fromFile(src)).getOrThrow()

        val after = ExifInterface(prepared.file.absolutePath)
        assertNull("GPS latitude must not survive", after.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS longitude must not survive", after.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("device model must not survive", after.getAttribute(ExifInterface.TAG_MODEL))
        assertNull("device make must not survive", after.getAttribute(ExifInterface.TAG_MAKE))

        // §14.5.3 — and it is downscaled within the cap.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(prepared.file.absolutePath, opts)
        assertTrue("longest edge should be capped", maxOf(opts.outWidth, opts.outHeight) <= 2048)
        assertTrue("should be under the 4 MB cap", prepared.file.length() < 4 * 1024 * 1024)

        println("IMAGEPREP src=${src.length()} out=${prepared.file.length()} " +
                "dims=${opts.outWidth}x${opts.outHeight}")
        src.delete(); prepared.file.delete()
    }
}
