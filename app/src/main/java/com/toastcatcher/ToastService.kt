package com.toastcatcher

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Android tells accessibility services about every toast on the device (from any app),
 * as a "notification state changed" event whose class name is android.widget.Toast.
 * We keep only those and hand them to ToastStore.
 */
class ToastService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (event.className?.toString() != "android.widget.Toast") return

        val text = event.text.joinToString(" ") { it.toString() }.trim()
        val pkg = event.packageName?.toString() ?: "unknown"
        Log.d(TAG, "toast from $pkg: $text")
        ToastStore.add(applicationContext, pkg, text)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        Log.d(TAG, "service connected")
    }

    companion object {
        private const val TAG = "ToastCatcher"
    }
}
