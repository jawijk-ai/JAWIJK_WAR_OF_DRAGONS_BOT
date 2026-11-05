package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    private lateinit var categorySpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var targetTitle: TextView

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

    private val mystrasTargets = arrayOf(
        "Golden Shams",
        "Mystras Serpentfish",
        "Akduff the Multiped"
    )

    private val handler = Handler(Looper.getMainLooper())

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running && !scanInProgress) {
                scanPage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)

        status = TextView(this)
        status.text = "JAWIJK WOD – GOTOWY"
        status.textSize = 16f
        status.setPadding(8, 8, 8, 8)
        layout.addView(status)

        val categoryLabel = TextView(this)
        categoryLabel.text = "KATEGORIA:"
        layout.addView(categoryLabel)

        categorySpinner = Spinner(this)

        val categories = arrayOf(
            "Ryby podstawowe",
            "Ryby Mystras"
        )

        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        )

        categoryAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        categorySpinner.adapter = categoryAdapter
        layout.addView(categorySpinner)

        targetTitle = TextView(this)
        targetTitle.text = "WYBIERZ KOLOR KÓŁKA:"
        layout.addView(targetTitle)

        targetSpinner = Spinner(this)
        layout.addView(targetSpinner)

        updateTargetList()

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateTargetList()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }

        webContainer = FrameLayout(this)
        webView = WebView(this)

        webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        layout.addView(
            webContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL

        val startButton = Button(this)
        startButton.text = "START"
        startButton.setOnClickListener {
            startAnalysis()
        }

        val stopButton = Button(this)
        stopButton.text = "STOP"
        stopButton.setOnClickListener {
            stopAnalysis()
        }

        buttons.addView(
            startButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        buttons.addView(
            stopButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        layout.addView(buttons)

        val resultTitle = TextView(this)
        resultTitle.text = "WYNIKI ANALIZY:"
        layout.addView(resultTitle)

        scanStatus = TextView(this)
        scanStatus.text = "Oczekiwanie na START..."
        scanStatus.textSize = 12f
        scanStatus.setPadding(5, 5, 5, 5)
        scanStatus.setTextIsSelectable(true)

        val scroll = ScrollView(this)
        scroll.addView(scanStatus)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                140
            )
        )

        setContentView(layout)

        configureWebView()
        webView.loadUrl("https://www.warofdragons.com/")
    }

    private fun updateTargetList() {

        val basicMode =
            categorySpinner.selectedItemPosition == 0

        val targets = if (basicMode) {
            basicTargets
        } else {
            mystrasTargets
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            targets
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        targetSpinner.adapter = adapter

        targetTitle.text = if (basicMode) {
            "WYBIERZ KOLOR KÓŁKA:"
        } else {
            "WYBIERZ RYBĘ MYSTRAS:"
        }
    }

    private fun configureWebView() {

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        CookieManager.getInstance().setAcceptCookie(true)

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)
                status.text = "Strona gry załadowana"
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {

                val popup = WebView(this@MainActivity)

                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.loadsImagesAutomatically = true
                popup.settings.javaScriptCanOpenWindowsAutomatically = true
                popup.settings.setSupportMultipleWindows(true)
                popup.settings.useWideViewPort = true
                popup.settings.loadWithOverviewMode = true

                CookieManager.getInstance().setAcceptCookie(true)

                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(popup, true)
                }

                popup.webViewClient = WebViewClient()
                popup.webChromeClient = this

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

                transport.setWebView(popup)
                resultMsg.sendToTarget()

                return true
            }

            override fun onCloseWindow(window: WebView?) {

                if (window != null) {
                    webContainer.removeView(window)
                    window.destroy()
                }

                popupWebView = null
            }
        }
    }
       private fun startAnalysis() {

        if (running) return

        running = true
        scanInProgress = false
        scanCount = 0

        status.text = "ANALIZA AKTYWNA"
        scanStatus.text = "Przygotowanie analizy..."

        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
    }

    private fun stopAnalysis() {

        running = false
        scanInProgress = false

        handler.removeCallbacks(scanRunnable)

        status.text = "ANALIZA ZATRZYMANA"
        scanStatus.text = "Analiza została zatrzymana."
    }

    private fun scanPage() {

        if (!running || scanInProgress) return

        val activeWebView = popupWebView ?: webView

        if (
            !activeWebView.isAttachedToWindow ||
            activeWebView.width <= 0 ||
            activeWebView.height <= 0
        ) {
            scanStatus.text = "Okno gry nie jest widoczne."
            scheduleNextScan()
            return
        }

        scanInProgress = true
        scanStatus.text = "SKAN W TOKU – pobieranie obrazu..."

        // Pobieramy ustawienia na głównym wątku.
        val selectedCategory =
            categorySpinner.selectedItemPosition

        val selectedTarget =
            targetSpinner.selectedItem?.toString() ?: ""

        try {

            val bitmap = Bitmap.createBitmap(
                activeWebView.width,
                activeWebView.height,
                Bitmap.Config.ARGB_8888
            )

            activeWebView.draw(Canvas(bitmap))

            Thread {

                try {

                    val report = analyzeBitmap(
                        bitmap,
                        selectedCategory,
                        selectedTarget
                    )

                    runOnUiThread {

                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }

                        if (running) {
                            scanCount++
                            status.text =
                                "ANALIZA AKTYWNA | SKAN: $scanCount"

                            scanStatus.text = report
                        }

                        scanInProgress = false
                        scheduleNextScan()
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }

                        scanStatus.text =
                            "BŁĄD ANALIZY: ${e.message}"

                        scanInProgress = false
                        scheduleNextScan()
                    }
                }

            }.start()

        } catch (e: Exception) {

            scanStatus.text =
                "BŁĄD POBIERANIA OBRAZU: ${e.message}"

            scanInProgress = false
            scheduleNextScan()
        }
    }

    private fun scheduleNextScan() {

        if (running) {
            handler.removeCallbacks(scanRunnable)
            handler.postDelayed(scanRunnable, 20000)
        }
    }

    private data class Detection(
        val colorName: String,
        val x: Int,
        val y: Int,
        val pixels: Int,
        val width: Int,
        val height: Int
    )

    private fun analyzeBitmap(
        bitmap: Bitmap,
        category: Int,
        target: String
    ): String {

        // Mystras ma osobny tryb.
        if (category == 1) {
            return analyzeMystras(bitmap, target)
        }

        // W trybie podstawowym analizujemy tylko wybrany kolor.
        val selectedColorId = when (target) {
            "Niebieski" -> 1
            "Fioletowy" -> 2
            "Szary" -> 3
            "Zielony" -> 4
            "Czerwony" -> 5
            else -> 0
        }

        if (selectedColorId == 0) {
            return "Nie wybrano prawidłowego koloru."
        }

        val width = bitmap.width
        val height = bitmap.height

        val step = 2
        val gridWidth = width / step
        val gridHeight = height / step

        if (gridWidth < 5 || gridHeight < 5) {
            return "Obraz łowiska jest za mały."
        }

        val colors = Array(gridHeight) {
            IntArray(gridWidth)
        }

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {

                val px = min(gx * step, width - 1)
                val py = min(gy * step, height - 1)

                colors[gy][gx] =
                    classifyColor(bitmap.getPixel(px, py))
            }
        }

        val visited = Array(gridHeight) {
            BooleanArray(gridWidth)
        }

        val detections = mutableListOf<Detection>()

        val dx = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val dy = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {

                if (
                    colors[y][x] != selectedColorId ||
                    visited[y][x]
                ) {
                    continue
                }

                val queue = ArrayDeque<Int>()

                queue.add(y * gridWidth + x)
                visited[y][x] = true

                var count = 0
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y

                var sumX = 0
                var sumY = 0

                while (queue.isNotEmpty()) {

                    val point = queue.removeFirst()

                    val cx = point % gridWidth
                    val cy = point / gridWidth

                    count++
                    sumX += cx
                    sumY += cy

                    minX = min(minX, cx)
                    maxX = max(maxX, cx)
                    minY = min(minY, cy)
                    maxY = max(maxY, cy)

                    for (d in 0 until 8) {

                        val nx = cx + dx[d]
                        val ny = cy + dy[d]

                        if (
                            nx in 0 until gridWidth &&
                            ny in 0 until gridHeight &&
                            !visited[ny][nx] &&
                            colors[ny][nx] == selectedColorId
                        ) {
                            visited[ny][nx] = true
                            queue.add(ny * gridWidth + nx)
                        }
                    }
                }

                val boxWidth = maxX - minX + 1
                val boxHeight = maxY - minY + 1

                val ratio =
                    boxWidth.toFloat() /
                    max(1, boxHeight).toFloat()

                if (
                    count >= 5 &&
                    count <= 1800 &&
                    ratio in 0.45f..2.2f
                ) {

                    detections.add(
                        Detection(
                            colorName(selectedColorId),
                            (sumX / count) * step,
                            (sumY / count) * step,
                            count,
                            boxWidth * step,
                            boxHeight * step
                        )
                    )
                }
            }
        }

        return buildBasicReport(
            width,
            height,
            target,
            detections
        )
    }
       private fun classifyColor(pixel: Int): Int {

        val hsv = FloatArray(3)
        Color.colorToHSV(pixel, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        if (value < 0.20f) return 0

        // Szary.
        if (saturation < 0.20f && value > 0.30f) {
            return 3
        }

        if (saturation < 0.25f) return 0

        // Niebieski.
        if (hue in 185f..245f) {
            return 1
        }

        // Fioletowy.
        if (hue in 246f..305f) {
            return 2
        }

        // Zielony.
        if (hue in 65f..175f) {
            return 4
        }

        // Czerwony.
        if (hue <= 15f || hue >= 345f) {
            return 5
        }

        return 0
    }

    private fun colorName(id: Int): String {
        return when (id) {
            1 -> "NIEBIESKI"
            2 -> "FIOLETOWY"
            3 -> "SZARY"
            4 -> "ZIELONY"
            5 -> "CZERWONY"
            else -> "NIEZNANY"
        }
    }

    private fun buildBasicReport(
        width: Int,
        height: Int,
        target: String,
        detections: List<Detection>
    ): String {

        val output = StringBuilder()

        output.append("TRYB: RYBY PODSTAWOWE\n")
        output.append("WYBRANY KOLOR: $target\n")
        output.append("OBRAZ: ${width}x${height}\n")
        output.append("WYKRYTE OBSZARY: ${detections.size}\n\n")

        if (detections.isEmpty()) {

            output.append(
                "Nie wykryto obszarów wybranego koloru.\n"
            )

            output.append(
                "Sprawdź, czy łowisko jest widoczne.\n"
            )

        } else {

            for (i in detections.indices) {

                val item = detections[i]

                output.append(
                    "${i + 1}. ${item.colorName}\n"
                )

                output.append(
                    "Pozycja: X=${item.x}, Y=${item.y}\n"
                )

                output.append(
                    "Rozmiar: ${item.width}x${item.height}\n"
                )

                output.append(
                    "Punkty obrazu: ${item.pixels}\n\n"
                )
            }
        }

        output.append(
            "UWAGA: obszary kolorów nie są jeszcze " +
            "potwierdzonymi kółkami na wodzie.\n"
        )

        output.append("Następny skan za 20 sekund.")

        return output.toString()
    }

    private fun analyzeMystras(
        bitmap: Bitmap,
        target: String
    ): String {

        val output = StringBuilder()

        output.append("TRYB: RYBY MYSTRAS\n")
        output.append("WYBRANA RYBA: $target\n\n")

        output.append("Dostępne cele:\n")

        for (fish in mystrasTargets) {
            output.append("- $fish\n")
        }

        output.append("\nOBRAZ ŁOWISKA:\n")
        output.append(
            "${bitmap.width}x${bitmap.height}\n\n"
        )

        output.append(
            "Tryb Mystras jest oddzielony od rozpoznawania " +
            "pięciu kolorów.\n\n"
        )

        output.append(
            "Rozpoznawanie wybranej ryby wymaga jeszcze " +
            "dopasowania jej ikony lub wzoru graficznego " +
            "z biblioteki gry.\n"
        )

        output.append("\nNastępny skan za 20 sekund.")

        return output.toString()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        val popup = popupWebView

        if (popup != null) {

            webContainer.removeView(popup)
            popup.destroy()
            popupWebView = null

            return
        }

        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {

        running = false
        scanInProgress = false

        handler.removeCallbacks(scanRunnable)

        popupWebView?.destroy()
        popupWebView = null

        webView.destroy()

        super.onDestroy()
    }
} 
