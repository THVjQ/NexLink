package com.nexlink.social

import android.app.Application
import com.nexlink.social.core.SocialPlatform
import com.nexlink.social.core.SocialSessionManager

/**
 * §11.7.4 — `SocialPlatform.init()` must run before any SDK network call, and
 * `Application.onCreate` is the only place that is reliably true. Skipping it
 * fails every request with an error naming a Rust crate.
 */
class SocialApplication : Application() {

    lateinit var sessions: SocialSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        SocialPlatform.init()
        sessions = SocialSessionManager(this)
    }
}
