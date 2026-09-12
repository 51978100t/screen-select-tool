package com.example.screenselect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View

/**
 * Полноэкранный холст поверх скриншота: позволяет рисовать поверх картинки
 * (маркер разных цветов + ластик). Рисунок хранится в отдельном прозрачном
 * слое (overlayBitmap), чтобы можно было стирать не трогая сам скриншот.
 */
class MarkerDrawingView(
    context: Context,
    private val baseBitmap: Bitmap
) : View(context) {

    private val overlayBitmap = Bitmap.createBitmap(
        baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888
    )
    private val overlayCanvas = Canvas(overlayBitmap)

    private var currentColor = Color.parseColor("#FF3B30") // красный по умолчанию
    private var strokeWidth = 10f
    private var eraserMode = false

    private val drawPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // matrix переводит координаты битмапа в координаты экрана (для отрисовки);
    // inverseMatrix — обратно, из координат касания в координаты битмапа
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()

    private var lastMappedX = 0f
    private var lastMappedY = 0f
    private var hasLast = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix(w, h)
    }

    private fun updateMatrix(viewWidth: Int, viewHeight: Int) {
        val bw = baseBitmap.width.toFloat()
        val bh = baseBitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f || viewWidth <= 0 || viewHeight <= 0) return

        val scale = minOf(viewWidth / bw, viewHeight / bh)
        val dx = (viewWidth - bw * scale) / 2f
        val dy = (viewHeight - bh * scale) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        matrix.invert(inverseMatrix)
    }

    fun setColor(color: Int) {
        currentColor = color
        eraserMode = false
    }

    fun setEraser(enabled: Boolean) {
        eraserMode = enabled
    }

    fun isEraser(): Boolean = eraserMode

    fun setStrokeWidth(width: Float) {
        strokeWidth = width
    }

    fun getStrokeWidth(): Float = strokeWidth

    fun clearDrawing() {
        overlayCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
        invalidate()
    }

    /** Скриншот + всё нарисованное поверх, склеенные в один битмап (для "Поделиться"). */
    fun getResultBitmap(): Bitmap {
        val result = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)
        canvas.drawBitmap(overlayBitmap, 0f, 0f, null)
        return result
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)
        canvas.drawBitmap(overlayBitmap, 0f, 0f, null)
        canvas.restore()
    }

    private fun mapToBitmap(x: Float, y: Float): FloatArray {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return pts
    }

    private fun configurePaint() {
        drawPaint.strokeWidth = strokeWidth
        if (eraserMode) {
            drawPaint.color = Color.TRANSPARENT
            drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            drawPaint.color = currentColor
            drawPaint.xfermode = null
        }
    }

    private fun drawDot(x: Float, y: Float) {
        configurePaint()
        overlayCanvas.drawPoint(x, y, drawPaint)
    }

    private fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        configurePaint()
        overlayCanvas.drawLine(x1, y1, x2, y2, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pts = mapToBitmap(event.x, event.y)
        val mx = pts[0]
        val my = pts[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastMappedX = mx
                lastMappedY = my
                hasLast = true
                drawDot(mx, my)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (hasLast) {
                    drawLine(lastMappedX, lastMappedY, mx, my)
                    lastMappedX = mx
                    lastMappedY = my
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hasLast = false
            }
        }
        return true
    }
}
