package com.nexlink.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.nexlink.wear.MainActivity
import com.nexlink.wear.data.WearDataRepository

class UnreadComplication : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("3").build(),
            contentDescription = PlainComplicationText.Builder("Unread messages").build()
        ).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null

        WearDataRepository.loadExisting(applicationContext)
        val count = WearDataRepository.unreadCount.value
        val label = if (count > 0) count.toString() else "✓"

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(label).build(),
            contentDescription = PlainComplicationText.Builder(
                if (count > 0) "$count unread" else "No unread messages"
            ).build()
        )
            .setTapAction(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
