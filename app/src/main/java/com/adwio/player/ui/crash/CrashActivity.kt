package com.adwio.player.ui.crash

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = intent.getStringExtra("crash")
            ?: getSharedPreferences("adwio_crash", MODE_PRIVATE)
                .getString("last_crash", "Unknown startup error")
            ?: "Unknown startup error"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 22, 28, 22)
            setBackgroundColor(0xFF0B0914.toInt())
        }

        root.addView(TextView(this).apply {
            text = "ADWIO Startup Error"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
        })

        root.addView(TextView(this).apply {
            text = "صوّر هذه الشاشة وأرسلها لي. هذا هو سبب الإغلاق الحقيقي."
            textSize = 16f
            setTextColor(0xFFE6E1F0.toInt())
            setPadding(0, 10, 0, 14)
        })

        val body = TextView(this).apply {
            text = crash.take(9000)
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setTextIsSelectable(true)
        }

        root.addView(ScrollView(this).apply {
            addView(body)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { finishAndRemoveTask() }
        })

        setContentView(root)
    }
}
