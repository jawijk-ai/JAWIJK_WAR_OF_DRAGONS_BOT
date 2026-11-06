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
import kotlin.random.Random

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

    private val intervalValues = listOf(10, 20, 30, 45, 60, 90, 120)

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running) {
                scanAndClickJS()
                
                // Randomizacja częstotliwości (-20% do +20%)
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

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 22))
            setPadding(8, 8, 8, 8)
        }

        val title = TextView(this).apply {
            text = "JAWIJK WOD BOT (JS Engine)"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 12)
        }
        root.addView(title)

        categorySpinner = Spinner(this)
        categorySpinner.adapter = makeAdapter(listOf("Ryby podstawowe", "Ryby Mystras"))
        root.addView(categorySpinner)

        targetSpinner = Spinner(this)
        targetSpinner.adapter = makeAdapter(basicTargets)
        root.addView(targetSpinner)

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val targets = if (position == 1) mystrasTargets else basicTargets
                targetSpinner.adapter = makeAdapter(targets)
                scanStatus.text = "Wybierz surowiec i uruchom bota."
            }
        }

        intervalSpinner = Spinner(this)
        intervalSpinner.adapter = makeAdapter(intervalValues.map { "$it sekund" })
        intervalSpinner.setSelection(1)
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
                val popup = WebView(this@MainActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.webViewClient = WebViewClient()
                popup.webChromeClient = this

                popupWebView?.let { old ->
                    webContainer.removeView(old)
                    old.destroy()
                }

                popupWebView = popup
                webContainer.addView(popup, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popup
                resultMsg.sendToTarget()
                addLog("Otwarto okno gry (Popup).")
                return true
            }
        }
    }

    private fun selectedInterval() = intervalValues.getOrElse(intervalSpinner.selectedItemPosition) { 20 }
    private fun selectedTarget() = targetSpinner.selectedItem?.toString() ?: ""

    private fun startScanning() {
        if (running) return
        running = true
        status.text = "Status: bot działa (JS Engine)"
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

    private fun scanAndClickJS() {
        val active = popupWebView ?: webView
        scanCount++
        scanStatus.text = "Skanowanie nr $scanCount (JS)..."

        val targetName = selectedTarget()

        val jsScript = """
            (function() {
                var elements = document.querySelectorAll('a, img, div[onclick], svg, canvas');
                var found = [];
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    var title = el.getAttribute('title') || el.getAttribute('alt') || el.className || el.id || '';
                    if (title.toLowerCase().indexOf('$targetName'.toLowerCase()) !== -1 || el.outerHTML.indexOf('$targetName') !== -1) {
                        var rect = el.getBoundingClientRect();
                        if (rect.width > 0 && rect.height > 0) {
                            found.push({
                                x: rect.left + rect.width / 2,
                                y: rect.top + rect.height / 2
                            });
                        }
                    }
                }
                return JSON.stringify(found);
            })();
        """.trimIndent()

        active.evaluateJavascript(jsScript) { jsonResult ->
            if (jsonResult == null || jsonResult == "null" || jsonResult == "[]") {
                scanStatus.text = "JS: Nie znaleziono obiektu '$targetName'."
                addLog("Skan $scanCount | Nie odnaleziono surowca w strukturze strony.")
            } else {
                try {
                    val cleanJson = if (jsonResult.startsWith("\"")) {
                        jsonResult.substring(1, jsonResult.length - 1).replace("\\\"", "\"")
                    } else jsonResult

                    val array = org.json.JSONArray(cleanJson)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val baseX = obj.getDouble("x").toFloat()
                        val baseY = obj.getDouble("y").toFloat()

                        // Randomizacja pozycji kliknięcia (-6px do +6px)
                        val offsetX = Random.nextInt(-6, 7)
                        val offsetY = Random.nextInt(-6, 7)

                        val finalX = baseX + offsetX + active.scrollX
                        val finalY = baseY + offsetY + active.scrollY

                        performHumanClick(active, finalX, finalY)

                        scanStatus.text = "JS: Znaleziono surowiec! Kliknięcie: ($finalX, $finalY)"
                        addLog("Skan $scanCount | Trafienie! Klik w ($finalX, $finalY) [offset: X=$offsetX, Y=$offsetY]")
                    }
                } catch (e: Exception) {
                    addLog("Błąd parsowania JS: ${e.message}")
                }
            }
        }
    }

    private fun performHumanClick(view: View, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        
        view.dispatchTouchEvent(down)
        down.recycle()

        // Losowy czas przytrzymania palca (60ms - 130ms)
        val holdDuration = Random.nextLong(60, 130)
        handler.postDelayed({
            val upTime = android.os.SystemClock.uptimeMillis()
            val up = android.view.MotionEvent.obtain(now, upTime, android.view.MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(up)
            up.recycle()
        }, holdDuration)
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

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
