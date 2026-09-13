package com.example.screenselect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View

/**
 * Полноэкранный холст поверх скриншота: позволяет рисовать поверх картинки
 * (маркер разных цветов, ластик, размытие/пикселизация для скрытия личных данных).
 * Рисунок хранится в отдельном прозрачном слое (overlayBitmap), чтобы можно
 * было стирать не трогая сам скриншот.
 */
class MarkerDrawingView(
    context: Context,
    private val baseBitmap: Bitmap
) : View(context) {

    enum class Tool { MARKER, ERASER, BLUR }

    data class ToolState(val tool: Tool, val color: Int, val strokeWidthPx: Float)

    private val overlayBitmap = Bitmap.createBitmap(
        baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888
    )
    private val overlayCanvas = Canvas(overlayBitmap)

    // пикселизированная версия скриншота — источник для Blur-мазка
    private val blurredBitmap: Bitmap by lazy { createPixelatedBitmap(baseBitmap, 18) }

    private var currentTool = Tool.MARKER
    private var currentColor = Color.parseColor("#FF3B30") // красный по умолчанию

    private val density = context.resources.displayMetrics.density
    // маркер — тонкая линия по умолчанию; ластик/blur — сразу крупнее (удобнее для замазывания)
    private var markerStrokeWidth = 10f
    private var eraserBlurStrokeWidth = 24f * density
    private var strokeWidth = markerStrokeWidth

    /** Вызывается при любом изменении инструмента/цвета/толщины — для обновления превью. */
    var onToolChanged: (() -> Unit)? = null

    private val drawPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val maskPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    private val blurStampPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
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

    private fun createPixelatedBitmap(source: Bitmap, blockSize: Int): Bitmap {
        val smallW = (source.width / blockSize).coerceAtLeast(1)
        val smallH = (source.height / blockSize).coerceAtLeast(1)
        // сначала уменьшаем со сглаживанием (усредняет блок), затем увеличиваем
        // обратно без сглаживания (nearest neighbor) — получаются крупные "пиксели"
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        return Bitmap.createScaledBitmap(small, source.width, source.height, false)
    }

    fun setColor(color: Int) {
        currentColor = color
        if (currentTool != Tool.MARKER) {
            strokeWidth = markerStrokeWidth
        }
        currentTool = Tool.MARKER
        onToolChanged?.invoke()
    }

    fun setEraser() {
        if (currentTool == Tool.MARKER) {
            strokeWidth = eraserBlurStrokeWidth
        }
        currentTool = Tool.ERASER
        onToolChanged?.invoke()
    }

    fun setBlur() {
        if (currentTool == Tool.MARKER) {
            strokeWidth = eraserBlurStrokeWidth
        }
        currentTool = Tool.BLUR
        onToolChanged?.invoke()
    }

    fun setStrokeWidth(width: Float) {
        strokeWidth = width
        when (currentTool) {
            Tool.MARKER -> markerStrokeWidth = width
            Tool.ERASER, Tool.BLUR -> eraserBlurStrokeWidth = width
        }
        onToolChanged?.invoke()
    }

    fun getStrokeWidth(): Float = strokeWidth

    fun getToolState(): ToolState = ToolState(currentTool, currentColor, strokeWidth)

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

    private fun stampSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        when (currentTool) {
            Tool.MARKER -> {
                drawPaint.strokeWidth = strokeWidth
                drawPaint.color = currentColor
                overlayCanvas.drawLine(x1, y1, x2, y2, drawPaint)
            }
            Tool.ERASER -> {
                drawPaint.strokeWidth = strokeWidth
                drawPaint.color = Color.TRANSPARENT
                drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                overlayCanvas.drawLine(x1, y1, x2, y2, drawPaint)
                drawPaint.xfermode = null
            }
            Tool.BLUR -> {
                val padding = strokeWidth
                val left = (minOf(x1, x2) - padding).coerceAtLeast(0f)
                val top = (minOf(y1, y2) - padding).coerceAtLeast(0f)
                val right = (maxOf(x1, x2) + padding).coerceAtMost(overlayBitmap.width.toFloat())
                val bottom = (maxOf(y1, y2) + padding).coerceAtMost(overlayBitmap.height.toFloat())

                val w = (right - left).toInt().coerceAtLeast(1)
                val h = (bottom - top).toInt().coerceAtLeast(1)
                if (left >= right || top >= bottom) return

                // временный слой строго по размеру мазка (не всего скриншота) — быстро
                val patch = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val patchCanvas = Canvas(patch)

                maskPaint.strokeWidth = strokeWidth
                patchCanvas.drawLine(x1 - left, y1 - top, x2 - left, y2 - top, maskPaint)

                val srcLeft = left.toInt().coerceAtMost(blurredBitmap.width - 1)
                val srcTop = top.toInt().coerceAtMost(blurredBitmap.height - 1)
                val srcRight = (srcLeft + w).coerceAtMost(blurredBitmap.width)
                val srcBottom = (srcTop + h).coerceAtMost(blurredBitmap.height)
                patchCanvas.drawBitmap(
                    blurredBitmap,
                    android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom),
                    android.graphics.Rect(0, 0, srcRight - srcLeft, srcBottom - srcTop),
                    blurStampPaint
                )

                overlayCanvas.drawBitmap(patch, left, top, null)
                patch.recycle()
            }
        }
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
                stampSegment(mx, my, mx, my)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (hasLast) {
                    stampSegment(lastMappedX, lastMappedY, mx, my)
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

    /**
     * Маленький кружок-превью: показывает текущий цвет/размер маркера,
     * либо текстуру прозрачности (ластик) / пикселизации (Blur).
     */
    class ToolPreviewView(context: Context) : View(context) {

        private var tool = Tool.MARKER
        private var color = Color.RED
        private var strokeWidthPx = 10f

        private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val checkerPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val outlinePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#80FFFFFF")
        }

        fun updateState(state: ToolState) {
            tool = state.tool
            color = state.color
            strokeWidthPx = state.strokeWidthPx
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (strokeWidthPx / 2f).coerceAtLeast(2f)

            when (tool) {
                Tool.MARKER -> {
                    fillPaint.color = color
                    canvas.drawCircle(cx, cy, radius, fillPaint)
                }
                Tool.ERASER -> drawChecker(canvas, cx, cy, radius, Color.parseColor("#EAEAEA"), Color.parseColor("#B0B0B0"))
                Tool.BLUR -> drawChecker(canvas, cx, cy, radius, Color.parseColor("#606060"), Color.parseColor("#303030"))
            }

            canvas.drawCircle(cx, cy, radius, outlinePaint)
        }

        private fun drawChecker(canvas: Canvas, cx: Float, cy: Float, radius: Float, colorA: Int, colorB: Int) {
            val path = Path()
            path.addCircle(cx, cy, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(path)

            val cell = (radius / 2.2f).coerceAtLeast(3f)
            var y = cy - radius
            var row = 0
            while (y < cy + radius) {
                var x = cx - radius
                var col = 0
                while (x < cx + radius) {
                    checkerPaint.color = if ((row + col) % 2 == 0) colorA else colorB
                    canvas.drawRect(x, y, x + cell, y + cell, checkerPaint)
                    x += cell
                    col++
                }
                y += cell
                row++
            }
            canvas.restore()
        }
    }
}
