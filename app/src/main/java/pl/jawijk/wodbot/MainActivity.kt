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

    private val handler = Handler(Looper.getMainLooper())

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running) {
                scanPage()
                handler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(8, 8, 8, 8)

        status = TextView(this)
        status.text = "JAWIJK WOD – GOTOWY"
        status.textSize = 16f
        status.setPadding(10, 10, 10, 10)
        layout.addView(status)

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

        this.scanStatus = TextView(this)
        this.scanStatus.text = "Oczekiwanie na skanowanie..."
        this.scanStatus.textSize = 11f
        this.scanStatus.setPadding(10, 5, 10, 5)
        this.scanStatus.isTextSelectable = true

        val resultScroll = ScrollView(this)
        resultScroll.addView(this.scanStatus)

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

        setContentView(layout)

        configureWebView()

        webView.loadUrl("https://www.warofdragons.com/")
    }

    private fun configureWebView() {

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
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
                status.text = "Strona załadowana"
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

                CookieManager.getInstance()
                    .setAcceptCookie(true)

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

                // Poprawione przypisanie WebView
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
        scanCount = 0

        scanStatus.text = "Rozpoczynam analizę strony..."
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

        if (!running) return

        val javascript = """
            (function() {

                var elements = [];

                var found = document.querySelectorAll(
                    'a, button, [role="button"], [onclick], img, svg, canvas, [style*="background"]'
                );

                for (var i = 0; i < found.length; i++) {

                    var el = found[i];
                    var rect = el.getBoundingClientRect();

                    if (
                        rect.width <= 0 ||
                        rect.height <= 0 ||
                        rect.bottom <= 0 ||
                        rect.right <= 0 ||
                        rect.top >= window.innerHeight ||
                        rect.left >= window.innerWidth
                    ) {
                        continue;
                    }

                    elements.push({
                        tag: el.tagName,
                        text: (el.innerText || el.textContent || "").trim().substring(0, 80),
                        title: el.getAttribute("title") || "",
                        alt: el.getAttribute("alt") || "",
                        src: el.getAttribute("src") || "",
                        className: typeof el.className === "string"
                            ? el.className.substring(0, 100)
                            : "",
                        width: Math.round(rect.width),
                        height: Math.round(rect.height)
                    });

                    if (elements.length >= 40) break;
                }

                return {
                    url: location.href,
                    count: elements.length,
                    items: elements
                };

            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->

            if (!running || result == null || result == "null") {
                return@evaluateJavascript
            }

            try {

                val data = JSONObject(result)
                val count = data.optInt("count", 0)
                val items = data.optJSONArray("items")

                val output = StringBuilder()

                scanCount++

                output.append("SKAN: $scanCount\n")
                output.append("WYKRYTE ELEMENTY: $count\n\n")

                if (items != null) {

                    for (i in 0 until items.length()) {

                        val item = items.getJSONObject(i)

                        output.append("ELEMENT ${i + 1}\n")
                        output.append("Typ: ${item.optString("tag")}\n")
                        output.append("Tekst: ${item.optString("text")}\n")
                        output.append("Nazwa: ${item.optString("title")}\n")
                        output.append("Obraz: ${item.optString("alt")}\n")
                        output.append("Adres: ${item.optString("src")}\n")
                        output.append(
                            "Rozmiar: ${item.optInt("width")} x ${item.optInt("height")}\n"
                        )
                        output.append("--------------------\n")
                    }

                } else {
                    output.append("Nie znaleziono widocznych elementów.")
                }

                scanStatus.text = output.toString()
                status.text = "ANALIZA: $scanCount | ELEMENTY: $count"

            } catch (e: Exception) {

                scanStatus.text = "Błąd odczytu: ${e.message}"
            }
        }
    }

    private fun updateStatus() {
        status.text = "ZEBRANE SUROWCE: $collected"
    }

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
