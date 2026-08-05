package com.nexlink.app.ui.bridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexlink.app.R
import com.nexlink.app.crypto.BridgeCrypto
import com.nexlink.app.crypto.BridgeKeyManager
import com.nexlink.app.db.BridgeApiClient
import com.nexlink.app.db.BridgePrefs
import com.nexlink.app.services.BridgePollingService
import com.nexlink.app.services.BridgeWatchdogWorker
import com.nexlink.app.ui.applySystemBarInsetsPadding
import org.json.JSONObject

/**
 * Disclaimer (§5b) + setup wizard (§5c) for the Computer Bridge.
 *
 * The feature cannot turn on until: the user ticks the disclaimer checkbox, enters a server +
 * API key (typed or imported), and BOTH a `/health` reachability probe AND an authenticated key
 * check (§16.1) pass. Only then are runtime permissions requested and the service started.
 */
class BridgeSetupActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }
    private fun Int.px() = (this * dp).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)

    // Wizard state
    private var step = STEP_DISCLAIMER
    private var healthOk = false
    private var authOk = false

    /**
     * Pairing code entered at STEP_CONNECT and redeemed at STEP_ENABLE. The wizard rebuilds its
     * whole view hierarchy on every step change, so this cannot live only in the EditText. It is
     * deliberately never persisted — a code is single-use and expires in 15 minutes.
     */
    private var pairingCode: String = ""

    /** Reveals the optional API-key field at STEP_CONNECT. Off unless the user asks, or pairing
     *  came back without a key because the server predates issuing one. */
    private var showManualKey: Boolean = false

    /**
     * Starting the foreground service before this dialog is answered is what crashed the app on
     * first enable: the launch is asynchronous, so the service came up with POST_NOTIFICATIONS
     * still ungranted. The service start waits for the outcome and happens here instead.
     */
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startBridgeServices()
            else toast("Notifications are off, so the bridge can't run in the background. " +
                       "Allow notifications for NexLink, then turn the bridge on again.")
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importConfig(it) } }

    companion object {
        private const val STEP_DISCLAIMER = 0
        private const val STEP_GUIDE      = 1
        private const val STEP_CONNECT    = 2
        private const val STEP_TEST       = 3
        private const val STEP_ENABLE     = 4
        private const val GUIDE_URL = "https://github.com/THVjQ/SMS-Brigde"

        /** Skip straight to the connect step — set by Unlink & re-pair (§4.3). */
        const val EXTRA_START_AT_CONNECT = "start_at_connect"

        /** Server mints codes as 4 random bytes rendered as uppercase hex (`/generate-code`). */
        private val PAIRING_CODE_RE = Regex("^[0-9A-F]{8}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Set up Computer Bridge"
        step = when {
            intent?.getBooleanExtra(EXTRA_START_AT_CONNECT, false) == true -> STEP_CONNECT
            BridgePrefs.isDisclaimerAccepted(this)                         -> STEP_GUIDE
            else                                                           -> STEP_DISCLAIMER
        }

        val scroll = ScrollView(this).apply { setBackgroundColor(col(R.color.bg)); isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.px(), 20.px(), 20.px(), 28.px())
        }
        scroll.addView(root)
        setContentView(scroll)
        root.applySystemBarInsetsPadding()
        render()
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    private fun render() {
        root.removeAllViews()
        when (step) {
            STEP_DISCLAIMER -> renderDisclaimer()
            STEP_GUIDE      -> renderGuide()
            STEP_CONNECT    -> renderConnect()
            STEP_TEST       -> renderTest()
            STEP_ENABLE     -> renderEnable()
        }
    }

    private fun renderDisclaimer() {
        addTitle("Computer Bridge — please read before enabling")
        addBody(
            "This feature lets you send and receive your text messages from a computer. To do that, " +
            "NexLink relays your SMS through a server that you must set up and run yourself.\n\n" +
            "• NexLink provides no server and no cloud service. Nothing works until you point this app " +
            "at a server you control (a home server, VPS, or a free tier like Railway).\n\n" +
            "• Your messages leave your phone. Message contents are end-to-end encrypted between your " +
            "phone and your computer — your server relays only ciphertext and cannot read your texts. " +
            "Your server does still see metadata: phone numbers, timing, and message sizes. And because " +
            "your devices exchange encryption keys through your server, a compromised server could attempt " +
            "to intercept unless you verify the key fingerprint during setup (this wizard shows it).\n\n" +
            "• Secure it properly. Generate a strong, random API key (openssl rand -hex 32), never reuse " +
            "it, and never expose the server to the open internet without TLS.\n\n" +
            "• You are responsible for how you use it. Only relay messages from a device and number you " +
            "own. Intercepting or forwarding other people's messages without consent may be illegal where " +
            "you live.\n\n" +
            "• No warranty. This is a self-hosted, at-your-own-risk feature. The developer can't see, store, " +
            "or recover your data, and isn't liable for messages lost, delayed, or exposed by your server.\n\n" +
            "Setup takes ~15 minutes and requires running one small server. Full guide: $GUIDE_URL"
        )
        val check = CheckBox(this).apply {
            text = "I understand this feature requires my own server and that my incoming messages are processed on it."
            setTextColor(col(R.color.text)); textSize = 13f
            setPadding(6.px(), 12.px(), 0, 12.px())
        }
        root.addView(check)
        val accept = primaryButton("I understand — set up my server") {
            if (!check.isChecked) { toast("Please tick the box to continue"); return@primaryButton }
            BridgePrefs.setDisclaimerAccepted(this, true)
            step = STEP_GUIDE; render()
        }
        accept.isEnabled = false
        check.setOnCheckedChangeListener { _, c -> accept.isEnabled = c }
        root.addView(secondaryButton("Cancel") { finish() })
        root.addView(accept)
    }

    private fun renderGuide() {
        stepHeader(1, "Set up your server")
        addBody(
            "Before continuing, you need your bridge server running. Follow the self-host guide — it " +
            "walks you through deploying the small server. Come back here once it's up and you know " +
            "its address; this phone gets its own key when you pair, so you won't need to type the " +
            "server's key here."
        )
        root.addView(primaryButton("Open the setup guide") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL)))
        })
        root.addView(secondaryButton("I have my server ready →") { step = STEP_CONNECT; render() })
    }

    private fun renderConnect() {
        stepHeader(2, "Connect to your server")
        addBody(
            "You need two things: your server's address, and a one-time pairing code from the PC.\n\n" +
            "On the PC, open the SOS POS tab, click the blue chat button, then Pair Device, then " +
            "Generate Pairing Code. Enter that code here within 15 minutes — after that it expires " +
            "and you'll need a fresh one."
        )

        val etServer = editField("Server URL (https://…)", BridgePrefs.getServerUrl(this), InputType.TYPE_TEXT_VARIATION_URI)
        root.addView(etServer)

        // The pairing code is the only credential this phone needs. Redeeming it registers the
        // device and the server mints a key for this phone alone, which arrives in the /link
        // response — so the shared server API key never has to be typed on a handset, and a lost
        // phone is revoked on its own without re-keying every other device.
        val etCode = editField(
            "Pairing code (from the PC: Pair Device → Generate)",
            pairingCode,
            InputType.TYPE_CLASS_TEXT
        )
        etCode.filters = arrayOf(android.text.InputFilter.LengthFilter(8), android.text.InputFilter.AllCaps())
        root.addView(etCode)

        // Escape hatch for a server old enough not to issue a key at pairing. Hidden by default:
        // offering it up front invites people to type the shared key when they never need to.
        var etKey: EditText? = null
        if (showManualKey) {
            addBody(
                "Manual key entry is only needed for a server that doesn't issue one at pairing. " +
                "If you're not sure, leave this and let pairing handle it."
            )
            etKey = editField("API key (optional)", BridgePrefs.getApiKey(this), InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            root.addView(etKey)
        }

        val warn = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFD48A00.toInt()); setPadding(2.px(), 6.px(), 0, 0)
            visibility = View.GONE
        }
        root.addView(warn)
        fun checkHttp() {
            val u = etServer.text.toString().trim()
            val insecure = u.startsWith("http://")
            warn.visibility = if (insecure) View.VISIBLE else View.GONE
            if (insecure) warn.text = "⚠ Not HTTPS — your pairing code and contacts would be visible on the network. Use HTTPS or a private tunnel (Tailscale / Cloudflare Tunnel)."
        }
        etServer.setOnFocusChangeListener { _, _ -> checkHttp() }

        root.addView(secondaryButton("Import config file…") {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        })
        root.addView(primaryButton("Save & continue →") {
            val server = etServer.text.toString().trim()
            val code   = etCode.text.toString().trim().uppercase()
            if (server.isEmpty()) { toast("Enter your server URL"); return@primaryButton }
            if (code.isEmpty())   { toast("Enter the pairing code generated on the PC"); return@primaryButton }
            if (!PAIRING_CODE_RE.matches(code)) {
                toast("Pairing codes are 8 characters, digits and A–F only (e.g. 3F9A1C0B)")
                return@primaryButton
            }
            BridgePrefs.setServerUrl(this, server)
            etKey?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { BridgePrefs.setApiKey(this, it) }
            pairingCode = code
            healthOk = false; authOk = false
            step = STEP_TEST; render()
        })
        if (!showManualKey) {
            root.addView(secondaryButton("Advanced — enter an API key manually") {
                showManualKey = true; render()
            })
        }
        root.addView(secondaryButton("← Back") { step = STEP_GUIDE; render() })
    }

    private fun renderTest() {
        stepHeader(3, "Test the connection")
        addBody(
            "NexLink will check the server is reachable. Your pairing code is checked in the next " +
            "step, when this phone actually registers itself."
        )
        val status = TextView(this).apply {
            textSize = 14f; setTextColor(col(R.color.text)); setPadding(2.px(), 10.px(), 0, 10.px())
            text = "Server: ${BridgePrefs.serverHost(this@BridgeSetupActivity)}"
        }
        root.addView(status)

        val result = TextView(this).apply { textSize = 13f; setPadding(2.px(), 4.px(), 0, 10.px()) }
        root.addView(result)

        val proceed = primaryButton("Enable bridge →") {
            if (healthOk) { step = STEP_ENABLE; render() } else toast("Run a passing test first")
        }
        proceed.isEnabled = healthOk

        root.addView(primaryButton("Run test") {
            result.setTextColor(col(R.color.muted)); result.text = "Testing reachability…"
            BridgeApiClient.ping(this) { reach ->
                runOnUiThread {
                    healthOk = reach
                    if (reach) {
                        result.setTextColor(0xFF2E7D32.toInt())
                        result.text = "✅ Server reachable."
                    } else {
                        result.setTextColor(0xFFC62828.toInt())
                        result.text = "❌ Can't reach the server. Check the URL, that the server is running, and that it uses HTTPS."
                    }
                    proceed.isEnabled = healthOk
                }
            }
        })
        root.addView(proceed)
        root.addView(secondaryButton("← Back") { step = STEP_CONNECT; render() })
    }

    private fun renderEnable() {
        stepHeader(4, "Link & turn it on")
        val status = TextView(this).apply {
            textSize = 14f; setTextColor(col(R.color.text)); setPadding(2.px(), 8.px(), 0, 8.px())
            text = "Linking device…"
        }
        root.addView(status)

        // Ensure a device keypair exists, then redeem the pairing code. A link failure is FATAL:
        // without a paired_devices row the server has no public key for this phone, so outbound
        // messages are either stored in plaintext or encrypted to a stale device and silently
        // dropped here. Enabling the bridge on a failed link is what made "connected but dead"
        // look like a working setup.
        BridgeKeyManager.getOrCreatePublicKey(this)
        BridgeApiClient.linkDevice(this, pairingCode) { ok, code, msg ->
            runOnUiThread {
                if (!ok) {
                    failPairing(status, "Pairing failed: ${pairingError(code, msg)}")
                    return@runOnUiThread
                }

                // Redeeming the code is what earns this phone a credential: the server mints a key
                // for this device and returns it, and linkDevice has just stored it. If none
                // arrived the phone has nothing to poll with, so stop here rather than enabling a
                // bridge that would report connected and never fetch anything.
                if (BridgePrefs.getApiKey(this).isBlank()) {
                    failPairing(status,
                        "Linked, but the server didn't issue a key for this phone. It's probably " +
                        "running an older version — update it, or enter the API key manually.")
                    showManualKey = true
                    return@runOnUiThread
                }

                status.text = "✅ Linked. Checking access…"
                BridgeApiClient.verifyKey(this) { vOk, _, vReason ->
                    runOnUiThread {
                        if (vOk) {
                            status.text = "✅ Linked."
                            finishEnable()
                        } else {
                            failPairing(status, "Linked, but the key the server issued was rejected: $vReason")
                        }
                    }
                }
            }
        }
    }

    /** Leaves the bridge off and offers a way back, rather than enabling a half-paired device. */
    private fun failPairing(status: TextView, message: String) {
        status.setTextColor(0xFFC62828.toInt())
        status.text = "❌ $message"
        BridgePrefs.setLinked(this, false)
        BridgePrefs.setEnabled(this, false)
        root.addView(primaryButton("Enter a new code") {
            pairingCode = ""
            step = STEP_CONNECT; render()
        })
        root.addView(secondaryButton("Cancel") { finish() })
    }

    /**
     * A 403 from `/link` means the code was not found in `pairing_codes`. Codes are deleted 15
     * minutes after minting and consumed on first use, so an expired or already-used code is far
     * more likely than a typo — say so rather than making the user doubt what they typed.
     */
    private fun pairingError(httpCode: Int, msg: String): String = when (httpCode) {
        403  -> "that code has expired or was already used. Generate a fresh one on the PC and enter it within 15 minutes."
        409  -> "this phone is already paired to a different account on that server."
        429  -> "too many attempts. Wait a minute, then generate a fresh code."
        400  -> "the server rejected the request ($msg)."
        else -> msg
    }

    private fun finishEnable() {
        BridgePrefs.setLinked(this, true)
        BridgePrefs.setEnabled(this, true)

        // Runtime permissions the bridge needs (§7a). If the notification prompt is still to come,
        // the launcher's callback starts the services once the user has answered — see above.
        val needsNotifPrompt = Build.VERSION.SDK_INT >= 33 &&
            (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
        if (needsNotifPrompt) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        requestBatteryExemption()

        if (!needsNotifPrompt) startBridgeServices()

        // Fingerprint (§14.5) + final manual OEM steps (§9 / §12)
        val phonePub = BridgeKeyManager.getOrCreatePublicKey(this)
        val clientKey = BridgeKeyManager.getClientKey(this)
        val fp = if (clientKey != null) BridgeCrypto.fingerprint(phonePub, clientKey) else null

        addTitle("✅ Computer Bridge is on")
        if (fp != null) {
            addBody("Verify this fingerprint matches the one shown in your browser extension — if they match, " +
                "the connection is end-to-end secure even against a malicious server:")
            root.addView(TextView(this).apply {
                text = fp; typeface = Typeface.MONOSPACE; textSize = 18f
                setTextColor(col(R.color.accent)); setPadding(2.px(), 8.px(), 0, 12.px())
            })
        } else {
            addBody("Note: the server did not return a client key yet, so inbound messages will be queued until " +
                "your browser client registers its key. (Full-chain encryption never sends plaintext.)")
        }
        addBody(
            "Keep this phone reliable as a bridge:\n" +
            "• Accept the battery prompt (Unrestricted).\n" +
            "• Settings → Apps → NexLink → Battery → Unrestricted.\n" +
            "• Remove NexLink from Sleeping / Deep-sleeping apps (add to Never sleeping apps).\n" +
            "• Keep the phone plugged in.\n\n" +
            "Note: picture messages (MMS) are not carried over the bridge in this version — an MMS still " +
            "arrives on the phone but won't appear on the computer."
        )
        root.addView(primaryButton("Done") { finish() })
    }

    /** Watchdog + poll service, started only once the notification permission is settled. */
    private fun startBridgeServices() {
        BridgeWatchdogWorker.schedule(this)
        BridgePollingService.start(this)
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun importConfig(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.reader().readText() } ?: return
            val o = JSONObject(text)
            o.optString("serverUrl").takeIf { it.isNotBlank() }?.let { BridgePrefs.setServerUrl(this, it) }
            o.optString("apiKey").takeIf { it.isNotBlank() }?.let { BridgePrefs.setApiKey(this, it) }
            toast("Config imported${o.optString("note").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
            render()  // re-render connect step with prefilled fields
        } catch (e: Exception) {
            toast("Couldn't read config: ${e.message}")
        }
    }

    // ── Small UI helpers ─────────────────────────────────────────────────────────

    private fun stepHeader(n: Int, title: String) {
        root.addView(TextView(this).apply {
            text = "STEP $n OF 4"; textSize = 11f; setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.09f; setTextColor(col(R.color.accent)); setPadding(0, 0, 0, 4.px())
        })
        addTitle(title)
    }

    private fun addTitle(t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 20f; setTypeface(null, Typeface.BOLD)
        setTextColor(col(R.color.text)); setPadding(0, 0, 0, 10.px())
    })

    private fun addBody(t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 14f; setTextColor(col(R.color.text2)); setLineSpacing(4f, 1f)
        setPadding(0, 0, 0, 12.px())
    })

    private fun editField(hint: String, value: String, inputType: Int): EditText = EditText(this).apply {
        this.hint = hint; setText(value); this.inputType = inputType
        setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted)); textSize = 15f
        background = GradientDrawable().apply {
            cornerRadius = 10f * dp; setColor(col(R.color.surface))
            setStroke(1, col(R.color.divider))
        }
        setPadding(14.px(), 12.px(), 14.px(), 12.px())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = 10.px() }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        backgroundTintList = ContextCompat.getColorStateList(this@BridgeSetupActivity, R.color.accent)
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = 8.px() }
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        backgroundTintList = ContextCompat.getColorStateList(this@BridgeSetupActivity, R.color.surface2)
        setTextColor(col(R.color.text))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = 8.px() }
        setOnClickListener { onClick() }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
