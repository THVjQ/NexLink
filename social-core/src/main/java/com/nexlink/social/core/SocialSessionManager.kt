package com.nexlink.social.core

import android.content.Context
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.core.session.SessionState
import com.nexlink.social.core.session.SocialSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Owns the one live [SocialSession] and its lifecycle — §12, §13.
 *
 * This is the only place that decides whether the app is signed in, and the only
 * place that writes credentials. Everything else observes [state].
 */
class SocialSessionManager(private val context: Context) {

    private val store = SessionStore(context)

    private val _state = MutableStateFlow<SessionState>(SessionState.SignedOut)
    val state: Flow<SessionState> = _state.asStateFlow()

    @Volatile private var session: RustSocialSession? = null

    fun current(): SocialSession? = session

    val hasStoredSession: Boolean get() = store.hasSession

    /**
     * §12.2 — the SDK owns its own store, and it lives in the app's private
     * files directory. That directory is already excluded from cloud backup by
     * `android:allowBackup="false"` (§12.7.1), which is what keeps the crypto
     * store off Google's servers.
     */
    private fun paths(): Pair<String, String> {
        val data = File(context.filesDir, "matrix").apply { mkdirs() }
        val cache = File(context.cacheDir, "matrix").apply { mkdirs() }
        return data.absolutePath to cache.absolutePath
    }

    suspend fun signIn(homeserverUrl: String, username: String, password: String): Result<Unit> =
        runCatching {
            SocialPlatform.init()
            _state.value = SessionState.Restoring
            val (data, cache) = paths()
            val s = RustSocialSession.login(
                homeserverUrl = homeserverUrl,
                username = username,
                password = password,
                sessionPath = data,
                cachePath = cache,
                deviceDisplayName = "NexLink Social (${android.os.Build.MODEL})",
                storeKey = store.storeKey()
            )
            s.persistTo(store)
            session = s
            s.startSync()
            s.publishSignedInState()
            _state.value = s.currentState()
        }.onFailure { _state.value = SessionState.Failed(it.message ?: "sign-in failed") }

    /** Called at launch. Returns false when there is nothing to restore. */
    suspend fun restore(): Boolean {
        val saved = store.load() ?: return false
        return runCatching {
            SocialPlatform.init()
            _state.value = SessionState.Restoring
            val (data, cache) = paths()
            val s = RustSocialSession.restore(saved, data, cache, store.storeKey())
            session = s
            s.startSync()
            s.publishSignedInState()
            _state.value = s.currentState()
            true
        }.getOrElse {
            _state.value = SessionState.Failed(it.message ?: "could not restore session")
            false
        }
    }

    /**
     * §7.4 — set up recovery: a cross-signing identity **and** a key backup,
     * as one step, returning the recovery key.
     *
     * §7.4.4 is why both halves are here. Creating only the key backup leaves a
     * future device able to restore the backup key and still unable to read
     * anything — it stays UNVERIFIED with `recoveryState = INCOMPLETE`, forever.
     * That failure is silent and only surfaces when the user replaces their
     * phone, which is the worst possible moment.
     */
    suspend fun setUpRecovery(
        password: String,
        onProgress: (String) -> Unit = {}
    ): Result<String> = runCatching {
        val s = session ?: error("not signed in")
        onProgress("Setting up your account identity…")
        s.bootstrapCrossSigning(currentUserId(), password)
        onProgress("Encrypting your message history…")
        val key = s.recovery().enableRecovery(waitForBackupToUpload = true) { p ->
            onProgress(
                when (p) {
                    is com.nexlink.social.core.rust.RecoveryProgress.BackingUp ->
                        "Backing up your messages (${p.done} of ${p.total})…"
                    else -> "Setting up recovery…"
                }
            )
        }
        s.publishSignedInState()
        _state.value = s.currentState()
        key
    }

    private fun currentUserId(): String =
        (_state.value as? SessionState.SignedIn)?.userId?.value?.substringAfter('@')?.substringBefore(':')
            ?: error("not signed in")

    /**
     * §32.3 — sign-out clears the credentials AND the SDK's store. Leaving the
     * crypto store behind on a shared device would be a real disclosure, and
     * §20.5.1 makes the same point about the web client.
     */
    suspend fun signOut() {
        runCatching { session?.stopSync() }
        session = null
        store.clear()
        val (data, cache) = paths()
        runCatching { File(data).deleteRecursively(); File(cache).deleteRecursively() }
        _state.value = SessionState.SignedOut
    }
}
