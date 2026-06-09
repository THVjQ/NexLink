package com.nexlink.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.nexlink.app.db.NotificationStore

class App : Application() {
    companion object {
        const val CH_SMS     = "nexlink_sms"
        const val CH_SOCIAL  = "nexlink_social"
    }

    override fun onCreate() {
        super.onCreate()
        NotificationStore.load(applicationContext)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SMS, "SMS Messages", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Incoming SMS notifications" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SOCIAL, "Social Messages", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Social app notifications" }
        )
    }
}
