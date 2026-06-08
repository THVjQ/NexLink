package com.nexlink.app.db

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Exported ContentProvider with no read permission — serves in-memory MMS PDU bytes
 * to SmsManager.sendMultimediaMessage(). Because it is exported with no readPermission,
 * Samsung's MMS service can read from this URI without any per-package URI grants,
 * bypassing the FileProvider permission issue on One UI devices.
 */
class MmsPduProvider : ContentProvider() {

    companion object {
        private val pdus = ConcurrentHashMap<String, ByteArray>()

        fun store(ctx: Context, bytes: ByteArray): Uri {
            val id = System.currentTimeMillis().toString()
            pdus[id] = bytes
            // Auto-clean after 2 minutes — the MMS service reads synchronously on bind
            Handler(Looper.getMainLooper()).postDelayed({ pdus.remove(id) }, 120_000L)
            return Uri.parse("content://${ctx.packageName}.mmspdu/$id")
        }
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment
            ?: throw FileNotFoundException("Missing PDU id in $uri")
        val bytes = pdus[id]
            ?: throw FileNotFoundException("PDU $id not found (expired)")

        android.util.Log.d("NexLink_MMS",
            "MmsPduProvider.openFile: id=$id, ${bytes.size} bytes, callerUid=${android.os.Binder.getCallingUid()}")

        // Write to a temp file — more reliable than a pipe because Samsung's MMS service
        // can read at its own pace without risk of pipe truncation on large payloads.
        val ctx = context ?: throw FileNotFoundException("Provider has no context")
        val dir  = java.io.File(ctx.cacheDir, "mms_pdu_serve").also { it.mkdirs() }
        val file = java.io.File(dir, "pdu_$id.bin")
        file.writeBytes(bytes)
        Handler(Looper.getMainLooper()).postDelayed({ file.delete() }, 120_000L)

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "application/vnd.wap.mms-message"
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
