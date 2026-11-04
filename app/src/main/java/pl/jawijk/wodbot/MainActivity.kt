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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import org.json.JSONTokener

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
        scanStatus.maxLines = 35
        scanStatus.isTextSelectable = true

        layout.addView(
            scanStatus,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                220
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

        scanStatus.text = "Analiza ikon uruchomiona."

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

            const results = [];
            const seen = new Set();

            function visible(el) {

                const r = el.getBoundingClientRect();
                const s = getComputedStyle(el);

                return (
                    r.width > 10 &&
                    r.height > 10 &&
                    s.display !== 'none' &&
                    s.visibility !== 'hidden' &&
                    s.opacity !== '0' &&
                    r.bottom > 0 &&
                    r.right > 0 &&
                    r.top < window.innerHeight &&
                    r.left < window.innerWidth
                );
            }

            function addElement(el, sourceType, sourceUrl) {

                if (!el || !visible(el)) return;

                const r = el.getBoundingClientRect();
                const s = getComputedStyle(el);

                const width = Math.round(r.width);
                const height = Math.round(r.height);

                if (width < 15 || height < 15) return;

                const ratio = width / height;

                // Interesują nas głównie ikony,
                // a nie duże zdjęcia i banery.
                if (
                    width > 500 ||
                    height > 500 ||
                    ratio < 0.45 ||
                    ratio > 2.2
                ) return;

                const url = sourceUrl || '';

                const key = [
                    sourceType,
                    url,
                    Math.round(r.x),
                    Math.round(r.y),
                    width,
                    height
                ].join('|');

                if (seen.has(key)) return;

                seen.add(key);

                results.push({

                    type: sourceType,

                    tag: el.tagName,

                    url: url.substring(0, 350),

                    alt: el.alt || '',

                    title: el.title || '',

                    className: String(
                        el.className || ''
                    ).substring(0, 100),

                    x: Math.round(r.x),

                    y: Math.round(r.y),

                    width: width,

                    height: height

                });
            }

            function scanDocument(doc) {

                if (!doc || !doc.body) return;

                // Obrazki IMG
                doc.querySelectorAll('img').forEach(function(el) {

                    addElement(
                        el,
                        'IMG',
                        el.currentSrc || el.src || ''
                    );

                });

                // Elementy SVG
                doc.querySelectorAll('svg').forEach(function(el) {

                    addElement(
                        el,
                        'SVG',
                        ''
                    );

                });

                // Elementy z obrazkiem w tle
                doc.querySelectorAll('*').forEach(function(el) {

                    const style = getComputedStyle(el);
                    const bg = style.backgroundImage;

                    if (bg && bg !== 'none') {

                        addElement(
                            el,
                            'BACKGROUND',
                            bg
                        );
                    }

                    // Elementy, które mogą być ikonami
                    if (
                        el.tagName === 'CANVAS' ||
                        el.getAttribute('role') === 'button' ||
                        el.hasAttribute('onclick')
                    ) {

                        addElement(
                            el,
                            el.tagName,
                            ''
                        );
                    }

                });

                // Dostępne ramki iframe
                doc.querySelectorAll('iframe').forEach(function(frame) {

                    try {

                        const frameDoc =
                            frame.contentDocument ||
                            frame.contentWindow.document;

                        scanDocument(frameDoc);

                    } catch(e) {

                        // Nie można odczytać obcej domeny.
                    }

                });

            }

            scanDocument(document);

            results.sort(function(a, b) {

                return (a.width * a.height) -
                       (b.width * b.height);

            });

            return JSON.stringify({

                total: results.length,

                images: results.slice(0, 100)

            });

        })();
        """.trimIndent()

        activeView.evaluateJavascript(script) { result ->

            scanInProgress = false

            if (!running) return@evaluateJavascript

            scanCount++

            val decoded = try {

                JSONTokener(result ?: "null").nextValue()
                    as? String ?: ""

            } catch (e: Exception) {

                ""
            }

            if (decoded.isBlank()) {

                scanStatus.text =
                    "Skan: $scanCount\n" +
                    "Nie udało się odczytać wyników."

                updateStatus()
                return@evaluateJavascript
            }

            try {

                val json = org.json.JSONObject(decoded)

                val total = json.optInt("total", 0)
                val images = json.optJSONArray("images")

                val output = StringBuilder()

                output.append("Skan: $scanCount\n")
                output.append("Wykryte elementy: $total\n\n")

                if (images == null || images.length() == 0) {

                    output.append(
                        "Nie znaleziono ikon.\n"
                    )

                    output.append(
                        "Możliwe, że gra używa Canvas lub WebGL."
                    )

                } else {

                    for (i in 0 until images.length()) {

                        val item = images.getJSONObject(i)

                        output.append("ELEMENT ${i + 1}\n")

                        output.append(
                            "Typ: ${item.optString("type")}\n"
                        )

                        output.append(
                            "Rozmiar: " +
                            "${item.optInt("width")} x " +
                            "${item.optInt("height")}\n"
                        )

                        output.append(
                            "Pozycja: " +
                            "${item.optInt("x")}, " +
                            "${item.optInt("y")}\n"
                        )

                        val alt = item.optString("alt")
                        if (alt.isNotBlank()) {
                            output.append("Alt: $alt\n")
                        }

                        val url = item.optString("url")
                        if (url.isNotBlank()) {
                            output.append("Adres: $url\n")
                        }

                        output.append("\n")
                    }
                }

                scanStatus.text = output.toString().take(6000)

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd odczytu wyników:\n${e.message}"
            }

            updateStatus()
        }
    }

    private fun getActiveWebView(): WebView {

        val popup = popupWebView

        if (popup != null &&
            popup.visibility == View.VISIBLE
        ) {
            return popup
        }

        return webView
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

        val popup = popupWebView

        if (popup != null) {

            popup.visibility = View.GONE

            webView.visibility = View.VISIBLE
            webView.bringToFront()
        }
    }

    private fun updateStatus() {

        status.text =
            "Zebrane: $collected | Analiza: " +
            if (running) "AKTYWNA" else "WYŁĄCZONA"
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        val popup = popupWebView

        if (popup != null &&
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
