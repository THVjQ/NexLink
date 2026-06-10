package com.nexlink.wear.service

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.nexlink.wear.data.WearDataRepository

class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { WearDataRepository.handleDataEvent(it) }
    }
}
