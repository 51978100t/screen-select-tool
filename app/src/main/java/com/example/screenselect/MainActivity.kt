package com.example.screenselect

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#111111"))
        root.setPadding(dp(32), dp(32), dp(32), dp(32))

        val title = TextView(this)
        title.text = "Screen Select"
        title.setTextColor(Color.WHITE)
        title.textSize = 26f
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Выделение и распознавание текста с экрана"
        subtitle.setTextColor(Color.parseColor("#AAAAAA"))
        subtitle.textSize = 15f
        subtitle.gravity = Gravity.CENTER
        subtitle.setPadding(0, dp(8), 0, dp(40))

        statusText = TextView(this)
        statusText.textSize = 16f
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(dp(20), dp(12), dp(20), dp(12))

        val startButton = Button(this)
        startButton.text = "Старт"
        startButton.textSize = 18f
        startButton.setTextColor(Color.WHITE)
        startButton.isAllCaps = false
        startButton.setPadding(dp(48), dp(18), dp(48), dp(18))

        val btnBg = GradientDrawable()
        btnBg.shape = GradientDrawable.RECTANGLE
        btnBg.cornerRadius = dp(28).toFloat()
        btnBg.setColor(Color.parseColor("#2979FF"))
        startButton.background = btnBg

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.topMargin = dp(24)
        startButton.layoutParams = btnParams

        startButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(startButton)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val statusBg = GradientDrawable()
        statusBg.shape = GradientDrawable.RECTANGLE
        statusBg.cornerRadius = 999f

        if (isAccessibilityServiceEnabled()) {
            statusText.text = "\u25CF  Служба включена"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            statusBg.setColor(Color.parseColor("#1B3D1F"))
        } else {
            statusText.text = "\u25CF  Служба выключена"
            statusText.setTextColor(Color.parseColor("#BBBBBB"))
            statusBg.setColor(Color.parseColor("#2A2A2A"))
        }
        statusText.background = statusBg
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, MyAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
