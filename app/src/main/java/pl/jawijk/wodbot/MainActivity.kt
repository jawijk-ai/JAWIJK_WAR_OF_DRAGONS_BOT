package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var webContainer: FrameLayout
    private lateinit var markerView: MarkerView

    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    private var popupWebView: WebView? = null

    private var running = false
    private var scanGeneration = 0L
    private var scanInProgress = false
    private var scanCount = 0

    private val handler = Handler(Looper.getMainLooper())

    private val basicTargets = listOf(
        "Niebieski",
        "Fioletowy",
        "Szary",
        "Zielony",
        "Czerwony"
    )

    private val mystrasTargets = listOf(
        "Golden Shams",
        "Coastal Shamsu",
        "Mystras Serpentfish"
    )

    private val intervalValues = listOf(
        10, 20, 30, 45, 60, 90, 120
    )

    private val mystrasTemplates = mutableMapOf<String, Bitmap>()

    private val referenceWidth = 1080f
    private val referenceHeight = 1034f

    private val searchX1 = 200f
    private val searchY1 = 200f
    private val searchX2 = 800f
    private val searchY2 = 600f

    private data class Detection(
        val x: Int,
        val y: Int,
        val radius: Int,
        val name: String,
        val score: Float = 0f
    )

    private data class AnalysisResult(
        val detections: List<Detection>,
        val message: String,
        val imageWidth: Int,
        val imageHeight: Int
    )

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running && !scanInProgress) {
                scanPage()
            }

            if (running) {
                handler.postDelayed(
                    this,
                    selectedInterval() * 1000L
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildInterface()
        configureWebView()
        loadMystrasTemplates()

        webView.loadUrl("https://warofdragons.com/")
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
            setPadding(8, 8, 8, 8)
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 12)
        }
        root.addView(title)

        categorySpinner = Spinner(this)
        categorySpinner.adapter = makeAdapter(
            listOf("Ryby podstawowe", "Ryby Mystras")
        )
        root.addView(categorySpinner)

        targetSpinner = Spinner(this)
        targetSpinner.adapter = makeAdapter(basicTargets)
        root.addView(targetSpinner)

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val targets =
                        if (position == 1) {
                            mystrasTargets
                        } else {
                            basicTargets
                        }

                    targetSpinner.adapter = makeAdapter(targets)
                    markerView.clear()

                    scanStatus.text =
                        "Wybierz surowiec i uruchom analizę."
                }
            }

        intervalSpinner = Spinner(this)
        intervalSpinner.adapter = makeAdapter(
            intervalValues.map { "$it sekund" }
        )
        intervalSpinner.setSelection(1)
        root.addView(intervalSpinner)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "START"
            setOnClickListener {
                startScanning()
            }
        }

        val stopButton = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                stopScanning()
            }
        }

        buttons.addView(
            startButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        buttons.addView(
            stopButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(buttons)

        status = TextView(this).apply {
            text = "Status: gotowy"
            textSize = 13f
            setTextColor(Color.GREEN)
            setPadding(4, 6, 4, 6)
        }
        root.addView(status)

        scanStatus = TextView(this).apply {
            text = "Analiza jeszcze nieuruchomiona."
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(4, 4, 4, 8)
        }
        root.addView(scanStatus)

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val zoomOut = Button(this).apply {
            text = "−"
            setOnClickListener {
                webView.zoomOut()
            }
        }

        val zoomIn = Button(this).apply {
            text = "+"
            setOnClickListener {
                webView.zoomIn()
            }
        }

        zoomRow.addView(zoomOut)
        zoomRow.addView(zoomIn)
        root.addView(zoomRow)

        webContainer = FrameLayout(this)

        webView = WebView(this)

        webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        markerView = MarkerView(this).apply {
            isClickable = false
            isFocusable = false
        }

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

        val logButton = Button(this).apply {
            text = "POKAŻ / UKRYJ LOG"
        }

        logText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(6, 6, 6, 6)
        }

        logScroll = ScrollView(this).apply {
            visibility = View.GONE
            addView(logText)
        }

        logButton.setOnClickListener {
            logScroll.visibility =
                if (logScroll.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        root.addView(logButton)

        root.addView(
            logScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                110
            )
        )

        setContentView(root)
    }

    private fun makeAdapter(
        items: List<String>
    ): ArrayAdapter<String> {
        return ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            items
        ).also {
            it.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }

        CookieManager.getInstance().setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {

                val popup = WebView(this@MainActivity)

                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.databaseEnabled = true
                popup.settings.loadsImagesAutomatically = true
                popup.settings.setSupportMultipleWindows(true)

                popup.webViewClient = WebViewClient()
                popup.webChromeClient = this

                popupWebView?.let { old ->
                    webContainer.removeView(old)
                    old.destroy()
                }

                popupWebView = popup

                webContainer.addView(
                    popup,
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

                addLog("Otwarto dodatkowe okno gry.")
                return true
            }
        }
    }

    private fun selectedInterval(): Int {
        return intervalValues.getOrElse(
            intervalSpinner.selectedItemPosition
        ) {
            20
        }
    }

    private fun selectedTarget(): String {
        return targetSpinner.selectedItem?.toString() ?: ""
    }

    private fun isMystrasMode(): Boolean {
        return categorySpinner.selectedItemPosition == 1
    }

    private fun loadMystrasTemplates() {
        val files = mapOf(
            "Golden Shams" to "golden_shams.png",
            "Coastal Shamsu" to "coastal_shamsu.png",
            "Mystras Serpentfish" to "mystras_serpentfish.png"
        )

        for ((name, file) in files) {
            try {
                assets.open(file).use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)

                    if (bitmap != null) {
                        mystrasTemplates[name] = bitmap
                        addLog("Wczytano wzorzec: $name")
                    } else {
                        addLog("BŁĄD: nie można odczytać $file")
                    }
                }
            } catch (e: Exception) {
                addLog("BRAK PLIKU $file: ${e.message}")
            }
        }

        if (mystrasTemplates.size == 3) {
            addLog("Wszystkie 3 wzorce Mystras są gotowe.")
        } else {
            addLog(
                "UWAGA: wczytano ${mystrasTemplates.size}/3 wzorców Mystras."
            )
        }
    }

    private fun startScanning() {
        if (running) return

        if (isMystrasMode() && mystrasTemplates.size != 3) {
            scanStatus.text =
                "BŁĄD: brakuje zdjęć wzorcowych w folderze assets."
            return
        }

        scanGeneration++
        running = true

        status.text = "Status: analizator działa"
        status.setTextColor(Color.GREEN)

        addLog("Uruchomiono analizator.")

        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
    }

    private fun stopScanning() {
        scanGeneration++
        running = false
        handler.removeCallbacks(scanRunnable)

        status.text = "Status: zatrzymany"
        status.setTextColor(Color.RED)

        scanStatus.text = "Analiza zatrzymana."
        addLog("Zatrzymano analizator.")
    }

    private fun scanPage() {
        if (scanInProgress) return

        val active = popupWebView ?: webView

        if (active.width <= 0 || active.height <= 0) {
            scanStatus.text = "Brak widocznego obrazu gry."
            return
        }

        scanInProgress = true
        scanCount++

        scanStatus.text = "Skanowanie nr $scanCount..."

        val bitmap = try {
            Bitmap.createBitmap(
                active.width,
                active.height,
                Bitmap.Config.ARGB_8888
            ).also {
                active.draw(Canvas(it))
            }
        } catch (e: Exception) {
            scanInProgress = false
            scanStatus.text = "Błąd zrzutu ekranu: ${e.message}"
            return
        }

        val target = selectedTarget()
        val mystras = isMystrasMode()
        val generation = scanGeneration
        val scannedUrl = active.url
        val scannedScrollX = active.scrollX
        val scannedScrollY = active.scrollY

        Thread {
            val result = try {
                if (mystras) {
                    analyzeMystras(bitmap, target)
                } else {
                    analyzeBasic(bitmap, target)
                }
            } catch (e: Exception) {
                AnalysisResult(
                    emptyList(),
                    "Błąd analizy: ${e.message}",
                    bitmap.width,
                    bitmap.height
                )
            } finally {
                bitmap.recycle()
            }

            runOnUiThread {
                scanInProgress = false

                val valid = running && generation == scanGeneration &&
                    active === (popupWebView ?: webView) &&
                    active.width == result.imageWidth &&
                    active.height == result.imageHeight &&
                    active.url == scannedUrl &&
                    active.scrollX == scannedScrollX &&
                    active.scrollY == scannedScrollY &&
                    selectedTarget() == target && isMystrasMode() == mystras &&
                    !isFinishing && !isDestroyed

                if (valid) {
                    showResult(result, if (mystras) "Mystras" else "Ryby podstawowe")
                    val point = result.detections.firstOrNull()
                    if (point != null && point.x in 0 until active.width &&
                        point.y in 0 until active.height) {
                        
                        val targetX = point.x + active.scrollX
                        val targetY = point.y + active.scrollY

                        val now = android.os.SystemClock.uptimeMillis()
                        val down = android.view.MotionEvent.obtain(now, now,
                            android.view.MotionEvent.ACTION_DOWN,
                            targetX.toFloat(), targetY.toFloat(), 0)
                        val up = android.view.MotionEvent.obtain(now, now,
                            android.view.MotionEvent.ACTION_UP,
                            targetX.toFloat(), targetY.toFloat(), 0)
                        try {
                            active.dispatchTouchEvent(down)
                            active.dispatchTouchEvent(up)
                            addLog("Wysłano dotyk: X=$targetX, Y=$targetY")
                        } finally {
                            down.recycle()
                            up.recycle()
                        }
                    }
                }
            }
        }.start()
    }

    private fun analyzeMystras(
        bitmap: Bitmap,
        target: String
    ): AnalysisResult {

        val template = mystrasTemplates[target]
            ?: return AnalysisResult(
                emptyList(),
                "Nie znaleziono wzorca: $target",
                bitmap.width,
                bitmap.height
            )

        val sx = bitmap.width / referenceWidth
        val sy = bitmap.height / referenceHeight

        val left = (searchX1 * sx)
            .toInt().coerceIn(0, bitmap.width - 1)

        val top = (searchY1 * sy)
            .toInt().coerceIn(0, bitmap.height - 1)

        val right = (searchX2 * sx)
            .toInt().coerceIn(left + 1, bitmap.width)

        val bottom = (searchY2 * sy)
            .toInt().coerceIn(top + 1, bitmap.height)

        val detections = mutableListOf<Detection>()

        val scales = listOf(
            0.65f,
            0.80f,
            0.95f,
            1.10f,
            1.25f
        )

        val mask = createTemplateMask(template)

        if (mask.size < 12) {
            return AnalysisResult(
                emptyList(),
                "Wzorzec $target ma za mało kolorowych punktów.",
                bitmap.width,
                bitmap.height
            )
        }

        for (scale in scales) {
            val tw = (template.width * scale)
                .toInt().coerceAtLeast(8)

            val th = (template.height * scale)
                .toInt().coerceAtLeast(8)

            if (tw >= right - left || th >= bottom - top) {
                continue
            }

            val scaledPoints = mask.map { point ->
                TemplatePoint(
                    (point.x * scale).toInt(),
                    (point.y * scale).toInt(),
                    point.color
                )
            }

            var y = top

            while (y <= bottom - th) {
                var x = left

                while (x <= right - tw) {
                    val score = compareTemplate(
                        bitmap,
                        x,
                        y,
                        scaledPoints
                    )

                    if (score >= 0.70f) {
                        val centerX = x + tw / 2
                        val centerY = y + th / 2

                        val duplicate = detections.any {
                            abs(it.x - centerX) < tw / 2 &&
                            abs(it.y - centerY) < th / 2
                        }

                        if (!duplicate) {
                            detections.add(
                                Detection(
                                    centerX,
                                    centerY,
                                    max(tw, th) / 2,
                                    target,
                                    score
                                )
                            )
                        }
                    }

                    x += 4
                }

                y += 4
            }
        }

        val message = if (detections.isEmpty()) {
            "Mystras: nie znaleziono $target. " +
                "Obszar: $left,$top - $right,$bottom."
        } else {
            "Mystras: znaleziono ${detections.size} x $target."
        }

        return AnalysisResult(
            detections,
            message,
            bitmap.width,
            bitmap.height
        )
    }

    private data class TemplatePoint(
        val x: Int,
        val y: Int,
        val color: Int
    )

    private fun createTemplateMask(
        template: Bitmap
    ): List<TemplatePoint> {

        val points = mutableListOf<TemplatePoint>()

        val stepX = max(1, template.width / 35)
        val stepY = max(1, template.height / 35)

        var y = 0

        while (y < template.height) {
            var x = 0

            while (x < template.width) {
                val color = template.getPixel(x, y)

                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))

                val saturation = if (maxC == 0) {
                    0f
                } else {
                    (maxC - minC).toFloat() / maxC
                }

                if (saturation > 0.18f && maxC > 55) {
                    points.add(
                        TemplatePoint(x, y, color)
                    )
                }

                x += stepX
            }

            y += stepY
        }

        return points
    }

    private fun compareTemplate(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        points: List<TemplatePoint>
    ): Float {

        var matched = 0
        var checked = 0

        for (point in points) {
            val x = startX + point.x
            val y = startY + point.y

            if (x !in 0 until bitmap.width ||
                y !in 0 until bitmap.height
            ) {
                continue
            }

            val actual = bitmap.getPixel(x, y)
            val expected = point.color

            val dr = abs(
                Color.red(actual) - Color.red(expected)
            )

            val dg = abs(
                Color.green(actual) - Color.green(expected)
            )

            val db = abs(
                Color.blue(actual) - Color.blue(expected)
            )

            val distance = dr + dg + db

            if (distance < 180) {
                matched++
            }

            checked++
        }

        return if (checked == 0) {
            0f
        } else {
            matched.toFloat() / checked.toFloat()
        }
    }

    // --------------------------------------------------
    // ANALIZATOR RYB PODSTAWOWYCH (POPRAWIONY)
    // --------------------------------------------------

    private fun analyzeBasic(
        bitmap: Bitmap,
        target: String
    ): AnalysisResult {

        val step = 3

        val gridWidth =
            (bitmap.width + step - 1) / step

        val gridHeight =
            (bitmap.height + step - 1) / step

        val mask = BooleanArray(gridWidth * gridHeight)

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {

                val x = min(gx * step, bitmap.width - 1)
                val y = min(gy * step, bitmap.height - 1)

                val color = bitmap.getPixel(x, y)

                mask[gy * gridWidth + gx] =
                    classifyBasicColor(color) == target
            }
        }

        val visited = BooleanArray(mask.size)
        val detections = mutableListOf<Detection>()

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {

                val index = gy * gridWidth + gx

                if (!mask[index] || visited[index]) {
                    continue
                }

                val queue = ArrayDeque<Int>()

                queue.add(index)
                visited[index] = true

                var count = 0
                var sumX = 0
                var sumY = 0

                var minX = gx
                var maxX = gx
                var minY = gy
                var maxY = gy

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()

                    val cx = current % gridWidth
                    val cy = current / gridWidth

                    count++
                    sumX += cx
                    sumY += cy

                    minX = min(minX, cx)
                    maxX = max(maxX, cx)
                    minY = min(minY, cy)
                    maxY = max(maxY, cy)

                    for (dy in -1..1) {
                        for (dx in -1..1) {

                            if (dx == 0 && dy == 0) {
                                continue
                            }

                            val nx = cx + dx
                            val ny = cy + dy

                            if (nx !in 0 until gridWidth ||
                                ny !in 0 until gridHeight
                            ) {
                                continue
                            }

                            val ni = ny * gridWidth + nx

                            if (mask[ni] && !visited[ni]) {
                                visited[ni] = true
                                queue.add(ni)
                            }
                        }
                    }
                }

                val width = maxX - minX + 1
                val height = maxY - minY + 1

                val aspect =
                    width.toFloat() / height.toFloat()

                if (count in 15..180 && aspect in 0.8f..1.2f) {
                    val centerX = (sumX / count) * step
                    val centerY = (sumY / count) * step

                    val tooClose = detections.any {
                        abs(it.x - centerX) < 40 && abs(it.y - centerY) < 40
                    }

                    if (!tooClose) {
                        detections.add(
                            Detection(
                                x = centerX,
                                y = centerY,
                                radius = max(width, height) * step / 2,
                                name = target
                            )
                        )
                    }
                }
            }
        }

        val message = if (detections.isEmpty()) {
            "Ryby podstawowe: nie znaleziono koloru $target."
        } else {
            "Ryby podstawowe: znaleziono " +
                "${detections.size} obszarów: $target."
        }

        return AnalysisResult(
            detections,
            message,
            bitmap.width,
            bitmap.height
        )
    }

    private fun classifyBasicColor(
        color: Int
    ): String? {

        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        // Odrzucamy tylko całkowicie ciemne tło wody
        if (v < 0.10f) return null

        // Szary / Biały (pulsujące kółka jasne)
        if (s < 0.35f && v > 0.40f) {
            return "Szary"
        }

        if (s < 0.12f) return null

        // Czerwony
        if ((h <= 25f || h >= 325f) && s > 0.12f && v > 0.15f) {
            return "Czerwony"
        }

        // Fioletowy
        if (h in 225f..325f && s > 0.12f && v > 0.15f) {
            return "Fioletowy"
        }

        // Niebieski
        if (h in 160f..235f && s > 0.12f && v > 0.15f) {
            return "Niebieski"
        }

        // Zielony
        if (h in 45f..165f && s > 0.12f && v > 0.15f) {
            return "Zielony"
        }

        return null
    }

    private fun showResult(
        result: AnalysisResult,
        mode: String
    ) {

        markerView.setDetections(
            result.detections,
            result.imageWidth,
            result.imageHeight
        )

        scanStatus.text = result.message

        addLog(
            "Skan $scanCount | $mode | ${result.message}"
        )

        if (result.detections.isNotEmpty()) {

            val first = result.detections.first()

            addLog(
                "Pierwszy punkt: X=${first.x}, Y=${first.y}"
            )

            if (result.detections.size > 1) {
                addLog(
                    "Pozostałe punkty: " +
                        "${result.detections.size - 1}"
                )
            }
        }
    }

    private fun addLog(message: String) {

        if (!::logText.isInitialized) return

        runOnUiThread {

            val time = java.text.SimpleDateFormat(
                "HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())

            logText.append("[$time] $message\n")

            if (logText.lineCount > 300) {
                val all = logText.text.toString()
                logText.text = all.takeLast(12000)
            }

            if (::logScroll.isInitialized) {
                logScroll.post {
                    logScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    override fun onBackPressed() {

        val popup = popupWebView

        if (popup != null) {

            webContainer.removeView(popup)

            popup.stopLoading()
            popup.destroy()

            popupWebView = null

            addLog("Zamknięto dodatkowe okno.")
            return
        }

        if (webView.canGoBack()) {
            webView.goBack()
            return
        }

        super.onBackPressed()
    }

    override fun onDestroy() {

        running = false
        handler.removeCallbacksAndMessages(null)

        popupWebView?.let {
            webContainer.removeView(it)
            it.stopLoading()
            it.destroy()
        }

        popupWebView = null

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }

        mystrasTemplates.values.forEach {
            if (!it.isRecycled) {
                it.recycle()
            }
        }

        mystrasTemplates.clear()

        super.onDestroy()
    }

    private inner class MarkerView(
        context: android.content.Context
    ) : View(context) {

        private val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        private var detections: List<Detection> =
            emptyList()

        private var sourceWidth = 1
        private var sourceHeight = 1

        fun setDetections(
            newDetections: List<Detection>,
            imageWidth: Int,
            imageHeight: Int
        ) {
            detections = newDetections

            sourceWidth = max(1, imageWidth)
            sourceHeight = max(1, imageHeight)

            invalidate()
        }

        fun clear() {
            detections = emptyList()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (detections.isEmpty()) return

            val scaleX = width.toFloat() / sourceWidth.toFloat()
            val scaleY = height.toFloat() / sourceHeight.toFloat()

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f

            for (d in detections) {
                val cx = d.x * scaleX
                val cy = d.y * scaleY
                val r = d.radius * max(scaleX, scaleY)

                paint.color = when (d.name) {
                    "Czerwony" -> Color.RED
                    "Niebieski" -> Color.BLUE
                    "Zielony" -> Color.GREEN
                    "Fioletowy" -> Color.MAGENTA
                    "Szary" -> Color.GRAY
                    else -> Color.YELLOW
                }

                canvas.drawCircle(cx, cy, max(15f, r), paint)
            }
        }
    }
}
