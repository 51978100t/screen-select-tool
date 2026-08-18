package com.example.screenselect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

class SelectionOverlayView(
    context: Context,
    private val onSelectionChanged: (Rect?) -> Unit
) : View(context) {

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var selecting = false
    private var hasSelection = false

    private val selectionPaint = Paint().apply {
        color = Color.parseColor("#FF4081")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (selecting || hasSelection) {
            val left = minOf(startX, currentX)
            val top = minOf(startY, currentY)
            val right = maxOf(startX, currentX)
            val bottom = maxOf(startY, currentY)
            canvas.drawRect(left, top, right, bottom, selectionPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                selecting = true
                hasSelection = false
                onSelectionChanged(null)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (selecting) {
                    currentX = event.x
                    currentY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                selecting = false
                hasSelection = true
                val left = minOf(startX, currentX).toInt()
                val top = minOf(startY, currentY).toInt()
                val right = maxOf(startX, currentX).toInt()
                val bottom = maxOf(startY, currentY).toInt()
                onSelectionChanged(Rect(left, top, right, bottom))
                invalidate()
            }
        }
        return true
    }

    fun clearSelection() {
        hasSelection = false
        selecting = false
        invalidate()
    }
}
