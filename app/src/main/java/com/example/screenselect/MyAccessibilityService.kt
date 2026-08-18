package com.example.screenselect

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MyAccessibilityService : AccessibilityService() {

    private val channelId = "screenselect_channel"
    private var windowManager: WindowManager? = null
    private var selectionContainer: View? = null
    private var stripView: View? = null
    private var lastRect: Rect? = null

    private var startY = 0f
    private var tracking = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        removeStripView()
        closeSelectionScreen()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeStripView()
        closeSelectionScreen()
        return super.onUnbind(intent)
    }

    private fun removeStripView() {
        stripView?.let { windowManager?.removeView(it) }
        stripView = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createChannel()
        showNotification("Сервис запущен", "Полоска-ловушка добавлена снизу")
        addBottomStrip()
    }

    private fun getNavBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(48)
    }

    private fun addBottomStrip() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val strip = View(this)
        stripView = strip

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            60,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM
        params.y = getNavBarHeight()

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

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun createIconButton(emoji: String, onClick: () -> Unit): Button {
        val button = Button(this)
        button.text = emoji
        button.textSize = 12f
        button.setTextColor(Color.parseColor("#00E5FF"))
        button.isAllCaps = true
        button.letterSpacing = 0.05f
        button.minWidth = 0
        button.minHeight = 0
        button.setPadding(dp(16), dp(10), dp(16), dp(10))

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(4).toFloat()
        bg.setColor(Color.parseColor("#E60A0A0F"))
        bg.setStroke(dp(1), Color.parseColor("#00E5FF"))
        button.background = bg
        button.elevation = dp(4).toFloat()

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        button.layoutParams = lp

        button.setOnClickListener { onClick() }
        return button
    }

    private fun getScreenSize(): Pair<Int, Int> {
        val bounds = windowManager?.currentWindowMetrics?.bounds
        return if (bounds != null) {
            Pair(bounds.width(), bounds.height())
        } else {
            Pair(1080, 1920)
        }
    }

    private fun positionToolbar(toolbar: View, rect: Rect) {
        toolbar.post {
            val (screenWidth, screenHeight) = getScreenSize()
            val toolbarWidth = toolbar.width
            val toolbarHeight = toolbar.height
            val gap = dp(16)

            val spaceRight = screenWidth - rect.right
            val spaceLeft = rect.left
            val spaceBelow = screenHeight - rect.bottom
            val spaceAbove = rect.top

            var left: Int
            var top: Int

            if (spaceRight >= toolbarWidth + gap) {
                left = rect.right + gap
                top = (rect.top + rect.bottom) / 2 - toolbarHeight / 2
            } else if (spaceLeft >= toolbarWidth + gap) {
                left = rect.left - toolbarWidth - gap
                top = (rect.top + rect.bottom) / 2 - toolbarHeight / 2
            } else if (spaceBelow >= toolbarHeight + gap) {
                left = rect.left
                top = rect.bottom + gap
            } else if (spaceAbove >= toolbarHeight + gap) {
                left = rect.left
                top = rect.top - toolbarHeight - gap
            } else {
                left = rect.left
                top = rect.bottom + gap
            }

            left = left.coerceIn(0, (screenWidth - toolbarWidth).coerceAtLeast(0))
            top = top.coerceIn(0, (screenHeight - toolbarHeight).coerceAtLeast(0))

            val lp = toolbar.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.TOP or Gravity.START
            lp.leftMargin = left
            lp.topMargin = top
            toolbar.layoutParams = lp
        }
    }

    private fun openSelectionScreen() {
        if (selectionContainer != null) return

        val container = FrameLayout(this)

        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.visibility = View.GONE

        val toolbarBg = GradientDrawable()
        toolbarBg.shape = GradientDrawable.RECTANGLE
        toolbarBg.cornerRadius = dp(6).toFloat()
        toolbarBg.setColor(Color.parseColor("#DD08080C"))
        toolbarBg.setStroke(dp(1), Color.parseColor("#4D00E5FF"))
        toolbar.background = toolbarBg
        toolbar.setPadding(dp(6), dp(6), dp(6), dp(6))

        val overlay = SelectionOverlayView(this) { rect ->
            if (rect == null) {
                toolbar.visibility = View.GONE
                lastRect = null
            } else {
                toolbar.visibility = View.VISIBLE
                lastRect = rect
                positionToolbar(toolbar, rect)
            }
        }

        container.addView(
            overlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val closeButton = createIconButton("\u2715") {
            closeSelectionScreen()
        }

        val zoomButton = createIconButton("Просмотр") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> showZoomScreen(bitmap) } }
        }

        val textButton = createIconButton("Текст") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> recognizeAndShowText(bitmap) } }
        }

        val translateButton = createIconButton("Перевод") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> translateAndShowText(bitmap) } }
        }

        val shareButton = createIconButton("Поделиться") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> shareScreenshot(bitmap) } }
        }

        toolbar.addView(closeButton)
        toolbar.addView(zoomButton)
        toolbar.addView(textButton)
        toolbar.addView(translateButton)
        toolbar.addView(shareButton)

        val toolbarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        toolbarParams.gravity = Gravity.TOP or Gravity.START
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
        lastRect = null
    }

    private fun captureCropped(rect: Rect, onReady: (Bitmap) -> Unit) {
        closeSelectionScreen()

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )
                    screenshot.hardwareBuffer.close()

                    if (hardwareBitmap == null) {
                        showNotification("Ошибка", "Не удалось получить скриншот")
                        return
                    }

                    val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap.recycle()

                    val safeLeft = rect.left.coerceIn(0, softwareBitmap.width)
                    val safeTop = rect.top.coerceIn(0, softwareBitmap.height)
                    val safeRight = rect.right.coerceIn(0, softwareBitmap.width)
                    val safeBottom = rect.bottom.coerceIn(0, softwareBitmap.height)
                    val w = safeRight - safeLeft
                    val h = safeBottom - safeTop

                    if (w <= 0 || h <= 0) {
                        showNotification("Ошибка", "Пустая область выделения")
                        return
                    }

                    val cropped = Bitmap.createBitmap(softwareBitmap, safeLeft, safeTop, w, h)
                    onReady(cropped)
                }

                override fun onFailure(errorCode: Int) {
                    showNotification("Ошибка скриншота", "Код=" + errorCode)
                }
            }
        )
    }

    private fun ensureTessData(onReady: () -> Unit) {
        Thread {
            try {
                val tessDir = File(filesDir, "tesseract")
                val tessDataDir = File(tessDir, "tessdata")
                if (!tessDataDir.exists()) tessDataDir.mkdirs()

                val names = listOf("rus.traineddata", "eng.traineddata", "est.traineddata")
                for (name in names) {
                    val target = File(tessDataDir, name)
                    if (!target.exists() || target.length() == 0L) {
                        val url = URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/" + name)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        connection.connect()
                        connection.inputStream.use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                Handler(Looper.getMainLooper()).post { onReady() }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    showNotification("Ошибка загрузки языковых данных", e.message ?: "проверь интернет")
                }
            }
        }.start()
    }

    private fun runOcr(bitmap: Bitmap, onResult: (String) -> Unit) {
        showNotification("Распознавание", "Обрабатываю область...")
        ensureTessData {
            Thread {
                val dataPath = File(filesDir, "tesseract").absolutePath
                val tess = TessBaseAPI()
                val ok = tess.init(dataPath, "rus+eng+est")
                if (!ok) {
                    tess.recycle()
                    Handler(Looper.getMainLooper()).post {
                        showNotification("Ошибка", "Не удалось запустить распознавание")
                    }
                    return@Thread
                }
                tess.setImage(bitmap)
                val text = tess.utF8Text ?: ""
                tess.recycle()
                Handler(Looper.getMainLooper()).post { onResult(text) }
            }.start()
        }
    }

    private fun recognizeAndShowText(bitmap: Bitmap) {
        runOcr(bitmap) { text ->
            if (text.isNotBlank()) {
                showTextResultScreen(text.trim())
            } else {
                showNotification("Текст не найден", "На выделенной области нет текста")
            }
        }
    }

    private fun translateAndShowText(bitmap: Bitmap) {
        runOcr(bitmap) { rawText ->
            val text = rawText.trim()
            if (text.isEmpty()) {
                showNotification("Текст не найден", "На выделенной области нет текста")
                return@runOcr
            }

            val languageIdentifier = LanguageIdentification.getClient()
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode == "ru") {
                        showRussianTargetChoice(text)
                        return@addOnSuccessListener
                    }

                    val sourceLang = if (languageCode == "und") {
                        TranslateLanguage.ENGLISH
                    } else {
                        TranslateLanguage.fromLanguageTag(languageCode) ?: TranslateLanguage.ENGLISH
                    }

                    translateText(text, sourceLang, TranslateLanguage.RUSSIAN) { translated ->
                        showTextResultScreen(translated)
                    }
                }
                .addOnFailureListener { e ->
                    showNotification("Ошибка определения языка", e.message ?: "неизвестно")
                }
        }
    }

    private fun translateText(text: String, sourceLang: String, targetLang: String, onResult: (String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated -> onResult(translated) }
                    .addOnFailureListener { e ->
                        showNotification("Ошибка перевода", e.message ?: "неизвестно")
                    }
            }
            .addOnFailureListener { e ->
                showNotification("Ошибка загрузки модели", e.message ?: "нужен интернет")
            }
    }

    private fun showRussianTargetChoice(text: String) {
        val container = FrameLayout(this)
        container.setBackgroundColor(Color.TRANSPARENT)
        container.isClickable = true
        container.setOnClickListener {
            windowManager?.removeView(container)
        }

        val (screenWidth, screenHeight) = getScreenSize()

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true

        val cardBg = GradientDrawable()
        cardBg.shape = GradientDrawable.RECTANGLE
        cardBg.cornerRadius = dp(8).toFloat()
        cardBg.setColor(Color.parseColor("#F00C0C11"))
        cardBg.setStroke(dp(1), Color.parseColor("#8000E5FF"))
        card.background = cardBg
        card.setPadding(dp(20), dp(20), dp(20), dp(16))

        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this)
        textView.text = text
        textView.setTextColor(Color.WHITE)
        textView.textSize = 17f
        textView.setTextIsSelectable(true)
        scrollView.addView(textView)

        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (screenHeight * 0.4f).toInt()
        )
        card.addView(scrollView, scrollParams)

        val buttonsRow = LinearLayout(this)
        buttonsRow.orientation = LinearLayout.HORIZONTAL
        buttonsRow.gravity = Gravity.END
        val rowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLp.topMargin = dp(16)
        buttonsRow.layoutParams = rowLp

        val enButton = createIconButton("English") {
            windowManager?.removeView(container)
            translateText(text, TranslateLanguage.RUSSIAN, TranslateLanguage.ENGLISH) { translated ->
                showTextResultScreen(translated)
            }
        }
        val estButton = createIconButton("Eesti") {
            windowManager?.removeView(container)
            translateText(text, TranslateLanguage.RUSSIAN, "et") { translated ->
                showTextResultScreen(translated)
            }
        }
        val closeButton = createIconButton("Закрыть") {
            windowManager?.removeView(container)
        }
        buttonsRow.addView(enButton)
        buttonsRow.addView(estButton)
        buttonsRow.addView(closeButton)
        card.addView(buttonsRow)

        val cardParams = FrameLayout.LayoutParams(
            (screenWidth * 0.8f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.gravity = Gravity.CENTER
        container.addView(card, cardParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(container, params)
    }

    private fun showTextResultScreen(text: String) {
        val container = FrameLayout(this)
        container.setBackgroundColor(Color.TRANSPARENT)
        container.isClickable = true
        container.setOnClickListener {
            windowManager?.removeView(container)
        }

        val (screenWidth, screenHeight) = getScreenSize()

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true

        val cardBg = GradientDrawable()
        cardBg.shape = GradientDrawable.RECTANGLE
        cardBg.cornerRadius = dp(8).toFloat()
        cardBg.setColor(Color.parseColor("#F00C0C11"))
        cardBg.setStroke(dp(1), Color.parseColor("#8000E5FF"))
        card.background = cardBg
        card.setPadding(dp(20), dp(20), dp(20), dp(16))

        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this)
        textView.text = text
        textView.setTextColor(Color.WHITE)
        textView.textSize = 17f
        textView.setTextIsSelectable(true)
        scrollView.addView(textView)

        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (screenHeight * 0.45f).toInt()
        )
        card.addView(scrollView, scrollParams)

        val buttonsRow = LinearLayout(this)
        buttonsRow.orientation = LinearLayout.HORIZONTAL
        buttonsRow.gravity = Gravity.END
        val rowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLp.topMargin = dp(16)
        buttonsRow.layoutParams = rowLp

        val copyButton = createIconButton("Скопировать") {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
            showNotification("Скопировано", "Текст в буфере обмена")
        }
        val closeButton = createIconButton("Закрыть") {
            windowManager?.removeView(container)
        }
        buttonsRow.addView(copyButton)
        buttonsRow.addView(closeButton)
        card.addView(buttonsRow)

        val cardParams = FrameLayout.LayoutParams(
            (screenWidth * 0.8f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.gravity = Gravity.CENTER
        container.addView(card, cardParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(container, params)
    }

    private fun shareScreenshot(bitmap: Bitmap) {
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_image.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/png"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val chooser = Intent.createChooser(intent, "Поделиться")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
        } catch (e: Exception) {
            showNotification("Ошибка", e.message ?: "не удалось поделиться")
        }
    }

    private fun addCorner(root: FrameLayout, isTop: Boolean, isLeft: Boolean, color: Int) {
        val len = dp(26)
        val thickness = dp(3)
        val margin = dp(24)

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

    private fun showZoomScreen(bitmap: Bitmap) {
        val container = FrameLayout(this)
        container.setBackgroundColor(Color.parseColor("#F008080C"))

        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        container.addView(
            imageView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val cyan = Color.parseColor("#00E5FF")
        addCorner(container, true, true, cyan)
        addCorner(container, true, false, cyan)
        addCorner(container, false, true, cyan)
        addCorner(container, false, false, cyan)

        val closeButton = createIconButton("Закрыть") {
            windowManager?.removeView(container)
        }

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        btnParams.bottomMargin = dp(40)
        container.addView(closeButton, btnParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(container, params)
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
