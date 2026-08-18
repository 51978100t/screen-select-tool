package com.example.screenselect

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

class MyAccessibilityService : AccessibilityService() {

    private val channelId = "screenselect_channel"
    private var windowManager: WindowManager? = null
    private var selectionContainer: View? = null

    private var startY = 0f
    private var tracking = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        createChannel()
        showNotification("Сервис запущен", "Полоска-ловушка добавлена снизу")
        addBottomStrip()
    }

    private fun addBottomStrip() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val strip = View(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            80,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM

        strip.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    tracking = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (tracking) {
                        val delta = startY - event.rawY
                        if (delta > 100) {
                            tracking = false
                            openSelectionScreen()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracking = false
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(strip, params)
    }

    private fun openSelectionScreen() {
        if (selectionContainer != null) return

        val container = FrameLayout(this)

        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.visibility = View.GONE

        val overlay = SelectionOverlayView(this) { rect ->
            if (rect == null) {
                toolbar.visibility = View.GONE
            } else {
                toolbar.visibility = View.VISIBLE
                showNotification(
                    "Область выделена",
                    "left=" + rect.left + " top=" + rect.top + " right=" + rect.right + " bottom=" + rect.bottom
                )
            }
        }

        container.addView(
            overlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val closeButton = Button(this)
        closeButton.text = "\u2715 Закрыть"
        closeButton.setOnClickListener {
            closeSelectionScreen()
        }

        val redoButton = Button(this)
        redoButton.text = "\u27F3 Заново"
        redoButton.setOnClickListener {
            overlay.clearSelection()
            toolbar.visibility = View.GONE
        }

        toolbar.addView(closeButton)
        toolbar.addView(redoButton)

        val toolbarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        toolbarParams.gravity = Gravity.TOP or Gravity.END
        toolbarParams.topMargin = 60
        toolbarParams.rightMargin = 40
        container.addView(toolbar, toolbarParams)

        selectionContainer = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(container, params)
    }

    private fun closeSelectionScreen() {
        selectionContainer?.let {
            windowManager?.removeView(it)
        }
        selectionContainer = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ScreenSelect",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, text: String) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, builder.build())
    }
}
