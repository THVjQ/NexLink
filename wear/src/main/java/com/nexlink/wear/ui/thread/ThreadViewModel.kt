package com.nexlink.wear.ui.thread

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexlink.shared.SmsMessage
import com.nexlink.wear.data.WearDataRepository
import com.nexlink.wear.data.WearMessageClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ThreadViewModel(app: Application) : AndroidViewModel(app) {

    private val _messages  = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var loadedThreadId = -1L

    fun loadThread(threadId: Long, address: String) {
        if (threadId == loadedThreadId && _messages.value.isNotEmpty()) return
        loadedThreadId = threadId
        _isLoading.value = true

        // Serve from cache immediately if available
        WearDataRepository.threads.value[threadId]?.let {
            _messages.value = it
            _isLoading.value = false
        }

        // Always request fresh data from phone
        WearMessageClient.requestThread(getApplication(), threadId, address)

        // Observe repository for incoming push
        viewModelScope.launch {
            WearDataRepository.threads.collect { map ->
                map[threadId]?.let {
                    _messages.value = it
                    _isLoading.value = false
                }
            }
        }
    }

    fun markRead(ctx: Context, threadId: Long) {
        WearMessageClient.markRead(ctx, threadId)
    }
}
