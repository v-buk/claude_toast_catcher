package com.toastcatcher

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the captured toasts in a small text file (one JSON object per line) inside the
 * app's private storage, plus a "listening" on/off flag. Because it lives on disk, nothing
 * is lost if Android restarts the app process in the middle of a test run.
 */
object ToastStore {
    private const val PREFS = "toastcatcher"
    private const val KEY_LISTENING = "listening"
    private const val FILE_NAME = "toasts.jsonl"
    private val lock = Any()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE_NAME)

    private fun format(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))

    fun isListening(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_LISTENING, false)

    /** START: wipe everything captured before, then begin recording. */
    fun start(ctx: Context) {
        synchronized(lock) {
            file(ctx).delete()
            prefs(ctx).edit().putBoolean(KEY_LISTENING, true).commit()
        }
    }

    /** STOP: stop recording, keep what was captured so it can still be retrieved. */
    fun stop(ctx: Context) {
        synchronized(lock) {
            prefs(ctx).edit().putBoolean(KEY_LISTENING, false).commit()
        }
    }

    fun clear(ctx: Context) {
        synchronized(lock) {
            file(ctx).delete()
        }
    }

    /** Called by the accessibility service for every toast. Ignored unless started. */
    fun add(ctx: Context, pkg: String, text: String) {
        synchronized(lock) {
            if (!isListening(ctx)) return
            val f = file(ctx)
            val id = if (f.exists()) f.readLines().size + 1 else 1
            val now = System.currentTimeMillis()
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("time", format(now))
            obj.put("epoch_ms", now)
            obj.put("package", pkg)
            obj.put("text", text)
            f.appendText(obj.toString() + "\n")
        }
    }

    /** All captured toasts, oldest first, as JSON strings. */
    fun readAll(ctx: Context): List<String> {
        synchronized(lock) {
            val f = file(ctx)
            return if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
        }
    }

    /** Read everything and delete it in one step. */
    fun drain(ctx: Context): List<String> {
        synchronized(lock) {
            val rows = readAll(ctx)
            file(ctx).delete()
            return rows
        }
    }

    fun count(ctx: Context): Int = readAll(ctx).size

    /** True if the accessibility service is switched on in Android settings. */
    fun isServiceEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx.packageName, ToastService::class.java.name)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
