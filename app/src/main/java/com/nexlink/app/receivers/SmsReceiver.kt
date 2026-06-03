package com.nexlink.app.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.nexlink.app.App
import com.nexlink.app.R
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.ui.sms.ConversationActivity

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val grouped = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            grouped.getOrPut(msg.originatingAddress ?: "") { StringBuilder() }
                .append(msg.messageBody)
        }
        for ((sender, body) in grouped) {
            SmsNotifier.notify(context, sender, body.toString())
        }
    }
}

class SmsReceiverFallback : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val grouped = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            grouped.getOrPut(msg.originatingAddress ?: "") { StringBuilder() }
                .append(msg.messageBody)
        }
        for ((sender, body) in grouped) {
            SmsNotifier.notify(context, sender, body.toString())
        }
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { /* MMS stub */ }
}

object SmsNotifier {
    fun notify(ctx: Context, sender: String, body: String) {
        val name = SmsHelper.getContactName(ctx, sender)
        val pi = PendingIntent.getActivity(
            ctx, sender.hashCode(),
            Intent(ctx, ConversationActivity::class.java).apply {
                putExtra("address", sender)
                putExtra("contact_name", name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, App.CH_SMS)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(sender.hashCode(), notif)
    }
}
