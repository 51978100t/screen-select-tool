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

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Сервис запущен и подключен", Toast.LENGTH_SHORT).show()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        homeDownTime = System.currentTimeMillis()
                    }
                    Toast.makeText(this, "HOME DOWN поймано", Toast.LENGTH_SHORT).show()
                }
                KeyEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - homeDownTime
                    Toast.makeText(this, "HOME UP, длительность=" + duration + "мс", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return super.onKeyEvent(event)
    }
}
