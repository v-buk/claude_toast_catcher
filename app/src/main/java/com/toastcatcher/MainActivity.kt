package com.toastcatcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Small manual screen: shows status + captured toasts, with buttons for the same actions as adb. */
class MainActivity : Activity() {

    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener {
                action()
                refresh()
            }
        }

        root.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Start (clears old toasts)") { ToastStore.start(this) })
        root.addView(button("Stop") { ToastStore.stop(this) })
        root.addView(button("Clear") { ToastStore.clear(this) })
        root.addView(button("Refresh") { })

        info = TextView(this).apply { textSize = 14f }
        root.addView(ScrollView(this).apply { addView(info) })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sb = StringBuilder()
        sb.append("Service enabled: ").append(ToastStore.isServiceEnabled(this)).append('\n')
        sb.append("Listening: ").append(ToastStore.isListening(this)).append('\n')
        val rows = ToastStore.readAll(this)
        sb.append("Captured: ").append(rows.size).append("\n\n")
        for (row in rows.takeLast(100)) {
            try {
                val o = JSONObject(row)
                sb.append(o.getString("time")).append("  [")
                    .append(o.getString("package")).append("]\n  ")
                    .append(o.getString("text")).append("\n\n")
            } catch (e: Exception) {
                sb.append(row).append("\n\n")
            }
        }
        info.text = sb.toString()
    }
}
