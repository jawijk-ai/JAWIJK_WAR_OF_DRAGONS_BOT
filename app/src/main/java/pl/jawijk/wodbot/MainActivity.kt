package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// Modele danych dla JSON
data class TargetItem(val name: String, val classId: Int)
data class CategoryItem(val name: String, val targets: List<TargetItem>)

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

    private val intervalValues = listOf(10, 20, 30, 45, 60, 90, 120)

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running) {
                fetchAndClickFromDOM()
                
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
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
            setPadding(8, 8, 8, 8)
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT (DOM-FIRST)"
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

    private fun selectedInterval() = intervalValues.getOrElse(intervalSpinner.selectedItemPosition) { 20 }
    
    private fun selectedTargetItem(): TargetItem? {
        val catPos = categorySpinner.selectedItemPosition
        val targetPos = targetSpinner.selectedItemPosition
        return categoriesData.getOrNull(catPos)?.targets?.getOrNull(targetPos)
    }

    private fun startScanning() {
        if (running) return
        running = true
        status.text = "Status: bot działa (DOM-First)"
        status.setTextColor(Color.GREEN)
        addLog("Uruchomiono bota w trybie DOM-First.")
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

    // GŁÓwna metoda: Odczyt z kodu gry (DOM) i automatyczne kliknięcie w pozycję elementu
    private fun fetchAndClickFromDOM() {
        val active = popupWebView ?: webView
        if (active.width <= 0 || active.height <= 0) return

        scanCount++
        val currentTarget = selectedTargetItem() ?: return
        scanStatus.text = "Skanowanie DOM nr $scanCount (${currentTarget.name})..."

        // Skrypt JavaScript przeszukujący elementy gry w poszukiwaniu obiektów pasujących do wybranego celu oraz ich pozycji (bounding client rect)
        val jsCode = """
            (function() {
                var targetName = "${currentTarget.name}".toLowerCase();
                var results = [];
                
                // Szukamy elementów w grze, które reprezentują zasoby/moby na planszy (np. ikony, linki, obiekty z klasami gry)
                // Przesukujemy powszechne selektory interaktywne na mapie
                var elements = document.querySelectorAll('.map-object, .resource, .monster, [data-name], img, div');
                
                elements.forEach(function(el) {
                    var text = (el.innerText || el.getAttribute('data-name') || el.getAttribute('title') || '').toLowerCase();
                    if (text.includes(targetName)) {
                        var rect = el.getBoundingClientRect();
                        if (rect.width > 0 && rect.height > 0) {
                            results.push({
                                x: rect.left + rect.width / 2,
                                y: rect.top + rect.height / 2
                            });
                        }
                    }
                });
                
                return JSON.stringify(results);
            })();
        """.trimIndent()

        active.evaluateJavascript(jsCode) { jsonResult ->
            try {
                if (jsonResult != null && jsonResult != "null" && jsonResult != "[]") {
                    val jsonArray = JSONArray(jsonResult)
                    if (jsonArray.length() > 0) {
                        // Pobieramy współrzędne pierwszego znalezionego elementu
                        val obj = jsonArray.getJSONObject(0)
                        val x = obj.getDouble("x").toFloat()
                        val y = obj.getDouble("y").toFloat()

                        val finalX = x + active.scrollX
                        val finalY = y + active.scrollY

                        val offsetX = Random.nextInt(-4, 5)
                        val offsetY = Random.nextInt(-4, 5)

                        performHumanClick(active, finalX + offsetX, finalY + offsetY)
                        scanStatus.text = "Znaleziono i kliknięto ${currentTarget.name}!"
                        addLog("Skan $scanCount | DOM: Kliknięto ${currentTarget.name} w punkcie ($finalX, $finalY).")
                        return@evaluateJavascript
                    }
                }
                scanStatus.text = "Nie znaleziono ${currentTarget.name} w DOM."
                addLog("Skan $scanCount | DOM: Brak obiektów dla '${currentTarget.name}'.")
            } catch (e: Exception) {
                addLog("Błąd parsowania wynikowej pozycji DOM: ${e.message}")
            }
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
        super.onDestroy()
    }
}
