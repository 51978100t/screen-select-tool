package com.example.screenselect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/** Одна распознанная строка текста + её перевод + координаты на скриншоте (в пикселях битмапа). */
data class ScreenTextBlock(val rect: Rect, val originalText: String, val translatedText: String)

/**
 * Полноэкранный (нередактируемый) просмотр: показывает скриншот, но поверх
 * каждой распознанной строки текста закрашивает место оригинала и рисует
 * перевод — примерно как режим перевода камерой в Google Lens, только по
 * заранее сделанному снимку экрана.
 */
class ScreenTranslateView(
    context: Context,
    private val baseBitmap: Bitmap,
    private val blocks: List<ScreenTextBlock>
) : View(context) {

    private val matrix = Matrix()
    private val bgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { isAntiAlias = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bw = baseBitmap.width.toFloat()
        val bh = baseBitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f || w <= 0 || h <= 0) return

        val scale = minOf(w / bw, h / bh)
        val dx = (w - bw * scale) / 2f
        val dy = (h - bh * scale) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)

        for (block in blocks) {
            val bgColor = sampleBackgroundColor(block.rect)
            bgPaint.color = bgColor
            canvas.drawRect(block.rect, bgPaint)

            textPaint.color = contrastingColor(bgColor)

            // подбираем размер шрифта так, чтобы перевод уместился по ширине строки
            var textSize = block.rect.height() * 0.72f
            if (textSize < 8f) textSize = 8f
            textPaint.textSize = textSize
            var textWidth = textPaint.measureText(block.translatedText)
            while (textWidth > block.rect.width() && textSize > 8f) {
                textSize -= 1.5f
                textPaint.textSize = textSize
                textWidth = textPaint.measureText(block.translatedText)
            }

            val textX = block.rect.left.toFloat()
            val textY = block.rect.top + block.rect.height() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(block.translatedText, textX, textY, textPaint)
        }

        canvas.restore()
    }

    /** Берём цвет пикселя чуть выше строки — обычно попадает в фон, а не в сам текст. */
    private fun sampleBackgroundColor(rect: Rect): Int {
        val x = rect.left.coerceIn(0, baseBitmap.width - 1)
        val y = (rect.top - 3).coerceIn(0, baseBitmap.height - 1)
        return try {
            baseBitmap.getPixel(x, y)
        } catch (e: Exception) {
            Color.parseColor("#202020")
        }
    }

    private fun contrastingColor(bgColor: Int): Int {
        val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255.0
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}
