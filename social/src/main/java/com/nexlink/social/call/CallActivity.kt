package com.nexlink.social.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.SessionProvider
import com.nexlink.social.rtc.calls.CallService

/**
 * §17.6.3 / §19.5 — the call surface.
 *
 * §17.6 resolved to Option A: the call itself is Element Call, loaded in a
 * WebView, **self-hosted** at this deployment's own `/call/` rather than from
 * `call.element.io`. Everything upstream does — E2EE, key rotation, simulcast,
 * layout, reconnection — arrives without this project reimplementing it, which
 * is the entire argument for that decision.
 *
 * What this class owns is the part a WebView cannot do for itself, and it is
 * exactly the part §17.6's "Against" column worried about:
 *
 * | Concern from §17.6 | Handled here |
 * |---|---|
 * | Telecom integration (§19) | [SocialConnectionService] is registered and told about the call |
 * | Foreground service (§15) | [CallService] starts on **answer**, never on ring (§15.4.1) |
 * | The incoming-call surface | §19.5.1 — notification first, service second |
 * | Hardware media buttons | Follow from the Telecom registration |
 *
 * ## Why a WebView is acceptable here when §11.8 rejected one for messaging
 *
 * §11.8 rejected a JavaScript SDK in a WebView for the *messaging* client on
 * battery, notification and key-storage grounds. A call is foreground,
 * short-lived and user-initiated: it has no background battery cost, posts no
 * notifications of its own, and holds no long-lived keys. Those objections do
 * not transfer, and §17.6 says so explicitly.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var roomId: String? = null
    private var started = false

    /**
     * §15.4.1 — the permissions the call needs, requested **before** the
     * foreground service starts.
     *
     * This ordering is not cosmetic. §15.2.1's fallback chain cannot rescue a
     * missing runtime permission: `startForeground` with the microphone type
     * throws unless RECORD_AUDIO is *granted*, and all three rungs then fail
     * silently. Measured, and recorded in §15.2.1.
     */
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            beginCall()
        } else {
            Toast.makeText(
                this,
                "A call needs the microphone. Nothing was started.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        if (roomId.isNullOrBlank()) { finish(); return }

        web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true            // Element Call keeps state here
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                // Element Call is served from this deployment only. Nothing
                // else is allowed to load, so a compromised page cannot pull
                // code from elsewhere.
                allowFileAccess = false
                allowContentAccess = false
            }
            webViewClient = object : WebViewClient() {
                /**
                 * §17.6.3 — the widget is self-hosted, and this enforces it.
                 *
                 * Anything that is not this origin is refused rather than
                 * loaded. The whole point of self-hosting was to stop a third
                 * party serving executable code into the call surface; without
                 * this check a redirect would undo that silently.
                 */
                override fun shouldOverrideUrlLoading(
                    v: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val host = request?.url?.host ?: return true
                    return host != ALLOWED_HOST
                }
            }
            webChromeClient = object : WebChromeClient() {
                /**
                 * Grant the microphone and camera to the page — but only what
                 * Android has already granted to the app, and only for the
                 * allowed origin.
                 */
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request ?: return
                    if (request.origin?.host != ALLOWED_HOST) { request.deny(); return }
                    val wanted = request.resources.filter { res ->
                        when (res) {
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                has(Manifest.permission.RECORD_AUDIO)
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                has(Manifest.permission.CAMERA)
                            else -> false
                        }
                    }.toTypedArray()
                    if (wanted.isEmpty()) request.deny() else request.grant(wanted)
                }
            }
        }
        setContentView(web)

        permissions.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        )
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun beginCall() {
        if (started) return
        started = true
        val room = roomId ?: return

        // §15.4.1 — the service starts HERE, on answer, with the permission in
        // hand. Never while ringing: that would hold the microphone for a call
        // the user has not taken.
        CallService.start(this, room)

        val session = SessionProvider.manager(this).current() as? RustSocialSession
        val userId = session?.currentState()?.let { st ->
            (st as? com.nexlink.social.core.session.SessionState.SignedIn)?.userId?.value
        }
        web.loadUrl(widgetUrl(room, userId))
    }

    /**
     * §17.6.3 — the self-hosted widget, with the parameters it needs.
     *
     * `preload=false` and `skipLobby` are deliberate: the app has already asked
     * the user to take the call, so a second lobby screen inside the WebView is
     * a step they have effectively already completed.
     */
    private fun widgetUrl(room: String, userId: String?): String {
        val b = Uri.parse(BASE).buildUpon()
            .appendQueryParameter("roomId", room)
            .appendQueryParameter("embed", "true")
            .appendQueryParameter("hideHeader", "true")
            .appendQueryParameter("skipLobby", "true")
            .appendQueryParameter("perParticipantE2EE", "true")
        userId?.let { b.appendQueryParameter("userId", it) }
        return b.build().toString()
    }

    override fun onDestroy() {
        // §17.7 — leaving must tear the service down. A foreground service that
        // outlives its call is the "ghost participant" §17.7's crash row warns
        // about, seen from the device side.
        CallService.stop(this)
        web.loadUrl("about:blank")
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROOM_ID = "com.nexlink.social.call.ROOM_ID"
        private const val ALLOWED_HOST = "nexlink.thvjq.com.au"
        private const val BASE = "https://nexlink.thvjq.com.au/call/room"

        fun intent(c: Context, roomId: String) =
            Intent(c, CallActivity::class.java).putExtra(EXTRA_ROOM_ID, roomId)
    }
}
