package com.nexlink.app.db

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object DeepLinkHelper {

    // ── WhatsApp: open chat with a phone number ──
    fun whatsApp(ctx: Context, phone: String) {
        val clean = phone.replace("[^+\\d]".toRegex(), "")
        if (!open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$clean")))) {
            openApp(ctx, "com.whatsapp")
        }
    }

    // ── Telegram: open contact by phone number ──
    fun telegram(ctx: Context, phone: String) {
        val clean = phone.replace("[^+\\d]".toRegex(), "").trimStart('+')
        if (!open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$clean")))) {
            openApp(ctx, "org.telegram.messenger")
        }
    }

    // ── Signal: no public contact deep link — open app ──
    fun signal(ctx: Context) = openApp(ctx, "org.thoughtcrime.securesms")

    // ── Messenger: open app ──
    fun messenger(ctx: Context) = openApp(ctx, "com.facebook.orca")

    // ── Open any app by package name, toast if not installed ──
    fun openApp(ctx: Context, pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) ctx.startActivity(intent)
        else Toast.makeText(ctx, "App not installed", Toast.LENGTH_SHORT).show()
    }

    // ── Discord: open app ──
    fun discord(ctx: Context) = openApp(ctx, "com.discord")

    // ── Instagram: open app ──
    fun instagram(ctx: Context) = openApp(ctx, "com.instagram.android")

    // ── Steam: open app ──
    fun steam(ctx: Context) = openApp(ctx, "com.valvesoftware.android.steam.communityapp")

    // ── Open the right app for a given platform string ──
    fun openPlatform(ctx: Context, platform: String) {
        val pkg = when (platform) {
            "Signal"    -> "org.thoughtcrime.securesms"
            "Telegram"  -> "org.telegram.messenger"
            "WhatsApp"  -> "com.whatsapp"
            "Messenger" -> "com.facebook.orca"
            "Discord"   -> "com.discord"
            "Instagram" -> "com.instagram.android"
            "Steam"     -> "com.valvesoftware.android.steam.communityapp"
            else        -> return
        }
        openApp(ctx, pkg)
    }

    private fun open(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent); true
    } catch (_: Exception) { false }
}
