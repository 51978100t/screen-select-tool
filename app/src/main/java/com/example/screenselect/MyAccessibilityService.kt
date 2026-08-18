package com.example.screenselect

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class MyAccessibilityService : AccessibilityService() {

    private var homeDownTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        homeDownTime = System.currentTimeMillis()
                    }
                    Log.d("ScreenSelect", "HOME DOWN")
                }
                KeyEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - homeDownTime
                    Log.d("ScreenSelect", "HOME UP, duration=$duration ms")
                    if (duration > 500) {
                        Log.d("ScreenSelect", "LONG PRESS DETECTED!")
                        Toast.makeText(this, "Долгое нажатие Home поймано!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }
}
