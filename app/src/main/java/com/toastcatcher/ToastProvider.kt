package com.toastcatcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

/**
 * This is the "command line" of the app. adb can talk to it with the `content` tool:
 *
 *   start    adb shell content call   --uri content://com.toastcatcher.provider --method start
 *   stop     adb shell content call   --uri content://com.toastcatcher.provider --method stop
 *   status   adb shell content call   --uri content://com.toastcatcher.provider --method status
 *   get      adb shell content query  --uri content://com.toastcatcher.provider/toasts
 *   get+del  adb shell content query  --uri content://com.toastcatcher.provider/toasts/drain
 *   clear    adb shell content delete --uri content://com.toastcatcher.provider/toasts
 */
class ToastProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val out = Bundle()
        when (method) {
            "start" -> {
                ToastStore.start(ctx)
                out.putString("status", "started")
            }
            "stop" -> {
                ToastStore.stop(ctx)
                out.putString("status", "stopped")
            }
            "clear" -> {
                ToastStore.clear(ctx)
                out.putString("status", "cleared")
            }
            "status" -> {
                out.putString("listening", ToastStore.isListening(ctx).toString())
                out.putString("count", ToastStore.count(ctx).toString())
                out.putString("service_enabled", ToastStore.isServiceEnabled(ctx).toString())
            }
            else -> out.putString("error", "unknown method: $method")
        }
        return out
    }

    /** One row per toast, single column "json". Path ending in /drain also deletes them. */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val rows = if (uri.lastPathSegment == "drain") ToastStore.drain(ctx) else ToastStore.readAll(ctx)
        val cursor = MatrixCursor(arrayOf("json"))
        for (row in rows) cursor.addRow(arrayOf<Any>(row))
        return cursor
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val ctx = context ?: return 0
        val n = ToastStore.count(ctx)
        ToastStore.clear(ctx)
        return n
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
