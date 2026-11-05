package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var webContainer: FrameLayout
    private lateinit var markerView: MarkerView

    private lateinit var categorySpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner

    private var popupWebView: WebView? = null

    private var running = false
    private var scanInProgress = false
    private var scanCount = 0

    private val handler = Handler(Looper.getMainLooper())

    private val basicTargets = arrayOf(
        "Niebieski",
        "Fioletowy",
        "Szary",
        "Zielony",
        "Czerwony"
    )

    private val mystrasTargets = arrayOf(
        "Golden Shams",
        "Mystras Serpentfish",
        "Akduff the Multiped"
    )

    private val intervalValues = intArrayOf(
        10, 20, 30, 45, 60, 90, 120
    )

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            if (!scanInProgress) {
                scanPage()
            }

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }

        root.addView(title)

        categorySpinner = Spinner(this)

        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Ryby podstawowe", "Ryby Mystras")
        )

        root.addView(categorySpinner)

        val targetLabel = TextView(this).apply {
            text = "WYBRANY CEL:"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(8, 5, 8, 2)
        }

        root.addView(targetLabel)

        targetSpinner = Spinner(this)
        root.addView(targetSpinner)

        intervalSpinner = Spinner(this)

        intervalSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                "10 sekund",
                "20 sekund",
                "30 sekund",
                "45 sekund",
                "60 sekund",
                "90 sekund",
                "120 sekund"
            )
        )

        intervalSpinner.setSelection(2)
        root.addView(intervalSpinner)

        val zoomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(4, 5, 4, 5)
        }

        val zoomOut = Button(this).apply {
            text = "−"
            textSize = 20f
            setOnClickListener {
                activeWebView()?.zoomOut()
            }
        }

        val zoomReset = Button(this).apply {
            text = "100%"
            setOnClickListener {
                resetZoom()
            }
        }

        val zoomIn = Button(this).apply {
            text = "+"
            textSize = 20f
            setOnClickListener {
                activeWebView()?.zoomIn()
            }
        }

        zoomPanel.addView(
            zoomOut,
            LinearLayout.LayoutParams(0, 48, 1f)
        )

        zoomPanel.addView(
            zoomReset,
            LinearLayout.LayoutParams(0, 48, 1f)
        )

        zoomPanel.addView(
            zoomIn,
            LinearLayout.LayoutParams(0, 48, 1f)
        )

        root.addView(zoomPanel)

        status = TextView(this).apply {
            text = "Status: gotowy"
            textSize = 13f
            setTextColor(Color.GREEN)
            setPadding(8, 5, 8, 5)
        }

        root.addView(status)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val startButton = Button(this).apply {
            text = "START"
            setOnClickListener {
                startAnalysis()
            }
        }

        val stopButton = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                stopAnalysis()
            }
        }

        buttons.addView(
            startButton,
            LinearLayout.LayoutParams(0, 52, 1f)
        )

        buttons.addView(
            stopButton,
            LinearLayout.LayoutParams(0, 52, 1f)
        )

        root.addView(buttons)

        webContainer = FrameLayout(this)

        webView = WebView(this)
        configureWebView(webView)

        webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        markerView = MarkerView(this)

        markerView.isClickable = false
        markerView.isFocusable = false
        markerView.setOnTouchListener { _, _ -> false }

        webContainer.addView(
            markerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            webContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        scanStatus = TextView(this).apply {
            text = "Analiza: oczekiwanie na START"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
        }

        root.addView(scanStatus)

        setContentView(root)

        updateTargets()

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateTargets()

                    if (::markerView.isInitialized) {
                        markerView.clearPoint()
                    }

                    scanStatus.text =
                        if (position == 0) {
                            "Tryb: RYBY PODSTAWOWE"
                        } else {
                            "Tryb: RYBY MYSTRAS"
                        }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        webView.loadUrl("https://warofdragons.com/")
    }

    private fun updateTargets() {
        val items =
            if (categorySpinner.selectedItemPosition == 0) {
                basicTargets
            } else {
                mystrasTargets
            }

        targetSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            items
        )
    }

    private fun activeWebView(): WebView? {
        return popupWebView ?: webView
    }

    private fun resetZoom() {
        val active = activeWebView() ?: return

        if (active.scale > 0f) {
            active.zoomBy(1f / active.scale)
        }

        active.settings.textZoom = 100

        status.text = "Status: powiększenie przywrócone"
    }

    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        view.setBackgroundColor(Color.BLACK)

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)
                status.text = "Status: strona załadowana"
            }
        }

        view.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {

                val popup = WebView(this@MainActivity)

                configurePopupWebView(popup)

                popupWebView = popup

                webContainer.removeView(webView)

                webContainer.addView(
                    popup,
                    0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                val transport =
                    resultMsg?.obj as? WebView.WebViewTransport
                        ?: return false

                transport.webView = popup
                resultMsg.sendToTarget()

                status.text = "Status: otwarto nowe okno"

                return true
            }
        }
    }
        private fun configurePopupWebView(popup: WebView) {
        popup.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        popup.setBackgroundColor(Color.BLACK)

        popup.webViewClient = object : WebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                status.text = "Status: okno gry załadowane"
            }
        }

        popup.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                closePopup()
            }
        }
    }

    private fun closePopup() {
        val popup = popupWebView ?: return

        webContainer.removeView(popup)

        popup.stopLoading()
        popup.destroy()

        popupWebView = null

        webContainer.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        status.text = "Status: powrót do głównego okna"
    }

    private fun startAnalysis() {
        if (running) return

        running = true
        scanCount = 0

        markerView.clearPoint()

        status.text = "Status: analiza uruchomiona"

        scanStatus.text = "Analiza: oczekiwanie na pierwszy skan"

        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)

        scanPage()
    }

    private fun stopAnalysis() {
        running = false

        handler.removeCallbacks(scanRunnable)

        status.text = "Status: zatrzymano"
        scanStatus.text = "Analiza zatrzymana"

        markerView.clearPoint()
    }

    private fun selectedInterval(): Int {
        val position = intervalSpinner.selectedItemPosition
            .coerceIn(0, intervalValues.lastIndex)

        return intervalValues[position] * 1000
    }

    private fun scanPage() {
        if (!running || scanInProgress) return

        val active = activeWebView()

        if (active == null || active.width <= 0 || active.height <= 0) {
            scanStatus.text = "Analiza: ekran gry nie jest gotowy"
            return
        }

        scanInProgress = true
        scanCount++

        val bitmap = try {
            Bitmap.createBitmap(
                active.width,
                active.height,
                Bitmap.Config.ARGB_8888
            ).also { image ->
                val canvas = Canvas(image)
                active.draw(canvas)
            }
        } catch (e: Exception) {
            scanInProgress = false
            scanStatus.text = "Analiza: błąd obrazu: ${e.message}"
            return
        }

        val category = categorySpinner.selectedItemPosition
        val target = targetSpinner.selectedItem?.toString() ?: ""

        Thread {
            val result = try {
                if (category == 0) {
                    analyzeBitmap(bitmap, target)
                } else {
                    analyzeMystras(bitmap, target)
                }
            } catch (e: Exception) {
                AnalysisResult(
                    emptyList(),
                    "Błąd analizy: ${e.message}"
                )
            } finally {
                bitmap.recycle()
            }

            runOnUiThread {
                scanInProgress = false

                if (!running) return@runOnUiThread

                if (category == 0) {
                    showBasicResult(result)
                } else {
                    markerView.clearPoint()

                    scanStatus.text =
                        "MYSTRAS: ${result.message}\n" +
                        "Automatyczne rozpoznawanie Mystras nie jest jeszcze aktywne."
                }
            }
        }.start()
    }

    private fun showBasicResult(result: AnalysisResult) {
        val target = targetSpinner.selectedItem?.toString() ?: ""

        val matching = result.detections.filter {
            it.colorName == target
        }

        val report = StringBuilder()

        report.append("TRYB: RYBY PODSTAWOWE\n")
        report.append("WYBRANY KOLOR: $target\n")
        report.append("OBRAZ: ${result.imageWidth}x${result.imageHeight}\n")
        report.append("WYKRYTE OBSZARY: ${result.detections.size}\n")
        report.append("OBSZARY WYBRANEGO KOLORU: ${matching.size}\n\n")

        if (matching.isEmpty()) {
            markerView.clearPoint()

            report.append("NIE ZNALEZIONO OBSZARU WYBRANEGO KOLORU.\n")
            report.append(result.message)

        } else {
            val selected = matching.random()

            markerView.setPoint(
                selected.centerX.toFloat(),
                selected.centerY.toFloat()
            )

            report.append("LOSOWO WYBRANE WYKRYCIE:\n")
            report.append("KOLOR: ${selected.colorName}\n")
            report.append("POZYCJA: X=${selected.centerX}, Y=${selected.centerY}\n")
            report.append("ROZMIAR: ${selected.width}x${selected.height}\n")
            report.append("PIKSELE OBSZARU: ${selected.pixelCount}\n\n")

            report.append("POZOSTAŁE WYKRYCIA WYBRANEGO KOLORU:\n")

            matching.forEachIndexed { index, detection ->
                report.append(
                    "${index + 1}. ${detection.colorName} " +
                    "X=${detection.centerX}, Y=${detection.centerY} " +
                    "${detection.width}x${detection.height} " +
                    "piksele=${detection.pixelCount}\n"
                )
            }

            report.append("\nCZERWONY ZNACZNIK OZNACZA WYBRANY OBSZAR.")
            report.append("\nBOT NIE KLIKA W GRZE.")
        }

        scanStatus.text = report.toString()
    }

    private data class Detection(
        val colorId: Int,
        val colorName: String,
        val centerX: Int,
        val centerY: Int,
        val width: Int,
        val height: Int,
        val pixelCount: Int
    )

    private data class AnalysisResult(
        val detections: List<Detection>,
        val message: String,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0
    )

    private fun analyzeMystras(
        bitmap: Bitmap,
        target: String
    ): AnalysisResult {
        return AnalysisResult(
            emptyList(),
            "Wybrany cel: $target. Rozpoznawanie obrazów Mystras wymaga osobnego wzorca."
        )
    }

    private fun analyzeBitmap(
        bitmap: Bitmap,
        target: String
    ): AnalysisResult {

        val width = bitmap.width
        val height = bitmap.height

        val step = 2

        val gridWidth = (width + step - 1) / step
        val gridHeight = (height + step - 1) / step

        val total = gridWidth * gridHeight

        val colorGrid = IntArray(total)

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {

                val x = min(gx * step, width - 1)
                val y = min(gy * step, height - 1)

                val pixel = bitmap.getPixel(x, y)

                colorGrid[gy * gridWidth + gx] =
                    classifyColor(pixel)
            }
        }

        val visited = BooleanArray(total)
        val queue = ArrayDeque<Int>()

        val detections = mutableListOf<Detection>()

        for (start in 0 until total) {

            val colorId = colorGrid[start]

            if (colorId == 0 || visited[start]) continue

            visited[start] = true
            queue.clear()
            queue.add(start)

            var count = 0

            var minX = gridWidth
            var minY = gridHeight
            var maxX = 0
            var maxY = 0

            while (queue.isNotEmpty()) {

                val current = queue.removeFirst()

                val gx = current % gridWidth
                val gy = current / gridWidth

                count++

                minX = min(minX, gx)
                minY = min(minY, gy)
                maxX = max(maxX, gx)
                maxY = max(maxY, gy)

                val neighbors = intArrayOf(
                    if (gx > 0) current - 1 else -1,
                    if (gx < gridWidth - 1) current + 1 else -1,
                    if (gy > 0) current - gridWidth else -1,
                    if (gy < gridHeight - 1) current + gridWidth else -1
                )

                for (neighbor in neighbors) {

                    if (neighbor < 0) continue
                    if (visited[neighbor]) continue
                    if (colorGrid[neighbor] != colorId) continue

                    visited[neighbor] = true
                    queue.add(neighbor)
                }
            }

            val objectWidth = (maxX - minX + 1) * step
            val objectHeight = (maxY - minY + 1) * step

            val ratio =
                objectWidth.toFloat() /
                objectHeight.coerceAtLeast(1).toFloat()

            if (
                count >= 5 &&
                count <= 1800 &&
                ratio in 0.45f..2.2f
            ) {

                val centerX =
                    ((minX + maxX) / 2) * step

                val centerY =
                    ((minY + maxY) / 2) * step

                detections.add(
                    Detection(
                        colorId = colorId,
                        colorName = colorName(colorId),
                        centerX = centerX,
                        centerY = centerY,
                        width = objectWidth,
                        height = objectHeight,
                        pixelCount = count * step * step
                    )
                )
            }
        }

        return AnalysisResult(
            detections = detections,
            message = "Analiza zakończona.",
            imageWidth = width,
            imageHeight = height
        )
    }
        private fun classifyColor(pixel: Int): Int {

        val hsv = FloatArray(3)

        Color.colorToHSV(pixel, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        // SZARY
        if (saturation < 0.20f && value > 0.30f) {
            return 3
        }

        if (saturation < 0.25f) {
            return 0
        }

        // NIEBIESKI
        if (hue in 185f..245f) {
            return 1
        }

        // FIOLETOWY
        if (hue in 246f..305f) {
            return 2
        }

        // ZIELONY
        if (hue in 65f..175f) {
            return 4
        }

        // CZERWONY
        if (hue <= 15f || hue >= 345f) {
            return 5
        }

        return 0
    }

    private fun colorName(colorId: Int): String {
        return when (colorId) {
            1 -> "Niebieski"
            2 -> "Fioletowy"
            3 -> "Szary"
            4 -> "Zielony"
            5 -> "Czerwony"
            else -> "Nieznany"
        }
    }

    private inner class MarkerView(context: Context) :
        View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var pointX = -1f
        private var pointY = -1f

        fun setPoint(x: Float, y: Float) {
            pointX = x
            pointY = y
            invalidate()
        }

        fun clearPoint() {
            pointX = -1f
            pointY = -1f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (pointX < 0f || pointY < 0f) return

            paint.color = Color.RED
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE

            // Czerwone kółko
            canvas.drawCircle(
                pointX,
                pointY,
                12f,
                paint
            )

            // Pozioma linia
            canvas.drawLine(
                pointX - 18f,
                pointY,
                pointX + 18f,
                pointY,
                paint
            )

            // Pionowa linia
            canvas.drawLine(
                pointX,
                pointY - 18f,
                pointX,
                pointY + 18f,
                paint
            )

            paint.style = Paint.Style.FILL
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        if (popupWebView != null) {
            closePopup()
            return
        }

        val active = activeWebView()

        if (active != null && active.canGoBack()) {
            active.goBack()
            return
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        running = false

        handler.removeCallbacksAndMessages(null)

        try {
            popupWebView?.stopLoading()
            popupWebView?.destroy()
            popupWebView = null

            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
