package com.nexlink.app.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private class SentMmsDb(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "sent_mms.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sent_mms (
                id          INTEGER PRIMARY KEY,
                address     TEXT    NOT NULL,
                thread_id   INTEGER NOT NULL DEFAULT 0,
                timestamp   INTEGER NOT NULL,
                mime_type   TEXT,
                media_uri   TEXT,
                body        TEXT    NOT NULL DEFAULT '',
                is_voice    INTEGER NOT NULL DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS sent_mms")
        onCreate(db)
    }
}

object SentMmsStore {

    @Volatile private var helper: SentMmsDb? = null

    private fun db(ctx: Context): SentMmsDb =
        helper ?: synchronized(this) { helper ?: SentMmsDb(ctx).also { helper = it } }

    /** Persist a locally-sent MMS that Samsung didn't write to content://mms. */
    fun save(ctx: Context, msg: SmsMessage) {
        val w = db(ctx).writableDatabase
        val cv = ContentValues().apply {
            put("id",        msg.id)
            put("address",   msg.address)
            put("thread_id", msg.threadId)
            put("timestamp", msg.timestamp)
            put("mime_type", msg.mimeType)
            put("media_uri", msg.mediaUri)
            put("body",      msg.body)
            put("is_voice",  if (msg.isVoice) 1 else 0)
        }
        w.insertWithOnConflict("sent_mms", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        // Prune entries older than 7 days
        w.delete("sent_mms", "timestamp < ?",
            arrayOf((System.currentTimeMillis() - 7 * 86_400_000L).toString()))
    }

    /** Load locally-stored sent MMS for a given address, oldest first. */
    fun loadForAddress(ctx: Context, address: String): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        db(ctx).readableDatabase.query(
            "sent_mms", null, "address = ?", arrayOf(address),
            null, null, "timestamp ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                result += SmsMessage(
                    id         = c.getLong(c.getColumnIndexOrThrow("id")),
                    address    = c.getString(c.getColumnIndexOrThrow("address")),
                    threadId   = c.getLong(c.getColumnIndexOrThrow("thread_id")),
                    timestamp  = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    body       = c.getString(c.getColumnIndexOrThrow("body")) ?: "",
                    isIncoming = false,
                    isMms      = true,
                    isVoice    = c.getInt(c.getColumnIndexOrThrow("is_voice")) == 1,
                    mediaUri   = c.getString(c.getColumnIndexOrThrow("media_uri")),
                    mimeType   = c.getString(c.getColumnIndexOrThrow("mime_type"))
                )
            }
        }
        return result
    }

    /**
     * Remove local entries whose real counterpart now appears in content://mms.
     * Called inside getMessagesByThread after the DB query returns.
     */
    fun dedup(ctx: Context, address: String, realMsgs: List<SmsMessage>) {
        val w = db(ctx).writableDatabase
        for (real in realMsgs) {
            if (!real.isIncoming && real.isMms && real.mimeType != null) {
                w.delete("sent_mms",
                    "address = ? AND mime_type = ? AND ABS(timestamp - ?) < 60000",
                    arrayOf(address, real.mimeType, real.timestamp.toString()))
            }
        }
    }
}
