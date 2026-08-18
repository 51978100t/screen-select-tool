package com.example.screenselect

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private val density by lazy { resources.displayMetrics.density }

    private fun dp(value: Int) = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outer = FrameLayout(this)
        outer.setBackgroundColor(Color.parseColor("#08080C"))

        val cyan = Color.parseColor("#00E5FF")
        val magenta = Color.parseColor("#FF2E92")
        addCorner(outer, isTop = true, isLeft = true, color = cyan)
        addCorner(outer, isTop = true, isLeft = false, color = magenta)
        addCorner(outer, isTop = false, isLeft = true, color = magenta)
        addCorner(outer, isTop = false, isLeft = false, color = cyan)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(dp(32), dp(32), dp(32), dp(32))

        val title = TextView(this)
        title.text = "Screen Select"
        title.setTextColor(Color.WHITE)
        title.textSize = 26f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Выделение и распознавание текста с экрана"
        subtitle.setTextColor(Color.parseColor("#7A7A8A"))
        subtitle.textSize = 14f
        subtitle.gravity = Gravity.CENTER
        subtitle.setPadding(0, dp(10), 0, dp(36))

        statusText = TextView(this)
        statusText.textSize = 11f
        statusText.gravity = Gravity.CENTER
        statusText.isAllCaps = true
        statusText.letterSpacing = 0.08f
        statusText.setPadding(dp(18), dp(10), dp(18), dp(10))

        val startButton = Button(this)
        startButton.text = "Старт"
        startButton.textSize = 15f
        startButton.setTextColor(cyan)
        startButton.isAllCaps = true
        startButton.letterSpacing = 0.06f
        startButton.setPadding(dp(44), dp(16), dp(44), dp(16))

        val btnBg = GradientDrawable()
        btnBg.shape = GradientDrawable.RECTANGLE
        btnBg.cornerRadius = dp(4).toFloat()
        btnBg.setColor(Color.parseColor("#0A0A0F"))
        btnBg.setStroke(dp(1), cyan)
        startButton.background = btnBg

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.topMargin = dp(28)
        startButton.layoutParams = btnParams

        startButton.setOnClickListener {
            openAccessibilitySettings()
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(startButton)

        outer.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        setContentView(outer)
    }

    private fun addCorner(root: FrameLayout, isTop: Boolean, isLeft: Boolean, color: Int) {
        val len = dp(28)
        val thickness = dp(3)
        val margin = dp(20)

        val hLine = View(this)
        hLine.setBackgroundColor(color)
        val hParams = FrameLayout.LayoutParams(len, thickness)
        hParams.gravity = (if (isTop) Gravity.TOP else Gravity.BOTTOM) or (if (isLeft) Gravity.START else Gravity.END)
        hParams.topMargin = margin
        hParams.bottomMargin = margin
        hParams.leftMargin = margin
        hParams.rightMargin = margin
        root.addView(hLine, hParams)

        val vLine = View(this)
        vLine.setBackgroundColor(color)
        val vParams = FrameLayout.LayoutParams(thickness, len)
        vParams.gravity = (if (isTop) Gravity.TOP else Gravity.BOTTOM) or (if (isLeft) Gravity.START else Gravity.END)
        vParams.topMargin = margin
        vParams.bottomMargin = margin
        vParams.leftMargin = margin
        vParams.rightMargin = margin
        root.addView(vLine, vParams)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val statusBg = GradientDrawable()
        statusBg.shape = GradientDrawable.RECTANGLE
        statusBg.cornerRadius = dp(3).toFloat()

        if (isAccessibilityServiceEnabled()) {
            statusText.text = "\u25CF  Служба включена"
            statusText.setTextColor(Color.parseColor("#00E5FF"))
            statusBg.setColor(Color.parseColor("#0F1A1D"))
            statusBg.setStroke(dp(1), Color.parseColor("#4D00E5FF"))
        } else {
            statusText.text = "\u25CF  Служба выключена"
            statusText.setTextColor(Color.parseColor("#7A7A8A"))
            statusBg.setColor(Color.parseColor("#0F0F14"))
            statusBg.setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }
        statusText.background = statusBg
    }

    private fun openAccessibilitySettings() {
        try {
            val componentName = ComponentName(this, MyAccessibilityService::class.java).flattenToString()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.putExtra(":settings:fragment_args_key", componentName)
            val bundle = Bundle()
            bundle.putString(":settings:fragment_args_key", componentName)
            intent.putExtra(":settings:show_fragment_args", bundle)
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, MyAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
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
