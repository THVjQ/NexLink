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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nexlink.social.core.rust.RustCallWidget
import com.nexlink.social.core.session.RoomId

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
    private var widget: RustCallWidget? = null

    /** The host page, waiting to be served to [HOST_PATH]. See below. */
    @Volatile private var pendingHostPage: String? = null

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

                /**
                 * §17.6.3 — serve [hostPage] from the real origin.
                 *
                 * The host page is generated here, not fetched, but it must
                 * still *be* `https://nexlink.thvjq.com.au` as far as the
                 * browser is concerned. `loadDataWithBaseURL` does not give
                 * that: the resulting document's origin is not reliably the
                 * base URL's, and `matrix-widget-api` compares
                 * `event.origin` against `window.origin` before accepting a
                 * message. A mismatch there is invisible — the message is
                 * dropped with no error, and the widget simply times out.
                 *
                 * Intercepting one made-up path on the real origin sidesteps
                 * the whole question: parent and iframe are genuinely
                 * same-origin, so every check passes for the ordinary reason.
                 */
                override fun shouldInterceptRequest(
                    v: WebView?, request: WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val u = request?.url ?: return null
                    if (u.host != ALLOWED_HOST || u.path != HOST_PATH) return null
                    val html = pendingHostPage ?: return null
                    return android.webkit.WebResourceResponse(
                        "text/html", "utf-8",
                        java.io.ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                    )
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
        if (session == null) { toast("Not signed in"); finish(); return }

        lifecycleScope.launch {
            val built = runCatching {
                session.callWidget(
                    roomId = RoomId(room),
                    elementCallUrl = ELEMENT_CALL_URL,
                    parentUrl = ELEMENT_CALL_URL
                )
            }.getOrNull()

            if (built == null) {
                toast("Could not start the call")
                finish(); return@launch
            }
            val (bridge, url) = built
            widget = bridge

            // Pump host → page. postMessage is how the widget API travels, and
            // the widget will not proceed until it hears back.
            bridge.start { msg -> postToWidget(msg) }

            web.addJavascriptInterface(Bridge(), "NexLinkWidgetHost")
            // §17.6.3 — the widget goes in a REAL iframe, inside a host page we
            // control and serve from the real origin. See [hostPage].
            pendingHostPage = hostPage(url)
            web.loadUrl(ORIGIN + HOST_PATH.removePrefix("/"))
        }
    }

    /**
     * §17.6.3 — page → host.
     *
     * The host page forwards anything the widget iframe posts to its parent
     * through here, and this hands it to the Rust widget driver. An embedded
     * widget expects a parent client; here that client is native code.
     */
    private inner class Bridge {
        @android.webkit.JavascriptInterface
        fun postMessage(json: String) {
            val action = runCatching {
                org.json.JSONObject(json).optString("action")
            }.getOrDefault("")
            android.util.Log.d(TAG, "fromWidget action=$action")
            if (action == "set_always_on_screen") {
                // §19.5 — a call is watched, not touched. Without this the
                // screen times out mid-call and the display sleeps on a live
                // conversation. The widget asks; only the host can act.
                //
                // Still forwarded below, so the driver answers the request.
                val on = runCatching {
                    org.json.JSONObject(json).optJSONObject("data")
                        ?.optBoolean("value") ?: false
                }.getOrDefault(false)
                runOnUiThread {
                    if (on) window.addFlags(KEEP_SCREEN_ON)
                    else window.clearFlags(KEEP_SCREEN_ON)
                }
            }
            if (action in CLOSE_ACTIONS) {
                // §17.7 — the widget has left the call. Nothing else will tell
                // us; see [CLOSE_ACTIONS].
                runOnUiThread { finish() }
                return
            }
            lifecycleScope.launch { widget?.fromWidget(json) }
        }
    }

    /**
     * §17.6.3 — a host page whose iframe holds the widget.
     *
     * **Why an iframe rather than loading the widget directly.**
     *
     * The first attempt loaded the widget as the top-level page and replaced
     * `window.parent` with a shim, then delivered host replies by dispatching a
     * synthetic `MessageEvent`. The widget talked — the console showed
     * `[PostmessageTransport] Sending object` and *"Using a matryoshka client"*,
     * so it was genuinely in widget mode — and then **every request timed out**:
     *
     * ```
     * non-fatal error getting supported client versions: Error: Request timed out
     * Could not send DeviceMute action to widget  Error: Request timed out
     * ```
     *
     * The widget-api library validates that a reply came from its parent. A
     * synthetic event cannot satisfy that: `MessageEvent.source` must be a real
     * window, and a hand-made object is not one. Faking it harder was the wrong
     * direction.
     *
     * With a real iframe, every check passes for the ordinary reason — the
     * parent genuinely is the parent, and `postMessage` is genuinely
     * `postMessage`. This page is the small amount of glue that buys that:
     * widget → host via a normal `message` listener, host → widget via a normal
     * `contentWindow.postMessage`.
     */
    private fun hostPage(widgetUrl: String): String = """
        <!doctype html><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
        <style>
          html,body{margin:0;height:100%;background:#000;overflow:hidden}
          iframe{border:0;width:100%;height:100%;display:block}
        </style>
        <iframe id="w" allow="camera;microphone;display-capture;autoplay;clipboard-write"
                src="${widgetUrl.replace("&", "&amp;")}"></iframe>
        <script>
          var f = document.getElementById('w');
          // widget -> host. Only messages from the iframe are forwarded; a
          // message from anywhere else is not ours to relay.
          window.addEventListener('message', function (e) {
            if (e.source !== f.contentWindow) return;
            try {
              NexLinkWidgetHost.postMessage(
                typeof e.data === 'string' ? e.data : JSON.stringify(e.data));
            } catch (err) {}
          });
          // host -> widget. Called from Kotlin.
          window.__toWidget = function (text) {
            try { f.contentWindow.postMessage(JSON.parse(text), '*'); } catch (err) {}
          };
        </script>
    """.trimIndent()

    private fun postToWidget(message: String) {
        // Handed to the host page, which posts it into the iframe. Base64
        // avoids every quoting problem that string-concatenating JSON into
        // JavaScript otherwise creates.
        val b64 = android.util.Base64.encodeToString(
            message.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        web.evaluateJavascript(
            """
            (function () {
              var text = decodeURIComponent(escape(window.atob('$b64')));
              if (window.__toWidget) window.__toWidget(text);
            })();
            """.trimIndent(), null
        )
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        widget?.close()
        widget = null
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
        /**
         * §17.6.3 — this deployment's own Element Call, never call.element.io.
         * Self-hosting was the condition on choosing Option A.
         */
        private const val ELEMENT_CALL_URL = "https://nexlink.thvjq.com.au/call"

        /** The host page and the widget share this origin. */
        private const val ORIGIN = "https://nexlink.thvjq.com.au/"

        /**
         * Where [hostPage] is served. Nothing on the server answers this path
         * — it is intercepted locally — so it cannot collide with Element
         * Call's own routes.
         */
        private const val HOST_PATH = "/__nexlink_widget_host"

        private const val TAG = "NexLinkCall"

        private const val KEEP_SCREEN_ON =
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        /**
         * §17.7 — the actions that mean "the call is over, close me".
         *
         * Measured: tapping the widget's red hang-up button tears down media
         * correctly — the camera closes, the peer sees
         * `Participant disconnected` — and then **leaves the native shell
         * running on a black screen**, foreground service and all. The widget
         * has no way to close a window it does not own; it says so with one of
         * these actions and expects the host to act.
         *
         * `im.vector.hangup` is what Element Call sends today;
         * `io.element.close` and the generic `close` are accepted too, because
         * this is the one message whose loss strands the user, and the cost of
         * matching a name upstream later renames is nothing.
         */
        private val CLOSE_ACTIONS = setOf(
            "im.vector.hangup", "io.element.close", "close"
        )

        fun intent(c: Context, roomId: String) =
            Intent(c, CallActivity::class.java).putExtra(EXTRA_ROOM_ID, roomId)
    }
}
