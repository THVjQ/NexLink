package com.nexlink.wear

import android.app.Application
import com.nexlink.wear.data.WearDataRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WearDataRepository.loadExisting(applicationContext)
    }
}
