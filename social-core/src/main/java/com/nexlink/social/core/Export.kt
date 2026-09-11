package com.nexlink.social.core

import com.nexlink.social.core.session.SocialSession
import com.nexlink.social.core.session.TimelineContent
import com.nexlink.social.core.session.TimelineItem
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export the user's own messages — §12.7.2, §32.4.
 *
 * This is a **data-protection feature**, not a convenience. §32.4 establishes
 * the split plainly: *"the operator answers for server-side data; the app
 * answers for the user's content."* The operator cannot produce message content
 * because it has never been able to read it, so a subject access request for
 * "my messages" can only be satisfied here.
 *
 * It is also §1.2's answer to portability: Matrix is an open protocol, so an
 * export of decrypted events is genuinely portable rather than nominally so.
 *
 * **The output is plaintext.** That is the point — it is for the user — but it
 * means the file is the one place their history exists unencrypted (§12.6.1's
 * problem, deliberately incurred). It is written where the user chose to put it
 * and the app keeps no copy.
 */
object Export {

    private val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.UK)

    data class Result(val rooms: Int, val messages: Int, val bytes: Long)

    /**
     * Write every conversation this device can read to [out] as JSON.
     *
     * @param onProgress room name, so a long export can say what it is doing.
     */
    suspend fun exportAll(
        session: SocialSession,
        out: File,
        onProgress: (String) -> Unit = {}
    ): kotlin.Result<Result> = runCatching {
        val rooms = session.rooms().first()
        val root = JSONObject()
        root.put("exported_at", stamp.format(Date()))
        root.put("format", "nexlink-social-export-1")
        // §32.4 — say what this is and is not, inside the file. An export that
        // arrives without context invites the wrong conclusion about what is
        // missing and why.
        root.put(
            "note",
            "Messages this device could decrypt. Anything sent before this " +
                "device was added to the account is not here, and cannot be " +
                "recovered from the server — the server only ever held " +
                "ciphertext."
        )

        var messageCount = 0
        val roomsJson = JSONArray()

        rooms.forEach { room ->
            onProgress(room.title)
            val timeline = session.timeline(room.id)
            runCatching { timeline.paginateBack(500) }
            val items = timeline.items.first()
            timeline.close()

            val msgs = JSONArray()
            items.forEach { item ->
                itemToJson(item)?.let { msgs.put(it); messageCount++ }
            }
            roomsJson.put(
                JSONObject()
                    .put("name", room.title)
                    .put("id", room.id.value)
                    .put("is_group", room.isGroup)
                    .put("messages", msgs)
            )
        }
        root.put("conversations", roomsJson)

        out.writeText(root.toString(2))
        Result(rooms = rooms.size, messages = messageCount, bytes = out.length())
    }

    private fun itemToJson(item: TimelineItem): JSONObject? {
        val o = JSONObject()
            .put("event_id", item.eventId.value)
            .put("sender", item.sender.value)
            .put("sender_name", item.senderDisplayName)
            .put("sent_at", stamp.format(Date(item.timestamp)))
            .put("edited", item.isEdited)

        when (val c = item.content) {
            is TimelineContent.Text -> o.put("type", "text").put("body", c.body)
            is TimelineContent.Image -> o.put("type", "image").put("caption", c.caption ?: "")
            is TimelineContent.Video -> o.put("type", "video")
            is TimelineContent.Audio -> o.put("type", "audio")
            is TimelineContent.File -> o.put("type", "file").put("name", c.displayName)
            is TimelineContent.Redacted -> o.put("type", "deleted")
            // §14.2.3 — an undecryptable message is EXPORTED AS SUCH rather than
            // omitted. Silently dropping it would make the export look complete
            // when it is not, which is the opposite of what §32.4 needs.
            is TimelineContent.Undecryptable ->
                o.put("type", "unreadable")
                    .put("note", "This device could not decrypt this message.")
        }

        if (item.reactions.isNotEmpty()) {
            val r = JSONObject()
            item.reactions.forEach { (emoji, senders) -> r.put(emoji, senders.size) }
            o.put("reactions", r)
        }
        return o
    }
}
