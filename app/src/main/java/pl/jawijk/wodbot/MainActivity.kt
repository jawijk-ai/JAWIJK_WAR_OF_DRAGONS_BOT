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
import android.webkit.JavascriptInterface
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var webContainer: FrameLayout

    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    private var popupWebView: WebView? = null
    private var running = false
    private var scanCount = 0

    private val handler = Handler(Looper.getMainLooper())

    private var currentAvailableTargets: MutableList<String> = mutableListOf("Oczekiwanie na dane z gry...")

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

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
            setPadding(8, 8, 8, 8)
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT (NETWORK HOOK)"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 12)
        }
        root.addView(title)

        val refreshTargetsBtn = Button(this).apply {
            text = "ODŚWIEŻ / SPRAWDŹ DANE LOKACJI"
            setOnClickListener { 
                addLog("Przeładowywano / sprawdzono bufor sieciowy...")
                injectNetworkSniffer()
            }
        }
        root.addView(refreshTargetsBtn)

        targetSpinner = Spinner(this)
        updateTargetSpinner()
        root.addView(targetSpinner)

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

    private fun updateTargetSpinner() {
        targetSpinner.adapter = makeAdapter(currentAvailableTargets)
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
        
        // Dodanie interfejsu do komunikacji JavaScript -> Kotlin
        webView.addJavascriptInterface(WebAppInterface { namesList ->
            if (namesList.isNotEmpty()) {
                runOnUiThread {
                    currentAvailableTargets = namesList.toMutableList()
                    updateTargetSpinner()
                    addLog("Przechwycono z sieci (${namesList.size} pozycji): ${namesList.joinToString(", ")}")
                }
            }
        }, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                addLog("Strona załadowana. Wstrzykiwanie nasłuchu sieciowego...")
                injectNetworkSniffer()
            }
        }

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
            }
        }
    }

    // Klasa mostka do odbierania danych z JS
    class WebInterface(private val onTargetsFound: (List<String>) -> Unit) {
        @JavascriptInterface
        fun postTargets(jsonArrayStr: String) {
            try {
                val arr = JSONArray(jsonArrayStr)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                onTargetsFound(list)
            } catch (_: Exception) {}
        }
    }
    // Alias dla kompatybilności nazwy
    private fun WebAppInterface(onTargetsFound: (List<String>) -> Unit) = WebInterface(onTargetsFound)

    // Wstrzykiwanie skryptu przechwytującego ruch sieciowy hunt_conf.php
    private fun injectNetworkSniffer() {
        val active = popupWebView ?: webView
        val snifferJs = """
            (function() {
                if (window.jawijkHooked) return;
                window.jawijkHooked = true;

                // Hook fetch
                const origFetch = window.fetch;
                window.fetch = async function(...args) {
                    const response = await origFetch.apply(this, args);
                    try {
                        const url = args[0] || '';
                        if (typeof url === 'string' && url.includes('hunt_conf.php')) {
                            const clone = response.clone();
                            clone.text().then(text => parseAndSend(text));
                        }
                    } catch(e) {}
                    return response;
                };

                // Hook XMLHttpRequest
                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._url = url;
                    return origOpen.apply(this, arguments);
                };

                const origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    this.addEventListener('load', function() {
                        try {
                            if (this._url && typeof this._url === 'string' && this._url.includes('hunt_conf.php')) {
                                parseAndSend(this.responseText);
                            }
                        } catch(e) {}
                    });
                    return origSend.apply(this, arguments);
                };

                function parseAndSend(dataStr) {
                    try {
                        let names = [];
                        // Parsowanie prostego XML / stringów z name="..."
                        let matches = dataStr.match(/name="([^"]+)"/g);
                        if (matches) {
                            matches.forEach(m => {
                                let val = m.replace('name="', '').replace('"', '').trim();
                                if (val.length > 2 && !names.includes(val)) {
                                    names.push(val);
                                }
                            });
                        }
                        if (names.length > 0 && window.AndroidBridge) {
                            window.AndroidBridge.postTargets(JSON.stringify(names));
                        }
                    } catch(e) {}
                }
            })();
        """.trimIndent()

        active.evaluateJavascript(snifferJs, null)
    }

    private fun selectedInterval() = intervalValues.getOrElse(intervalSpinner.selectedItemPosition) { 20 }
    
    private fun selectedTargetName(): String {
        return targetSpinner.selectedItem?.toString() ?: ""
    }

    private fun startScanning() {
        if (running) return
        running = true
        status.text = "Status: bot działa (Network Hook)"
        status.setTextColor(Color.GREEN)
        addLog("Uruchomiono bota.")
        injectNetworkSniffer()
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

    private fun fetchAndClickFromDOM() {
        val active = popupWebView ?: webView
        if (active.width <= 0 || active.height <= 0) return

        scanCount++
        val targetName = selectedTargetName()
        if (targetName.isBlank() || targetName.startsWith("Oczekiwanie")) {
            scanStatus.text = "Wybierz cel!"
            return
        }

        scanStatus.text = "Skanowanie nr $scanCount ($targetName)..."

        val jsCode = """
            (function() {
                var targetName = "$targetName".toLowerCase();
                var results = [];
                var elements = document.querySelectorAll('div, span, a, img');
                
                elements.forEach(function(el) {
                    var text = (el.innerText || el.getAttribute('alt') || el.getAttribute('title') || '').toLowerCase();
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

        active.evaluateJavascript(jsCode) { rawResult ->
            try {
                var jsonResult = rawResult ?: "[]"
                if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                    jsonResult = jsonResult.substring(1, jsonResult.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                }

                if (jsonResult != "null" && jsonResult != "[]" && jsonResult.isNotBlank()) {
                    val jsonArray = JSONArray(jsonResult)
                    if (jsonArray.length() > 0) {
                        val obj = jsonArray.getJSONObject(0)
                        val x = obj.getDouble("x").toFloat()
                        val y = obj.getDouble("y").toFloat()

                        val finalX = x + active.scrollX
                        val finalY = y + active.scrollY

                        val offsetX = Random.nextInt(-4, 5)
                        val offsetY = Random.nextInt(-4, 5)

                        performHumanClick(active, finalX + offsetX, finalY + offsetY)
                        scanStatus.text = "Znaleziono i kliknięto $targetName!"
                        addLog("Skan $scanCount | Kliknięto $targetName w ($finalX, $finalY).")
                        return@evaluateJavascript
                    }
                }
                scanStatus.text = "Nie znaleziono $targetName."
                addLog("Skan $scanCount | Brak obiektu '$targetName' na planszy.")
            } catch (e: Exception) {
                addLog("Błąd kliknięcia: ${e.message}")
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
