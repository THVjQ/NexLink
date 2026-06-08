package com.nexlink.app.receivers

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import com.klinker.android.send_message.MmsReceivedReceiver

/**
 * Handles the MMS_RECEIVED broadcast fired by TransactionService (android-smsmms) after
 * the MMS PDU has been downloaded from the carrier MMSC and stored in content://mms.
 * The base class already handles: reading the temp file, calling DownloadRequest.persist()
 * to store the PDU, sending NotifyRespInd + AcknowledgeInd ACKs back to the MMSC, and
 * cleaning up the temp file.  We just need to notify the user.
 */
class NexLinkMmsReceivedReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(ctx: Context, messageUri: Uri?) {
        messageUri ?: return
        val id = ContentUris.parseId(messageUri)

        // Addr type 137 (0x89) = FROM in MMS PDU headers
        var sender = "Unknown"
        ctx.contentResolver.query(
            Uri.parse("content://mms/$id/addr"),
            arrayOf("address"), "type = 137", null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                sender = cursor.getString(0)
                    ?.substringBefore("/TYPE=")?.trim()?.ifBlank { null } ?: "Unknown"
            }
        }

        // Detect media type from parts for the notification preview
        var label = "📎 MMS"
        ctx.contentResolver.query(
            Uri.parse("content://mms/$id/part"),
            arrayOf("ct"), null, null, null
        )?.use { cursor ->
            var done = false
            while (cursor.moveToNext() && !done) {
                when {
                    cursor.getString(0)?.startsWith("image/") == true -> { label = "📷 Photo"; done = true }
                    cursor.getString(0)?.startsWith("video/") == true -> { label = "🎥 Video"; done = true }
                    cursor.getString(0)?.startsWith("audio/") == true -> { label = "🎤 Audio"; done = true }
                    cursor.getString(0)?.startsWith("text/plain") == true -> label = "Message"
                }
            }
        }

        SmsNotifier.notify(ctx, sender, label)
    }

    override fun onError(ctx: Context, contentLocation: String) {
        android.util.Log.e("NexLink_MMS", "Download failed: $contentLocation")
    }
}
