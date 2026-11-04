kotlin
package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var webContainer: FrameLayout
    private lateinit var categorySpinner: Spinner

    private var popupWebView: WebView? = null

    private var running = false
    private var scanCount = 0
    private var scanInProgress = false

    private val handler = Handler(Looper.getMainLooper())

    // Kolejne skanowanie co 20 sekund
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

        val categoryTitle = TextView(this)
        categoryTitle.text = "KATEGORIA SUROWCÓW:"
        layout.addView(categoryTitle)

        categorySpinner = Spinner(this)

        val categories = arrayOf(
            "Ryby podstawowe",
            "Ryby Mystras"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        categorySpinner.adapter = adapter
        layout.addView(categorySpinner)

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
        scanStatus.textSize = 11f
        scanStatus.setPadding(5, 5, 5, 5)
        scanStatus.setTextIsSelectable(true)

        val scroll = ScrollView(this)
        scroll.addView(scanStatus)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                130
            )
        )

        setContentView(layout)

        configureWebView()

        webView.loadUrl("https://www.warofdragons.com/")
    }
kotlin
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

        scanStatus.text = "Rozpoczynam analizę..."
        status.text = "ANALIZA AKTYWNA"

        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
    }

    private fun stopAnalysis() {

        running = false

        handler.removeCallbacks(scanRunnable)

        status.text = "ANALIZA ZATRZYMANA"
        scanStatus.text = "Analiza została zatrzymana."
    }
    private fun scanPage() {

        if (!running || scanInProgress) return

        val activeWebView = popupWebView ?: webView

        scanInProgress = true

        val javascript = """
            (function() {

                var selects = document.querySelectorAll('select');
                var filters = [];

                for (var i = 0; i < selects.length; i++) {

                    var select = selects[i];

                    var options = [];

                    for (var j = 0; j < select.options.length; j++) {

                        options.push({
                            text: select.options[j].text,
                            value: select.options[j].value,
                            selected: select.options[j].selected
                        });
                    }

                    filters.push({
                        name: select.name || "",
                        id: select.id || "",
                        value: select.value || "",
                        options: options
                    });
                }

                var canvases = document.querySelectorAll('canvas');

                var canvasInfo = [];

                for (var k = 0; k < canvases.length; k++) {

                    var rect = canvases[k].getBoundingClientRect();

                    canvasInfo.push({
                        width: canvases[k].width,
                        height: canvases[k].height,
                        screenWidth: Math.round(rect.width),
                        screenHeight: Math.round(rect.height)
                    });
                }

                return {
                    selects: filters,
                    canvasCount: canvases.length,
                    canvases: canvasInfo
                };

            })();
        """.trimIndent()

        activeWebView.evaluateJavascript(javascript) { result ->

            try {

                if (!running || result == null || result == "null") {
                    return@evaluateJavascript
                }

                val data = JSONArray("[$result]")
                    .getJSONObject(0)

                scanCount++

                val output = StringBuilder()

                output.append("SKAN: $scanCount\n")
                output.append("ODSTĘP: 20 SEKUND\n\n")

                val selects = data.optJSONArray("selects")

                if (selects != null && selects.length() > 0) {

                    output.append("LISTY ROZWIJANE GRY:\n\n")

                    for (i in 0 until selects.length()) {

                        val filter = selects.getJSONObject(i)

                        output.append("LISTA ${i + 1}\n")
                        output.append(
                            "Nazwa: ${filter.optString("name")}\n"
                        )
                        output.append(
                            "ID: ${filter.optString("id")}\n"
                        )
                        output.append(
                            "Wybrana wartość: ${filter.optString("value")}\n"
                        )

                        val options = filter.optJSONArray("options")

                        if (options != null) {

                            output.append("Dostępne poziomy:\n")

                            for (j in 0 until options.length()) {

                                val option = options.getJSONObject(j)

                                val selected =
                                    if (option.optBoolean("selected")) {
                                        " <-- WYBRANE"
                                    } else {
                                        ""
                                    }

                                output.append(
                                    "- ${option.optString("text")} " +
                                    "(wartość: ${option.optString("value")})" +
                                    selected + "\n"
                                )
                            }
                        }

                        output.append("\n")
                    }

                } else {

                    output.append(
                        "Nie znaleziono list rozwijanych HTML.\n"
                    )
                }

                val canvasCount = data.optInt("canvasCount")

                output.append("\nOBSZAR GRY:\n")
                output.append("Liczba Canvas: $canvasCount\n")

                val canvases = data.optJSONArray("canvases")

                if (canvases != null) {

                    for (i in 0 until canvases.length()) {

                        val canvas = canvases.getJSONObject(i)

                        output.append(
                            "Canvas ${i + 1}: " +
                            "${canvas.optInt("width")} x " +
                            "${canvas.optInt("height")}\n"
                        )
                    }
                }

                output.append(
                    "\nTo jest skan diagnostyczny. " +
                    "Wykrywanie kółek zostanie dodane po ustaleniu " +
                    "sposobu rysowania znaczników przez grę."
                )

                scanStatus.text = output.toString()

                status.text = "ANALIZA AKTYWNA | SKAN: $scanCount"

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd analizy: ${e.message}"

            } finally {

                scanInProgress = false

                if (running) {
                    handler.removeCallbacks(scanRunnable)
                    handler.postDelayed(scanRunnable, 20000)
                }
            }
        }
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

        handler.removeCallbacks(scanRunnable)

        popupWebView?.destroy()
        popupWebView = null

        webView.destroy()

        super.onDestroy()
    }
}
