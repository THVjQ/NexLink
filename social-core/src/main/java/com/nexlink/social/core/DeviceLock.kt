package com.nexlink.social.core

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * §12.4.4 — is there a screen lock, and does the user know what it costs?
 *
 * This is the second half of a bargain §12.4.4 makes explicitly. The store key
 * carries **no** `setUserAuthenticationRequired(true)`, because a background
 * sync woken by a push cannot authenticate the user, and requiring it would
 * stop message delivery whenever the phone is locked. §12.4.4 accepts that —
 * *"a working messenger, protected by platform encryption, rather than a broken
 * one protected slightly better"* — on one condition:
 *
 * > **The app should detect the absence of a device lock and warn clearly,
 * > because FBE without a screen lock provides materially less protection.**
 *
 * Without the warning the trade is not a trade, it is just the weaker half.
 * Android's file-based encryption derives its protection from the user's
 * credential; with no credential set, the data is effectively decrypted the
 * moment the device boots, and the app-level store key sitting in a Keystore
 * that also unlocks at boot does not change that.
 *
 * The warning is **not** a blocker. Refusing to run without a screen lock would
 * be a paternalism this product does not otherwise practise, and would push
 * people to a messenger that warns them about nothing at all.
 */
object DeviceLock {

    /**
     * True when the user has *any* screen lock — PIN, pattern, password, or a
     * biometric backed by one.
     *
     * `isDeviceSecure` rather than `isKeyguardSecure`: the latter has counted
     * swipe-to-unlock as "secure" on some versions, which is precisely the
     * configuration this is meant to catch.
     */
    fun isSet(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            // No KeyguardManager at all is not a device this runs on. Report
            // "set" rather than nagging on something we cannot assess — a
            // warning that cannot be acted on is noise.
            ?: return true
        return km.isDeviceSecure
    }

    /**
     * What to tell the user. Null when a lock is set and there is nothing to say.
     *
     * Phrased around what an attacker gains rather than around the setting,
     * because "no screen lock detected" tells someone nothing about whether to
     * care. §9.6.1's standard for user-facing security copy applies: say what is
     * true, including that this app cannot fix it.
     */
    fun warning(context: Context): String? = warningFor(isSet(context))

    /**
     * The message, decided from the fact alone.
     *
     * Split from [warning] so it can be tested. The branch that matters is the
     * one that cannot be reached on the development phone — it has a screen
     * lock, and removing the owner's lock to exercise a code path is not a
     * test, it is vandalism. So the decision is pure and the Context lookup is
     * the only part left unverified.
     */
    fun warningFor(lockIsSet: Boolean): String? =
        if (lockIsSet) null
        else "This phone has no PIN, pattern or password.\n\n" +
            "Your messages are encrypted in transit and on the server, but the " +
            "copy on this phone is protected by the screen lock. Without one, " +
            "anyone who picks up the phone can read everything — and so can " +
            "anyone who connects it to a computer.\n\n" +
            "Setting a screen lock is the single biggest thing you can do for " +
            "your privacy here, and it is not something this app can do for you."

    /** Where to send them. Always resolvable — it is a platform screen. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
