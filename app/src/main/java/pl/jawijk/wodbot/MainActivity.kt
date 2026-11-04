package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var webContainer: FrameLayout

    private var popupWebView: WebView? = null
    private var running = false
    private var collected = 0
    private var scanCount = 0
    private var scanInProgress = false

    private val handler = Handler(Looper.getMainLooper())

    private val scanTask = object : Runnable {
        override fun run() {
            if (!running) return

            analyzeScreen()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val title = TextView(this)
        title.text = "JAWIJK WAR OF DRAGONS"
        title.textSize = 19f
        title.setPadding(10, 8, 10, 8)
        layout.addView(title)

        status = TextView(this)
        status.setPadding(10, 5, 10, 5)
        layout.addView(status)

        val buttons = LinearLayout(this)

        val startButton = Button(this)
        startButton.text = "START ANALIZY"
        startButton.setOnClickListener {
            startAnalysis()
        }

        val stopButton = Button(this)
        stopButton.text = "STOP ANALIZY"
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
        resultTitle.text = "WYKRYTE IKONY I OBRAZY:"
        resultTitle.setPadding(10, 5, 10, 5)
        layout.addView(resultTitle)

        scanStatus = TextView(this)
        scanStatus.text = "Oczekiwanie na skanowanie..."
        scanStatus.textSize = 11f
        scanStatus.setPadding(10, 5, 10, 5)
        scanStatus.isTextSelectable = true

        val resultScroll = ScrollView(this)

        resultScroll.addView(
            scanStatus,
            ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        layout.addView(
            resultScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                180
            )
        )

        val addButton = Button(this)
        addButton.text = "DODAJ ZEBRANY SUROWIEC"

        addButton.setOnClickListener {
            collected++
            updateStatus()
        }

        layout.addView(addButton)

        val backButton = Button(this)
        backButton.text = "POWRÓT DO GRY"

        backButton.setOnClickListener {
            showMainPage()
        }

        layout.addView(backButton)

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

        layout.addView(
            webContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)

        updateStatus()

        webView.loadUrl("https://www.warofdragons.com/")
    }

    private fun startAnalysis() {
        if (running) return

        running = true
        scanCount = 0

        scanStatus.text = "Uruchomiono analizę co 3 sekundy."

        handler.removeCallbacks(scanTask)
        handler.post(scanTask)

        updateStatus()
    }

    private fun stopAnalysis() {
        running = false

        handler.removeCallbacks(scanTask)

        scanStatus.text = "Analiza zatrzymana."

        updateStatus()
    }

    private fun analyzeScreen() {
        if (scanInProgress) return

        val activeView = getActiveWebView()

        scanInProgress = true

        val script = """
            (function() {
                var results = [];
                var seen = {};

                function addItem(el, type, url) {
                    if (!el) return;

                    var rect = el.getBoundingClientRect();
                    var style = window.getComputedStyle(el);

                    if (
                        rect.width < 15 ||
                        rect.height < 15 ||
                        rect.width > 600 ||
                        rect.height > 600 ||
                        style.display === 'none' ||
                        style.visibility === 'hidden' ||
                        style.opacity === '0' ||
                        rect.bottom <= 0 ||
                        rect.right <= 0 ||
                        rect.top >= window.innerHeight ||
                        rect.left >= window.innerWidth
                    ) {
                        return;
                    }

                    var key = type + '|' + url + '|' +
                        Math.round(rect.x) + '|' +
                        Math.round(rect.y);

                    if (seen[key]) return;

                    seen[key] = true;

                    results.push({
                        type: type,
                        url: url || '',
                        alt: el.alt || '',
                        title: el.title || '',
                        width: Math.round(rect.width),
                        height: Math.round(rect.height),
                        x: Math.round(rect.x),
                        y: Math.round(rect.y)
                    });
                }

                document.querySelectorAll('img').forEach(function(el) {
                    addItem(
                        el,
                        'IMG',
                        el.currentSrc || el.src || ''
                    );
                });

                document.querySelectorAll('svg').forEach(function(el) {
                    addItem(el, 'SVG', '');
                });

                document.querySelectorAll('canvas').forEach(function(el) {
                    addItem(el, 'CANVAS', '');
                });

                document.querySelectorAll('*').forEach(function(el) {
                    var bg = window.getComputedStyle(el).backgroundImage;

                    if (bg && bg !== 'none') {
                        addItem(el, 'BACKGROUND', bg);
                    }
                });

                results.sort(function(a, b) {
                    return (a.width * a.height) -
                           (b.width * b.height);
                });

                return {
                    total: results.length,
                    images: results.slice(0, 80),
                    page: document.location.href
                };
            })();
        """.trimIndent()

        activeView.evaluateJavascript(script) { result ->

            scanInProgress = false

            if (!running) return@evaluateJavascript

            scanCount++

            try {
                if (result == null || result == "null") {
                    scanStatus.text =
                        "Skan $scanCount: brak odpowiedzi strony."

                    updateStatus()
                    return@evaluateJavascript
                }

                val json = JSONObject(result)

                val total = json.optInt("total", 0)
                val images = json.optJSONArray("images")

                val output = StringBuilder()

                output.append("SKAN: ")
                    .append(scanCount)
                    .append("\n")

                output.append("WYKRYTE ELEMENTY: ")
                    .append(total)
                    .append("\n\n")

                if (images == null || images.length() == 0) {

                    output.append(
                        "Nie znaleziono obrazków ani ikon."
                    )

                } else {

                    for (i in 0 until images.length()) {

                        val item = images.getJSONObject(i)

                        output.append("ELEMENT ")
                            .append(i + 1)
                            .append("\n")

                        output.append("Typ: ")
                            .append(item.optString("type"))
                            .append("\n")

                        output.append("Rozmiar: ")
                            .append(item.optInt("width"))
                            .append(" x ")
                            .append(item.optInt("height"))
                            .append("\n")

                        output.append("Pozycja: ")
                            .append(item.optInt("x"))
                            .append(", ")
                            .append(item.optInt("y"))
                            .append("\n")

                        val alt = item.optString("alt")

                        if (alt.isNotBlank()) {
                            output.append("Opis: ")
                                .append(alt)
                                .append("\n")
                        }

                        val url = item.optString("url")

                        if (url.isNotBlank()) {
                            output.append("Adres: ")
                                .append(url)
                                .append("\n")
                        }

                        output.append("\n")
                    }
                }

                scanStatus.text = output.toString()

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd odczytu skanowania:\n${e.message}\n\nWynik:\n$result"
            }

            updateStatus()
        }
    }

    private fun getActiveWebView(): WebView {
        val popup = popupWebView

        return if (
            popup != null &&
            popup.visibility == View.VISIBLE
        ) {
            popup
        } else {
            webView
        }
    }

    private fun configureWebView(view: WebView) {

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            mixedContentMode =
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }

        view.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): Boolean {
                return false
            }
        }

        view.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                return createPopup(resultMsg)
            }

            override fun onCloseWindow(window: WebView) {
                closePopup(window)
            }
        }
    }

    private fun createPopup(resultMsg: Message): Boolean {

        popupWebView?.let {
            closePopup(it)
        }

        val popup = WebView(this)

        popupWebView = popup

        configureWebView(popup)

        webContainer.addView(
            popup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        popup.bringToFront()

        val transport =
            resultMsg.obj as WebView.WebViewTransport

        transport.webView = popup

        resultMsg.sendToTarget()

        return true
    }

    private fun closePopup(window: WebView) {

        webContainer.removeView(window)

        window.stopLoading()
        window.destroy()

        if (popupWebView === window) {
            popupWebView = null
        }

        webView.bringToFront()
    }

    private fun showMainPage() {

        popupWebView?.let {
            it.visibility = View.GONE
        }

        webView.visibility = View.VISIBLE
        webView.bringToFront()
    }

    private fun updateStatus() {

        status.text =
            "Zebrane: $collected | Analiza: " +
                if (running) "AKTYWNA" else "WYŁĄCZONA"
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        val popup = popupWebView

        if (
            popup != null &&
            popup.visibility == View.VISIBLE
        ) {

            if (popup.canGoBack()) {
                popup.goBack()
            } else {
                showMainPage()
            }

        } else if (webView.canGoBack()) {

            webView.goBack()

        } else {

            super.onBackPressed()
        }
    }

    override fun onDestroy() {

        stopAnalysis()

        popupWebView?.destroy()
        webView.destroy()

        super.onDestroy()
    }
}
