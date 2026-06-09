package com.nexlink.app.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatCategory(val id: String, val name: String, val threadIds: MutableSet<Long> = mutableSetOf())

object CategoryStore {
    private const val PREFS = "nx_categories"
    private const val KEY   = "categories"

    fun getAll(ctx: Context): List<ChatCategory> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val ids = obj.optJSONArray("threadIds") ?: JSONArray()
                val set = (0 until ids.length()).map { ids.getLong(it) }.toMutableSet()
                ChatCategory(obj.getString("id"), obj.getString("name"), set)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(ctx: Context, categories: List<ChatCategory>) {
        val arr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            val ids = JSONArray(); cat.threadIds.forEach { ids.put(it) }
            obj.put("threadIds", ids)
            arr.put(obj)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    fun addCategory(ctx: Context, name: String): ChatCategory {
        val cats = getAll(ctx).toMutableList()
        val cat = ChatCategory(System.currentTimeMillis().toString(), name)
        cats.add(cat); save(ctx, cats); return cat
    }

    fun renameCategory(ctx: Context, id: String, newName: String) {
        val cats = getAll(ctx).map { if (it.id == id) it.copy(name = newName) else it }
        save(ctx, cats)
    }

    fun deleteCategory(ctx: Context, id: String) {
        save(ctx, getAll(ctx).filter { it.id != id })
    }

    fun assignThread(ctx: Context, categoryId: String, threadId: Long) {
        val cats = getAll(ctx)
        cats.forEach { if (it.id == categoryId) it.threadIds.add(threadId) }
        save(ctx, cats)
    }

    fun removeThread(ctx: Context, categoryId: String, threadId: Long) {
        val cats = getAll(ctx)
        cats.forEach { if (it.id == categoryId) it.threadIds.remove(threadId) }
        save(ctx, cats)
    }
}
