package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import kotlin.random.Random

// Modele danych dla JSON
data class TargetItem(val name: String, val classId: Int)
data class CategoryItem(val name: String, val targets: List<TargetItem>)
data class ScanArea(val startXPct: Float, val endXPct: Float, val startYPct: Float, val endYPct: Float)

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var webContainer: FrameLayout

    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    private var popupWebView: WebView? = null
    private var running = false
    private var scanCount = 0

    private val handler = Handler(Looper.getMainLooper())

    // Dane z plików JSON
    private var categoriesData: List<CategoryItem> = listOf()
    private var scanArea = ScanArea(0.1f, 0.9f, 0.25f, 0.65f)

    // Inicjalizacja ONNX Runtime
    private val ortEnvironment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val ortSession: OrtSession? by lazy {
        try {
            val inputStream = assets.open("mini_solver.onnx")
            val modelBytes = inputStream.readBytes()
            ortEnvironment.createSession(modelBytes)
        } catch (e: Exception) {
            addLog("Błąd ładowania modelu ONNX: ${e.message}")
            null
        }
    }

    private val intervalValues = listOf(10, 20, 30, 45, 60, 90, 120)

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running) {
                scanAndClickWithOnnx()
                
                val baseInterval = selectedInterval() * 1000L
                val randomJitter = Random.nextLong(-baseInterval / 5, baseInterval / 5)
                val nextDelay = (baseInterval + randomJitter).coerceAtLeast(3000L)

                handler.postDelayed(this, nextDelay)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfigs()
        buildInterface()
        configureWebView()
        webView.loadUrl("https://warofdragons.com/")
    }

    private fun addLog(message: String) {
        if (!::logText.isInitialized) return
        runOnUiThread {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            logText.append("[$time] $message\n")
            if (logText.lineCount > 300) {
                logText.text = logText.text.toString().takeLast(12000)
            }
            if (::logScroll.isInitialized) {
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun loadConfigs() {
        try {
            val collectablesStr = assets.open("collectables.json").bufferedReader().use { it.readText() }
            val rootJson = JSONObject(collectablesStr)
            val jsonCats = rootJson.getJSONArray("categories")
            val loadedCats = mutableListOf<CategoryItem>()

            for (i in 0 until jsonCats.length()) {
                val catObj = jsonCats.getJSONObject(i)
                val catName = catObj.getString("name")
                val jsonTargets = catObj.getJSONArray("targets")
                val targets = mutableListOf<TargetItem>()

                for (j in 0 until jsonTargets.length()) {
                    val tObj = jsonTargets.getJSONObject(j)
                    targets.add(TargetItem(tObj.getString("name"), tObj.getInt("class_id")))
                }
                loadedCats.add(CategoryItem(catName, targets))
            }
            categoriesData = loadedCats
            addLog("Wczytano collectables.json pomyślnie.")
        } catch (e: Exception) {
            addLog("Błąd wczytywania collectables.json: ${e.message}")
        }

        try {
            val locationsStr = assets.open("locations.json").bufferedReader().use { it.readText() }
            val locJson = JSONObject(locationsStr).getJSONObject("default_scan_area")
            scanArea = ScanArea(
                locJson.getDouble("start_x_pct").toFloat(),
                locJson.getDouble("end_x_pct").toFloat(),
                locJson.getDouble("start_y_pct").toFloat(),
                locJson.getDouble("end_y_pct").toFloat()
            )
            addLog("Wczytano locations.json pomyślnie.")
        } catch (e: Exception) {
            addLog("Błąd wczytywania locations.json: ${e.message}")
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
            setPadding(8, 8, 8, 8)
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT (ONNX + JSON)"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 12)
        }
        root.addView(title)

        categorySpinner = Spinner(this).apply {
            adapter = makeAdapter(categoriesData.map { it.name })
        }
        root.addView(categorySpinner)

        targetSpinner = Spinner(this)
        updateTargetSpinner(0)
        root.addView(targetSpinner)

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTargetSpinner(position)
                scanStatus.text = "Zmieniono kategorię. Wybierz cel."
            }
        }

        intervalSpinner = Spinner(this).apply {
            adapter = makeAdapter(intervalValues.map { "$it sekund" })
            setSelection(1)
        }
        root.addView(intervalSpinner)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "START"
            setOnClickListener { startScanning() }
        }

        val stopButton = Button(this).apply {
            text = "STOP"
            setOnClickListener { stopScanning() }
        }

        buttons.addView(startButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(stopButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)

        // Przycisk do automatycznego kliknięcia "Gotowe" w oknie gry
        val gotoweButton = Button(this).apply {
            text = "KLIKNIJ 'GOTOWE' W OKNIE"
            setOnClickListener { clickGotoweButton() }
        }
        root.addView(gotoweButton)

        // Przycisk awaryjny do zamknięcia okna logowania / popupu
        val closePopupBtn = Button(this).apply {
            text = "ZAMKNIJ OKNO LOGOWANIA"
            setOnClickListener {
                popupWebView?.let { popup ->
                    webContainer.removeView(popup)
                    popup.destroy()
                    popupWebView = null
                    addLog("Ręcznie zamknięto okno popup.")
                } ?: run {
                    addLog("Brak aktywnego okna popup do zamknięcia.")
                }
            }
        }
        root.addView(closePopupBtn)

        status = TextView(this).apply {
            text = "Status: gotowy"
            textSize = 13f
            setTextColor(Color.GREEN)
            setPadding(4, 6, 4, 6)
        }
        root.addView(status)

        scanStatus = TextView(this).apply {
            text = "Bot nieaktywny."
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(4, 4, 4, 8)
        }
        root.addView(scanStatus)

        webContainer = FrameLayout(this)
        webView = WebView(this)
        webContainer.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(webContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val logButton = Button(this).apply { text = "POKAŻ / UKRYJ LOG" }
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
            logScroll.visibility = if (logScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.addView(logButton)
        root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120))

        setContentView(root)
    }

    private fun makeAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun updateTargetSpinner(categoryIndex: Int) {
        if (categoriesData.isNotEmpty() && categoryIndex < categoriesData.size) {
            val targets = categoriesData[categoryIndex].targets.map { t -> t.name }
            targetSpinner.adapter = makeAdapter(targets)
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
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val chromeClientRef = this
                val popup = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.setSupportMultipleWindows(true)
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            addLog("Popup URL: $url")
                        }
                    }
                    webChromeClient = chromeClientRef
                }

                popupWebView?.let { old ->
                    webContainer.removeView(old)
                    old.destroy()
                }

                popupWebView = popup
                webContainer.addView(popup, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popup
                resultMsg.sendToTarget()
                addLog("Otwarto okno logowania/popup.")
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                window?.let {
                    webContainer.removeView(it)
                    it.destroy()
                }
                if (popupWebView == window) {
                    popupWebView = null
                }
                addLog("Zamknięto okno logowania przez aplikację.")
            }
        }
    }

    private fun clickGotoweButton() {
        val active = popupWebView ?: webView
        val jsCode = """
            (function() {
                var buttons = document.querySelectorAll('button, div, span, a');
                for (var i = 0; i < buttons.length; i++) {
                    if (buttons[i].innerText && buttons[i].innerText.trim() === 'Gotowe') {
                        buttons[i].click();
                        return "Clicked Gotowe";
                    }
                }
                return "Not found";
            })();
        """.trimIndent()

        active.evaluateJavascript(jsCode) { result ->
            if (result != null && result.contains("Clicked Gotowe")) {
                addLog("Automatycznie kliknięto przycisk 'Gotowe' i zamknięto okno.")
            } else {
                addLog("Nie znaleziono przycisku 'Gotowe' na ekranie.")
            }
        }
    }

    private fun selectedInterval() = intervalValues.getOrElse(intervalSpinner.selectedItemPosition) { 20 }
    
    private fun selectedTargetItem(): TargetItem? {
        val catPos = categorySpinner.selectedItemPosition
        val targetPos = targetSpinner.selectedItemPosition
        return categoriesData.getOrNull(catPos)?.targets?.getOrNull(targetPos)
    }

    private fun startScanning() {
        if (running) return
        if (ortSession == null) {
            scanStatus.text = "Błąd: Brak modelu ONNX!"
            addLog("Nie można uruchomić bota - model ONNX nie został załadowany.")
            return
        }
        running = true
        status.text = "Status: bot działa (ONNX + JSON)"
        status.setTextColor(Color.GREEN)
        addLog("Uruchomiono bota.")
        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
    }

    private fun stopScanning() {
        running = false
        handler.removeCallbacks(scanRunnable)
        status.text = "Status: zatrzymany"
        status.setTextColor(Color.RED)
        scanStatus.text = "Bot zatrzymany."
        addLog("Zatrzymano bota.")
    }

    private fun scanAndClickWithOnnx() {
        val active = popupWebView ?: webView
        if (active.width <= 0 || active.height <= 0) return

        scanCount++
        val currentTarget = selectedTargetItem()
        scanStatus.text = "Skanowanie nr $scanCount (${currentTarget?.name ?: "Cel"})..."
        val session = ortSession ?: return

        val bitmap = try {
            Bitmap.createBitmap(active.width, active.height, Bitmap.Config.ARGB_8888).also {
                active.draw(Canvas(it))
            }
        } catch (e: Exception) {
            scanStatus.text = "Błąd zrzutu: ${e.message}"
            return
        }

        try {
            val targetWidth = 128
            val targetHeight = 128
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

            val tensorBuffer = FloatBuffer.allocate(3 * targetWidth * targetHeight)
            for (channel in 0..2) {
                for (y in 0 until targetHeight) {
                    for (x in 0 until targetWidth) {
                        val px = scaledBitmap.getPixel(x, y)
                        val v = when (channel) {
                            0 -> Color.red(px)
                            1 -> Color.green(px)
                            else -> Color.blue(px)
                        }
                        tensorBuffer.put(v.toFloat() / 255.0f)
                    }
                }
            }
            tensorBuffer.rewind()
            scaledBitmap.recycle()

            val shape = longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, tensorBuffer, shape)
            val inputName = session.inputNames.iterator().next()

            val results = session.run(mapOf(inputName to inputTensor))
            inputTensor.close()

            val outputTensor = results[0]
            val outputVal = outputTensor.value
            
            // DIAGNOSTYKA: Wypisujemy kształt wyjściowy modelu do logów aplikacji
            val info = StringBuilder("Shape: ")
            if (outputTensor.info is ai.onnxruntime.TensorInfo) {
                val tInfo = outputTensor.info as ai.onnxruntime.TensorInfo
                info.append(tInfo.shape.contentToString())
            }
            addLog("ONNX Wyjście [$info]")

            results.close()

            val expectedClassId = currentTarget?.classId ?: 0
            var foundX = -1f
            var foundY = -1f

            try {
                if (outputVal is Array<*>) {
                    for (row in outputVal) {
                        when (row) {
                            is FloatArray -> {
                                if (row.size >= 6) {
                                    val xCoord = row[0]
                                    val yCoord = row[1]
                                    val conf = row[4]
                                    val cls = row[5].toInt()
                                    if (cls == expectedClassId && conf > 0.3f) {
                                        foundX = xCoord
                                        foundY = yCoord
                                        break
                                    }
                                }
                            }
                            is Array<*> -> {
                                for (inner in row) {
                                    if (inner is FloatArray && inner.size >= 6) {
                                        val xCoord = inner[0]
                                        val yCoord = inner[1]
                                        val conf = inner[4]
                                        val cls = inner[5].toInt()
                                        if (cls == expectedClassId && conf > 0.3f) {
                                            foundX = xCoord
                                            foundY = yCoord
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        if (foundX != -1f) break
                    }
                }
            } catch (parseEx: Exception) {
                addLog("Błąd parsowania: ${parseEx.message}")
            }

            if (foundX != -1f && foundY != -1f) {
                val scaleX = active.width.toFloat() / targetWidth.toFloat()
                val scaleY = active.height.toFloat() / targetHeight.toFloat()
                
                val finalX = (foundX * scaleX) + active.scrollX
                val finalY = (foundY * scaleY) + active.scrollY

                performHumanClick(active, finalX, finalY)
                scanStatus.text = "Znaleziono ${currentTarget?.name}!"
                addLog("Skan $scanCount | Kliknięto w ($finalX, $finalY).")
            } else {
                scanStatus.text = "Nie znaleziono ${currentTarget?.name}."
                addLog("Skan $scanCount | Brak dopasowania dla ID $expectedClassId.")
            }

        } catch (e: Exception) {
            scanStatus.text = "Błąd ONNX: ${e.message}"
            addLog("Wyjątek ONNX: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun performHumanClick(view: View, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        view.dispatchTouchEvent(down)
        down.recycle()

        val holdDuration = Random.nextLong(70, 150)
        handler.postDelayed({
            val upTime = android.os.SystemClock.uptimeMillis()
            val up = android.view.MotionEvent.obtain(now, upTime, android.view.MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(up)
            up.recycle()
        }, holdDuration)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            ortSession?.close()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
