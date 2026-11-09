package com.void.launcher

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(10, 10, 16)
        window.navigationBarColor = Color.rgb(10, 10, 16)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(10, 10, 16))
        }

        val title = TextView(this).apply {
            text = "VOID"
            textSize = 42f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
        }

        val subtitle = TextView(this).apply {
            text = "MINECRAFT JAVA LAUNCHER"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        layout.addView(title)
        layout.addView(subtitle)

        setContentView(layout)
    }
}
