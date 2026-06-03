package com.nexlink.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    companion object {
        const val CH_MESSAGES = "nexlink_messages"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
