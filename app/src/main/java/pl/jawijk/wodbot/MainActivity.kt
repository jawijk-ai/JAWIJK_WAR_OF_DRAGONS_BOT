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
        resultTitle.text = "WYKRYTE ELEMENTY GRY:"
        resultTitle.setPadding(10, 5, 10, 5)
        layout.addView(resultTitle)

        scanStatus = TextView(this)
        scanStatus.text = "Oczekiwanie na skanowanie..."
        scanStatus.textSize = 11f
        scanStatus.setPadding(10, 5, 10, 5)
        scanStatus.maxLines = 20
        scanStatus.isTextSelectable = true

        layout.addView(
            scanStatus,
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

                const elements = Array.from(
                    document.querySelectorAll(
                        'a, button, img, [role="button"], ' +
                        '[onclick], [style*="background"]'
                    )
                );

                const results = [];

                elements.forEach(function(el) {

                    const r = el.getBoundingClientRect();
                    const s = getComputedStyle(el);

                    if (
                        r.width <= 10 ||
                        r.height <= 10 ||
                        s.display === 'none' ||
                        s.visibility === 'hidden' ||
                        s.opacity === '0' ||
                        r.bottom <= 0 ||
                        r.right <= 0 ||
                        r.top >= window.innerHeight ||
                        r.left >= window.innerWidth
                    ) {
                        return;
                    }

                    const bg = s.backgroundImage;

                    results.push({

                        tag: el.tagName,

                        text: (
                            el.innerText || ''
                        ).trim().slice(0, 40),

                        title: el.title || '',

                        alt: el.alt || '',

                        src: el.currentSrc || el.src || '',

                        background: (
                            bg && bg !== 'none'
                            ? bg.slice(0, 300)
                            : ''
                        ),

                        className: String(
                            el.className || ''
                        ).slice(0, 100),

                        x: Math.round(r.x),

                        y: Math.round(r.y),

                        width: Math.round(r.width),

                        height: Math.round(r.height)

                    });

                });

                return JSON.stringify(
                    results.slice(0, 80)
                );

            })();
        """.trimIndent()

        activeView.evaluateJavascript(script) { result ->

            scanInProgress = false

            if (!running) return@evaluateJavascript

            scanCount++

            val decoded = try {

                JSONTokener(result ?: "null").nextValue()
                    as? String ?: "Brak wyników"

            } catch (e: Exception) {

                result ?: "Brak wyników"
            }

            val displayResult = decoded.take(4500)

            scanStatus.text =
                "Skan: $scanCount\n" +
                "Elementy strony:\n" +
                displayResult

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
