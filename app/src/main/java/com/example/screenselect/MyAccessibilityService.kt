package com.example.screenselect

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
    private var lastRect: Rect? = null

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

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun createIconButton(emoji: String, onClick: () -> Unit): Button {
        val button = Button(this)
        button.text = emoji
        button.textSize = 18f
        button.setTextColor(Color.WHITE)
        button.isAllCaps = false
        button.minWidth = 0
        button.minHeight = 0
        button.setPadding(dp(14), dp(10), dp(14), dp(10))

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#E6222222"))
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
        toolbarBg.cornerRadius = dp(18).toFloat()
        toolbarBg.setColor(Color.parseColor("#33000000"))
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

        val redoButton = createIconButton("\u27F3") {
            overlay.clearSelection()
            toolbar.visibility = View.GONE
        }

        val zoomButton = createIconButton("\uD83D\uDD0D") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> showZoomScreen(bitmap) } }
        }

        val textButton = createIconButton("\uD83D\uDCCB") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> recognizeAndShowText(bitmap) } }
        }

        val translateButton = createIconButton("\uD83C\uDF10") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> translateAndShowText(bitmap) } }
        }

        val shareButton = createIconButton("\uD83D\uDCE4") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> shareScreenshot(bitmap) } }
        }

        val saveButton = createIconButton("\uD83D\uDCBE") {
            lastRect?.let { rect -> captureCropped(rect) { bitmap -> saveToGallery(bitmap) } }
        }

        toolbar.addView(closeButton)
        toolbar.addView(redoButton)
        toolbar.addView(zoomButton)
        toolbar.addView(textButton)
        toolbar.addView(translateButton)
        toolbar.addView(shareButton)
        toolbar.addView(saveButton)

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

                val names = listOf("rus.traineddata", "eng.traineddata")
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
                val ok = tess.init(dataPath, "rus+eng")
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
                        showTextResultScreen(text)
                        return@addOnSuccessListener
                    }

                    val sourceLang = if (languageCode == "und") {
                        TranslateLanguage.ENGLISH
                    } else {
                        TranslateLanguage.fromLanguageTag(languageCode) ?: TranslateLanguage.ENGLISH
                    }

                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(sourceLang)
                        .setTargetLanguage(TranslateLanguage.RUSSIAN)
                        .build()
                    val translator = Translation.getClient(options)
                    val conditions = DownloadConditions.Builder().build()

                    translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener {
                            translator.translate(text)
                                .addOnSuccessListener { translated ->
                                    showTextResultScreen(translated)
                                }
                                .addOnFailureListener { e ->
                                    showNotification("Ошибка перевода", e.message ?: "неизвестно")
                                }
                        }
                        .addOnFailureListener { e ->
                            showNotification("Ошибка загрузки модели", e.message ?: "нужен интернет")
                        }
                }
                .addOnFailureListener { e ->
                    showNotification("Ошибка определения языка", e.message ?: "неизвестно")
                }
        }
    }

    private fun showTextResultScreen(text: String) {
        val container = FrameLayout(this)
        container.setBackgroundColor(Color.parseColor("#EE000000"))

        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this)
        textView.text = text
        textView.setTextColor(Color.WHITE)
        textView.textSize = 18f
        textView.setPadding(dp(24), dp(24), dp(24), dp(24))
        scrollView.addView(textView)

        val scrollParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        scrollParams.bottomMargin = dp(90)
        container.addView(scrollView, scrollParams)

        val buttonsRow = LinearLayout(this)
        buttonsRow.orientation = LinearLayout.HORIZONTAL

        val copyButton = createIconButton("\uD83D\uDCCB Скопировать") {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
            showNotification("Скопировано", "Текст в буфере обмена")
        }
        val closeButton = createIconButton("\u2715 Закрыть") {
            windowManager?.removeView(container)
        }
        buttonsRow.addView(copyButton)
        buttonsRow.addView(closeButton)

        val rowParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        rowParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        rowParams.bottomMargin = dp(30)
        container.addView(buttonsRow, rowParams)

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

    private fun saveToGallery(bitmap: Bitmap) {
        try {
            val filename = "ScreenSelect_" + System.currentTimeMillis() + ".png"
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenSelect")

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                showNotification("Сохранено", "Скриншот сохранён в галерею")
            } else {
                showNotification("Ошибка", "Не удалось сохранить")
            }
        } catch (e: Exception) {
            showNotification("Ошибка сохранения", e.message ?: "неизвестно")
        }
    }

    private fun showZoomScreen(bitmap: Bitmap) {
        val container = FrameLayout(this)
        container.setBackgroundColor(Color.parseColor("#CC000000"))

        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        container.addView(
            imageView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val closeButton = createIconButton("\u2715 Закрыть") {
            windowManager?.removeView(container)
        }

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.gravity = Gravity.TOP or Gravity.END
        btnParams.topMargin = 60
        btnParams.rightMargin = 40
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
