package com.nexlink.social

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.nexlink.social.core.session.MessageBody
import com.nexlink.social.core.session.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the inline actions on a message notification — §13.4.1.
 *
 * §15.5 is why this is a receiver doing the work itself rather than starting a
 * service: *"any background task must be able to complete its work without a
 * foreground service. If it cannot, it is designed wrongly."* A reply is one
 * network call against a session that is already open.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(Notifications.EXTRA_ROOM_ID) ?: return
        val rid = RoomId(roomId)
        val pending = goAsync()

        scope.launch {
            try {
                val session = SessionProvider.manager(context).current() ?: return@launch
                when (intent.action) {
                    Notifications.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(Notifications.KEY_REPLY_TEXT)?.toString()
                        if (!text.isNullOrBlank()) {
                            session.send(rid, MessageBody.Text(text))
                            session.markRead(rid)
                        }
                    }
                    Notifications.ACTION_MARK_READ -> session.markRead(rid)
                }
                Notifications.dismiss(context, rid)
            } finally {
                pending.finish()
            }
        }
    }
}
