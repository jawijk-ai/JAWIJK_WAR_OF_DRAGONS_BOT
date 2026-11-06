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
import android.graphics.drawable.GradientDrawable
import android.content.Context
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
    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var webContainer: FrameLayout
    private lateinit var markerView: MarkerView

    private lateinit var categorySpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner

    private lateinit var logScroll: ScrollView
    private lateinit var logButton: Button

    private var popupWebView: WebView? = null

    private var running = false
    private var scanInProgress = false
    private var scanCount = 0

    private val basicTargets = arrayOf(
        "Niebieski",
        "Fioletowy",
        "Szary",
        "Zielony",
        "Czerwony"
    )

    // DOKŁADNIE TRZY RYBY MYSTRAS
    private val mystrasTargets = arrayOf(
        "Golden Shams",
        "Coastal Shamsu",
        "Mystras Serpentfish"
    )

    // Kolory zapisane w formacie Clickermann BGR.
    private data class MystrasColor(
        val name: String,
        val code: Int,
        val tolerance: Int
    )

    private val mystrasColors = listOf(
        MystrasColor("Golden Shams", 45823, 20),
        MystrasColor("Coastal Shamsu", 10258836, 15),
        MystrasColor("Mystras Serpentfish", 7238415, 15)
    )

    // OBSZAR SKANOWANIA
    private val searchX1 = 200
    private val searchY1 = 200
    private val searchX2 = 800
    private val searchY2 = 600

    private val intervalValues = intArrayOf(
        10, 20, 30, 45, 60, 90, 120
    )

    private val handler = Handler(Looper.getMainLooper())

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            if (!scanInProgress) {
                scanPage()
            }

            handler.postDelayed(
                this,
                selectedInterval().toLong()
            )
        }
    }

    // --------------------------------------------------
    // WYGLĄD
    // --------------------------------------------------

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun buttonBackground(
        color: Int,
        borderColor: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            setStroke(dp(1), borderColor)
            cornerRadius = dp(5).toFloat()
        }
    }

    private fun createControlButton(
        label: String,
        backgroundColor: Int,
        borderColor: Int,
        textColor: Int,
        textSize: Float
    ): Button {
        return Button(this).apply {
            text = label
            this.textSize = textSize
            setTextColor(textColor)
            isAllCaps = false

            background = buttonBackground(
                backgroundColor,
                borderColor
            )

            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0

            setPadding(dp(3), 0, dp(3), 0)
            stateListAnimator = null
        }
    }

    // --------------------------------------------------
    // START APLIKACJI
    // --------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT"
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(8, 7, 8, 7)
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
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(8, 3, 8, 1)
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

        // --------------------------------------------------
        // PRZYCISKI
        // --------------------------------------------------

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        val zoomOut = createControlButton(
            "−", Color.LTGRAY, Color.GRAY,
            Color.BLACK, 21f
        )

        zoomOut.setOnClickListener {
            activeWebView()?.zoomOut()
        }

        val zoomReset = createControlButton(
            "100%", Color.rgb(210, 210, 210),
            Color.GRAY, Color.BLACK, 12f
        )

        zoomReset.setOnClickListener {
            resetZoom()
        }

        val zoomIn = createControlButton(
            "+", Color.LTGRAY, Color.GRAY,
            Color.BLACK, 21f
        )

        zoomIn.setOnClickListener {
            activeWebView()?.zoomIn()
        }

        val startButton = createControlButton(
            "START", Color.rgb(25, 135, 65),
            Color.rgb(100, 220, 130),
            Color.WHITE, 13f
        )

        startButton.setOnClickListener {
            startAnalysis()
        }

        val stopButton = createControlButton(
            "STOP", Color.rgb(180, 35, 35),
            Color.rgb(240, 100, 100),
            Color.WHITE, 13f
        )

        stopButton.setOnClickListener {
            stopAnalysis()
        }

        val controlHeight = dp(40)

        controls.addView(
            zoomOut,
            LinearLayout.LayoutParams(0, controlHeight, 0.8f)
        )

        controls.addView(
            zoomReset,
            LinearLayout.LayoutParams(0, controlHeight, 1f)
        )

        controls.addView(
            zoomIn,
            LinearLayout.LayoutParams(0, controlHeight, 0.8f)
        )

        controls.addView(
            startButton,
            LinearLayout.LayoutParams(0, controlHeight, 1.3f)
        )

        controls.addView(
            stopButton,
            LinearLayout.LayoutParams(0, controlHeight, 1.3f)
        )

        root.addView(controls)

        status = TextView(this).apply {
            text = "Status: gotowy"
            textSize = 12f
            setTextColor(Color.GREEN)
            setPadding(8, 3, 8, 3)
        }

        root.addView(status)

        // --------------------------------------------------
        // OKNO GRY
        // --------------------------------------------------

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

        markerView = MarkerView(this).apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener { _, _ -> false }
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

        // --------------------------------------------------
        // LOG
        // --------------------------------------------------

        scanStatus = TextView(this).apply {
            text = "Analiza: oczekiwanie na START"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        logButton = createControlButton(
            "POKAŻ LOG",
            Color.rgb(55, 65, 75),
            Color.GRAY,
            Color.WHITE,
            12f
        )

        logScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true

            addView(
                scanStatus,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        logButton.setOnClickListener {
            if (logScroll.visibility == View.VISIBLE) {
                logScroll.visibility = View.GONE
                logButton.text = "POKAŻ LOG"
            } else {
                logScroll.visibility = View.VISIBLE
                logButton.text = "UKRYJ LOG"
            }
        }

        root.addView(
            logButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        )

        root.addView(
            logScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(110)
            )
        )

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
                    markerView.clearPoint()

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

    // --------------------------------------------------
    // WYBÓR CELU
    // --------------------------------------------------

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

    // --------------------------------------------------
    // ZOOM
    // --------------------------------------------------

    private fun resetZoom() {
        val active = activeWebView() ?: return

        if (active.scale > 0f) {
            active.zoomBy(1f / active.scale)
        }

        active.settings.textZoom = 100
        status.text = "Status: powiększenie przywrócone"
    }

    // --------------------------------------------------
    // KONFIGURACJA WEBVIEW
    // --------------------------------------------------

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

                val transport =
                    resultMsg?.obj as? WebView.WebViewTransport
                        ?: return false

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
        // --------------------------------------------------
    // START I STOP ANALIZY
    // --------------------------------------------------

    private fun startAnalysis() {
        if (running) return

        running = true
        scanCount = 0
        scanInProgress = false

        markerView.clearPoint()

        status.text = "Status: analiza uruchomiona"
        scanStatus.text = "Analiza: rozpoczęto"

        handler.removeCallbacks(scanRunnable)

        scanPage()

        handler.postDelayed(
            scanRunnable,
            selectedInterval().toLong()
        )
    }

    private fun stopAnalysis() {
        running = false

        handler.removeCallbacks(scanRunnable)

        status.text = "Status: zatrzymano"
        scanStatus.text = "Analiza zatrzymana"

        markerView.clearPoint()
        scanInProgress = false
    }

    private fun selectedInterval(): Int {
        val position = intervalSpinner.selectedItemPosition
            .coerceIn(0, intervalValues.lastIndex)

        return intervalValues[position] * 1000
    }

    // --------------------------------------------------
    // POBIERANIE OBRAZU GRY
    // --------------------------------------------------

    private fun scanPage() {
        if (!running || scanInProgress) return

        val active = activeWebView()

        if (active == null ||
            active.width <= 0 ||
            active.height <= 0
        ) {
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
                active.draw(Canvas(image))
            }
        } catch (e: Exception) {
            scanInProgress = false
            scanStatus.text =
                "Analiza: błąd obrazu: ${e.message}"
            return
        }

        val category = categorySpinner.selectedItemPosition
        val target = targetSpinner.selectedItem?.toString() ?: ""

        scanStatus.text =
            "Skanowanie #$scanCount\n" +
            "Obraz: ${bitmap.width}x${bitmap.height}\n" +
            "Wybrany cel: $target"

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
                    "Błąd analizy: ${e.message}",
                    bitmap.width,
                    bitmap.height
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            runOnUiThread {
                if (!running) {
                    scanInProgress = false
                    return@runOnUiThread
                }

                if (category == 0) {
                    scanInProgress = false
                    showBasicResult(result)
                } else {
                    showMystrasResult(result)
                }
            }
        }.start()
    }

    // --------------------------------------------------
    // MYSTRAS - ROZPOZNAWANIE KOLORÓW
    // --------------------------------------------------

    private fun analyzeMystras(
        bitmap: Bitmap,
        target: String
    ): AnalysisResult {

        val selectedColor = mystrasColors.firstOrNull {
            it.name == target
        } ?: return AnalysisResult(
            emptyList(),
            "Nie znaleziono wybranego surowca.",
            bitmap.width,
            bitmap.height
        )

        // Ograniczenie obszaru do współrzędnych użytkownika.
        val left = searchX1.coerceIn(0, bitmap.width)
        val top = searchY1.coerceIn(0, bitmap.height)
        val right = searchX2.coerceIn(0, bitmap.width)
        val bottom = searchY2.coerceIn(0, bitmap.height)

        if (right <= left || bottom <= top) {
            return AnalysisResult(
                emptyList(),
                "Obszar skanowania znajduje się poza obrazem gry.\n" +
                    "Obraz: ${bitmap.width}x${bitmap.height}\n" +
                    "Obszar: $searchX1,$searchY1 - $searchX2,$searchY2",
                bitmap.width,
                bitmap.height
            )
        }

        val roiWidth = right - left
        val roiHeight = bottom - top

        // Co 2 piksele - szybsza analiza.
        val step = 2

        val gridWidth = (roiWidth + step - 1) / step
        val gridHeight = (roiHeight + step - 1) / step

        val total = gridWidth * gridHeight

        // Pobieramy piksele jednorazowo.
        val pixels = IntArray(bitmap.width * bitmap.height)

        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        val colorGrid = IntArray(total)

        var matchingPixels = 0

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {

                val x = min(
                    left + gx * step,
                    right - 1
                )

                val y = min(
                    top + gy * step,
                    bottom - 1
                )

                val pixel = pixels[
                    y * bitmap.width + x
                ]

                if (matchesMystrasColor(
                        pixel,
                        selectedColor
                    )
                ) {
                    colorGrid[gy * gridWidth + gx] =
                        selectedColor.code

                    matchingPixels++
                }
            }
        }

        // --------------------------------------------------
        // GRUPOWANIE SĄSIADUJĄCYCH PIKSELI
        // --------------------------------------------------

        val visited = BooleanArray(total)
        val queue = ArrayDeque<Int>()

        val detections = mutableListOf<Detection>()

        var rejectedGroups = 0

        for (start in 0 until total) {

            val colorId = colorGrid[start]

            if (colorId == 0 || visited[start]) {
                continue
            }

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

                // Lewy sąsiad
                if (gx > 0) {
                    val n = current - 1

                    if (!visited[n] &&
                        colorGrid[n] == colorId
                    ) {
                        visited[n] = true
                        queue.add(n)
                    }
                }

                // Prawy sąsiad
                if (gx < gridWidth - 1) {
                    val n = current + 1

                    if (!visited[n] &&
                        colorGrid[n] == colorId
                    ) {
                        visited[n] = true
                        queue.add(n)
                    }
                }

                // Górny sąsiad
                if (gy > 0) {
                    val n = current - gridWidth

                    if (!visited[n] &&
                        colorGrid[n] == colorId
                    ) {
                        visited[n] = true
                        queue.add(n)
                    }
                }

                // Dolny sąsiad
                if (gy < gridHeight - 1) {
                    val n = current + gridWidth

                    if (!visited[n] &&
                        colorGrid[n] == colorId
                    ) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
            }

            val objectWidth =
                (maxX - minX + 1) * step

            val objectHeight =
                (maxY - minY + 1) * step

            val ratio =
                objectWidth.toFloat() /
                    objectHeight.coerceAtLeast(1).toFloat()

            // Filtr przypadkowych skupisk.
            val validSize =
                count in 5..1800

            val validShape =
                ratio in 0.35f..2.8f

            if (validSize && validShape) {

                val centerX =
                    left + ((minX + maxX) / 2) * step

                val centerY =
                    top + ((minY + maxY) / 2) * step

                detections.add(
                    Detection(
                        colorId = colorId,
                        colorName = selectedColor.name,
                        centerX = centerX,
                        centerY = centerY,
                        width = objectWidth,
                        height = objectHeight,
                        pixelCount = count * step * step
                    )
                )

            } else {
                rejectedGroups++
            }
        }

        // --------------------------------------------------
        // RAPORT ANALIZY
        // --------------------------------------------------

        val report = StringBuilder()

        report.append("TRYB: RYBY MYSTRAS\n")
        report.append("SKANOWANIE: #$scanCount\n")
        report.append("WYBRANA RYBA: $target\n")

        report.append(
            "OBRAZ GRY: ${bitmap.width}x${bitmap.height}\n"
        )

        report.append(
            "OBSZAR: X=$left-$right, Y=$top-$bottom\n"
        )

        report.append("KROK ANALIZY: $step px\n")
        report.append(
            "KOLOR: ${selectedColor.code}\n"
        )

        report.append(
            "TOLERANCJA: ${selectedColor.tolerance}%\n"
        )

        report.append(
            "PASUJĄCE PRÓBKI: $matchingPixels\n"
        )

        report.append(
            "ODRZUCONE GRUPY: $rejectedGroups\n"
        )

        report.append(
            "WYKRYTE OBSZARY: ${detections.size}\n\n"
        )

        if (detections.isEmpty()) {

            report.append(
                "NIE ZNALEZIONO RYBY.\n"
            )

            if (matchingPixels == 0) {
                report.append(
                    "Nie znaleziono pikseli pasujących do koloru.\n"
                )
                report.append(
                    "Sprawdź współrzędne obszaru i kolor wzorcowy."
                )
            } else {
                report.append(
                    "Znaleziono kolor, ale skupiska nie przeszły filtrów."
                )
            }

        } else {

            report.append("WYKRYTE POZYCJE:\n")

            detections.take(30).forEachIndexed {
                index, detection ->

                report.append(
                    "${index + 1}. " +
                    "${detection.colorName}: " +
                    "X=${detection.centerX}, " +
                    "Y=${detection.centerY}, " +
                    "${detection.width}x${detection.height}, " +
                    "piksele=${detection.pixelCount}\n"
                )
            }
        }

        return AnalysisResult(
            detections = detections,
            message = report.toString(),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    // --------------------------------------------------
    // PORÓWNYWANIE KOLORU
    // --------------------------------------------------

    private fun matchesMystrasColor(
        pixel: Int,
        reference: MystrasColor
    ): Boolean {

        // Clickermann używa tu wartości BGR:
        // 0x00BBGGRR
        // Android potrzebuje osobnych kanałów RGB.

        val referenceRed =
            reference.code and 0xFF

        val referenceGreen =
            (reference.code shr 8) and 0xFF

        val referenceBlue =
            (reference.code shr 16) and 0xFF

        val redDifference = abs(
            Color.red(pixel) - referenceRed
        )

        val greenDifference = abs(
            Color.green(pixel) - referenceGreen
        )

        val blueDifference = abs(
            Color.blue(pixel) - referenceBlue
        )

        val allowedDifference =
            255f * reference.tolerance / 100f

        return redDifference <= allowedDifference &&
            greenDifference <= allowedDifference &&
            blueDifference <= allowedDifference
    }

    // --------------------------------------------------
    // WYNIK MYSTRAS
    // --------------------------------------------------

    private fun showMystrasResult(
        result: AnalysisResult
    ) {

        val target =
            targetSpinner.selectedItem?.toString() ?: ""

        val matching = result.detections.filter {
            it.colorName == target
        }

        if (matching.isEmpty()) {
            scanInProgress = false
            markerView.clearPoint()
            scanStatus.text = result.message
            return
        }

        // Losowy wybór jednego wykrytego obszaru.
        val selected = matching.random()

        markerView.setPoint(
            selected.centerX.toFloat(),
            selected.centerY.toFloat()
        )

        val report = StringBuilder()

        report.append(result.message)
        report.append("\n------------------------------\n")
        report.append("WYBRANY OBSZAR:\n")
        report.append("RYBA: ${selected.colorName}\n")

        report.append(
            "POZYCJA: X=${selected.centerX}, " +
                "Y=${selected.centerY}\n"
        )

        report.append(
            "ROZMIAR: ${selected.width}x${selected.height}\n"
        )

        report.append(
            "PIKSELE: ${selected.pixelCount}\n"
        )

        report.append(
            "\nCZERWONY ZNACZNIK WSKAZUJE WYKRYTĄ RYBĘ."
        )

        scanStatus.text = report.toString()

        scanInProgress = false
    }
        // --------------------------------------------------
    // STRUKTURY WYNIKÓW
    // --------------------------------------------------

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

    // --------------------------------------------------
    // ANALIZA KOLORÓW - RYBY PODSTAWOWE
    // --------------------------------------------------

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

                colorGrid[gy * gridWidth + gx] =
                    classifyColor(bitmap.getPixel(x, y))
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

    // --------------------------------------------------
    // ROZPOZNAWANIE KOLORÓW - PODSTAWOWE
    // --------------------------------------------------

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

    // --------------------------------------------------
    // WYNIKI RYB PODSTAWOWYCH
    // --------------------------------------------------

    private fun showBasicResult(result: AnalysisResult) {

        val target =
            targetSpinner.selectedItem?.toString() ?: ""

        val matching = result.detections.filter {
            it.colorName == target
        }

        val report = StringBuilder()

        report.append("TRYB: RYBY PODSTAWOWE\n")
        report.append("WYBRANY KOLOR: $target\n")

        report.append(
            "OBRAZ: ${result.imageWidth}x${result.imageHeight}\n"
        )

        report.append(
            "WYKRYTE OBSZARY: ${result.detections.size}\n"
        )

        report.append(
            "OBSZARY WYBRANEGO KOLORU: ${matching.size}\n\n"
        )

        if (matching.isEmpty()) {

            markerView.clearPoint()

            report.append(
                "NIE ZNALEZIONO OBSZARU WYBRANEGO KOLORU.\n"
            )

            report.append(result.message)

        } else {

            val selected = matching.random()

            markerView.setPoint(
                selected.centerX.toFloat(),
                selected.centerY.toFloat()
            )

            report.append("LOSOWO WYBRANE WYKRYCIE:\n")
            report.append("KOLOR: ${selected.colorName}\n")

            report.append(
                "POZYCJA: X=${selected.centerX}, " +
                    "Y=${selected.centerY}\n"
            )

            report.append(
                "ROZMIAR: ${selected.width}x${selected.height}\n"
            )

            report.append(
                "PIKSELE OBSZARU: ${selected.pixelCount}\n"
            )

            report.append(
                "\nCZERWONY ZNACZNIK OZNACZA WYBRANY OBSZAR."
            )
        }

        scanStatus.text = report.toString()
    }

    // --------------------------------------------------
    // CZERWONY ZNACZNIK
    // --------------------------------------------------

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

            canvas.drawCircle(
                pointX,
                pointY,
                12f,
                paint
            )

            canvas.drawLine(
                pointX - 18f,
                pointY,
                pointX + 18f,
                pointY,
                paint
            )

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

    // --------------------------------------------------
    // POWRÓT
    // --------------------------------------------------

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

    // --------------------------------------------------
    // ZAMYKANIE APLIKACJI
    // --------------------------------------------------

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
