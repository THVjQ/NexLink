package com.nexlink.social.core

import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

/**
 * One-time initialisation of the Matrix SDK's native layer.
 *
 * **This must run before any SDK call that touches the network** — see
 * docs/social/11-sdk-selection.md §11.7.4. Without it every request fails with
 * `InternalException: Expect rustls-platform-verifier to be initialized`, an
 * error that names a Rust crate and gives no hint that an init call is missing.
 *
 * Call from `Application.onCreate`, exactly once.
 */
object SocialPlatform {

    @Volatile private var initialised = false

    @Synchronized
    fun init() {
        if (initialised) return
        initPlatform(
            TracingConfiguration(
                // §27.5 — WARN, never DEBUG. The SDK logs room IDs and user IDs
                // at DEBUG, and §2.8's last invariant forbids identifiers in
                // logs. Turning this up to debug an issue is legitimate;
                // shipping it that way is a standing disclosure.
                logLevel = LogLevel.WARN,
                extraTargets = emptyList(),
                writeToStdoutOrSystem = true,
                writeToFiles = null,
                sentryConfig = null,
                traceLogPacks = emptyList()
            ),
            false
        )
        initialised = true
    }
}
