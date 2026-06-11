package com.nexlink.app.db

import android.content.Context

object GroupNameStore {
    private const val PREFS = "nx_group_names"

    private fun key(participants: List<String>) =
        participants.map { it.replace("\\s".toRegex(), "") }.sorted().joinToString(",")

    fun setName(ctx: Context, participants: List<String>, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(participants), name).apply()
    }

    fun getName(ctx: Context, participants: List<String>): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(participants), null)
}
