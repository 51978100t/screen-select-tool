package com.example.screenselect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class SelectionOverlayView(
    context: Context,
    private val onSelectionChanged: (Rect?) -> Unit
) : View(context) {

    private enum class DragMode { NONE, DRAWING, MOVING, RESIZING }
    private enum class Corner { TL, TR, BL, BR }

    // текущая (или предпросмотром показываемая) область выделения
    private var fLeft = 0f
    private var fTop = 0f
    private var fRight = 0f
    private var fBottom = 0f

    // для DRAWING/RESIZING — неподвижный угол, от которого тянется противоположный;
    // для MOVING — координаты последнего касания (для расчёта смещения)
    private var anchorX = 0f
    private var anchorY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var dragMode = DragMode.NONE
    private var selecting = false
    private var hasSelection = false
    private var animating = false
    private var scanProgress = 0f

    private val density = context.resources.displayMetrics.density
    private val bracketLen = 34f * density
    private val bracketThickness = 6f * density
    private val handleTouchRadius = 32f * density
    private val minSelectionSize = 40f * density

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

        canvas.drawRect(fLeft, fTop, fRight, fBottom, outlinePaint)

        drawCorner(canvas, fLeft, fTop, 1, 1)
        drawCorner(canvas, fRight, fTop, -1, 1)
        drawCorner(canvas, fLeft, fBottom, 1, -1)
        drawCorner(canvas, fRight, fBottom, -1, -1)

        if (selecting && fBottom - fTop > 4) {
            val y = fTop + (fBottom - fTop) * scanProgress
            val h = 2f * density
            scanPaint.alpha = 220
            canvas.drawRect(fLeft, y - h, fRight, y + h, scanPaint)
        }
    }

    private fun drawCorner(canvas: Canvas, x: Float, y: Float, dirX: Int, dirY: Int) {
        canvas.drawLine(x, y, x + bracketLen * dirX, y, bracketPaint)
        canvas.drawLine(x, y, x, y + bracketLen * dirY, bracketPaint)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun hitTestCorner(x: Float, y: Float): Corner? {
        if (distance(x, y, fLeft, fTop) <= handleTouchRadius) return Corner.TL
        if (distance(x, y, fRight, fTop) <= handleTouchRadius) return Corner.TR
        if (distance(x, y, fLeft, fBottom) <= handleTouchRadius) return Corner.BL
        if (distance(x, y, fRight, fBottom) <= handleTouchRadius) return Corner.BR
        return null
    }

    private fun isInsideRect(x: Float, y: Float): Boolean {
        return x in fLeft..fRight && y in fTop..fBottom
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (hasSelection) {
                    val corner = hitTestCorner(event.x, event.y)
                    if (corner != null) {
                        dragMode = DragMode.RESIZING
                        when (corner) {
                            Corner.TL -> { anchorX = fRight; anchorY = fBottom }
                            Corner.TR -> { anchorX = fLeft; anchorY = fBottom }
                            Corner.BL -> { anchorX = fRight; anchorY = fTop }
                            Corner.BR -> { anchorX = fLeft; anchorY = fTop }
                        }
                        onSelectionChanged(null)
                        invalidate()
                        return true
                    } else if (isInsideRect(event.x, event.y)) {
                        dragMode = DragMode.MOVING
                        lastTouchX = event.x
                        lastTouchY = event.y
                        onSelectionChanged(null)
                        invalidate()
                        return true
                    } else {
                        // тап вне текущей области — начинаем выделение заново
                        hasSelection = false
                    }
                }

                dragMode = DragMode.DRAWING
                selecting = true
                anchorX = event.x
                anchorY = event.y
                fLeft = event.x
                fTop = event.y
                fRight = event.x
                fBottom = event.y
                onSelectionChanged(null)
                startScanAnimation()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                when (dragMode) {
                    DragMode.DRAWING, DragMode.RESIZING -> {
                        val touchX = event.x.coerceIn(0f, width.toFloat())
                        val touchY = event.y.coerceIn(0f, height.toFloat())
                        var newLeft = minOf(anchorX, touchX)
                        var newTop = minOf(anchorY, touchY)
                        var newRight = maxOf(anchorX, touchX)
                        var newBottom = maxOf(anchorY, touchY)

                        if (newRight - newLeft < minSelectionSize) {
                            if (touchX < anchorX) newLeft = newRight - minSelectionSize
                            else newRight = newLeft + minSelectionSize
                        }
                        if (newBottom - newTop < minSelectionSize) {
                            if (touchY < anchorY) newTop = newBottom - minSelectionSize
                            else newBottom = newTop + minSelectionSize
                        }

                        fLeft = newLeft
                        fTop = newTop
                        fRight = newRight
                        fBottom = newBottom
                        invalidate()
                    }
                    DragMode.MOVING -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        var newLeft = fLeft + dx
                        var newRight = fRight + dx
                        var newTop = fTop + dy
                        var newBottom = fBottom + dy
                        val w = newRight - newLeft
                        val h = newBottom - newTop

                        if (newLeft < 0f) { newLeft = 0f; newRight = w }
                        if (newRight > width) { newRight = width.toFloat(); newLeft = newRight - w }
                        if (newTop < 0f) { newTop = 0f; newBottom = h }
                        if (newBottom > height) { newBottom = height.toFloat(); newTop = newBottom - h }

                        fLeft = newLeft
                        fTop = newTop
                        fRight = newRight
                        fBottom = newBottom
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                    DragMode.NONE -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode != DragMode.NONE) {
                    selecting = false
                    animating = false
                    hasSelection = true
                    dragMode = DragMode.NONE
                    onSelectionChanged(Rect(fLeft.toInt(), fTop.toInt(), fRight.toInt(), fBottom.toInt()))
                    invalidate()
                }
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
        dragMode = DragMode.NONE
        invalidate()
    }

    /** Программно выделяет весь экран (для кнопки "Full"). */
    fun selectFullScreen() {
        fLeft = 0f
        fTop = 0f
        fRight = width.toFloat()
        fBottom = height.toFloat()
        dragMode = DragMode.NONE
        selecting = false
        hasSelection = true
        animating = false
        invalidate()
        onSelectionChanged(Rect(0, 0, width, height))
    }
}
