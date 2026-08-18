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
    private var animating = false
    private var scanProgress = 0f

    private val density = context.resources.displayMetrics.density
    private val bracketLen = 34f * density
    private val bracketThickness = 6f * density

    private val cyan = Color.parseColor("#00E5FF")

    private val outlinePaint = Paint().apply {
        color = Color.parseColor("#4000E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        isAntiAlias = true
    }

    private val bracketPaint = Paint().apply {
        color = cyan
        style = Paint.Style.STROKE
        strokeWidth = bracketThickness
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val scanPaint = Paint().apply {
        color = cyan
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!selecting && !hasSelection) return

        val left = minOf(startX, currentX)
        val top = minOf(startY, currentY)
        val right = maxOf(startX, currentX)
        val bottom = maxOf(startY, currentY)

        canvas.drawRect(left, top, right, bottom, outlinePaint)

        drawCorner(canvas, left, top, 1, 1)
        drawCorner(canvas, right, top, -1, 1)
        drawCorner(canvas, left, bottom, 1, -1)
        drawCorner(canvas, right, bottom, -1, -1)

        if (selecting && bottom - top > 4) {
            val y = top + (bottom - top) * scanProgress
            val h = 2f * density
            scanPaint.alpha = 220
            canvas.drawRect(left, y - h, right, y + h, scanPaint)
        }
    }

    private fun drawCorner(canvas: Canvas, x: Float, y: Float, dirX: Int, dirY: Int) {
        canvas.drawLine(x, y, x + bracketLen * dirX, y, bracketPaint)
        canvas.drawLine(x, y, x, y + bracketLen * dirY, bracketPaint)
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
                startScanAnimation()
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
                animating = false
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

    private fun startScanAnimation() {
        animating = true
        val runnable = object : Runnable {
            override fun run() {
                if (!animating) return
                scanProgress += 0.02f
                if (scanProgress > 1f) scanProgress = 0f
                invalidate()
                postOnAnimation(this)
            }
        }
        postOnAnimation(runnable)
    }

    fun clearSelection() {
        hasSelection = false
        selecting = false
        animating = false
        invalidate()
    }
}
