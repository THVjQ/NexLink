package com.nexlink.app.db

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves MMS PDU files from the app's cache directory to Samsung's MMS service.
 *
 * Mirrors the approach used by QKSMS / Fossify Messages (Apache-2.0 android-smsmms):
 *   - android:exported="false" + android:grantUriPermissions="true"
 *   - authority = "${applicationId}.MmsFileProvider"
 *   - URI path = filename inside cacheDir
 *
 * The "exported=false + grantUriPermissions" combination is critical on Samsung One UI:
 * it lets Android's SmsManager grant the telephony service a one-shot read permission
 * automatically, whereas exported=true causes Samsung's MmsService to follow a different
 * (broken) code path that rewrites our PDU and strips the Transaction-ID.
 */
class MmsFileProvider : ContentProvider() {

    companion object {
        fun writePdu(ctx: Context, pduBytes: ByteArray): Uri {
            val name = "mms_send_${System.currentTimeMillis()}.dat"
            val file = File(ctx.cacheDir, name)
            file.writeBytes(pduBytes)
            android.util.Log.d("NexLink_MMS",
                "MmsFileProvider.writePdu: wrote ${pduBytes.size} bytes → $name")
            return Uri.Builder()
                .scheme("content")
                .authority("${ctx.packageName}.MmsFileProvider")
                .path(name)
                .build()
        }
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment
            ?: throw FileNotFoundException("Missing filename in $uri")
        val file = File(ctx().cacheDir, name)
        val write = mode.contains("w")
        android.util.Log.d("NexLink_MMS",
            "MmsFileProvider.openFile: $name mode=$mode (${file.length()}B) callerUid=${android.os.Binder.getCallingUid()}")
        val pdMode = if (write)
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        else {
            if (!file.exists()) throw FileNotFoundException("PDU file not found: $name")
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, pdMode)
    }

    private fun ctx() = context ?: throw FileNotFoundException("Provider has no context")

    override fun getType(uri: Uri) = "application/vnd.wap.mms-message"
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
