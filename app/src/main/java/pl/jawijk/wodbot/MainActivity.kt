package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.graphics.Bitmap
import android.graphics.Canvas
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

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var webContainer: FrameLayout

    private var popupWebView: WebView? = null

    private var running = false
    private var collected = 0

    private val handler = Handler(Looper.getMainLooper())

    private var previousFrame: Bitmap? = null
    private var scanCount = 0

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

        scanStatus = TextView(this)
        scanStatus.text = "Analiza obrazu: wyłączona"
        scanStatus.setPadding(10, 8, 10, 8)
        layout.addView(scanStatus)

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

        previousFrame?.recycle()
        previousFrame = null

        scanStatus.text = "Uruchomiono analizę obrazu co 3 sekundy."

        handler.removeCallbacks(scanTask)
        handler.post(scanTask)

        updateStatus()
    }

    private fun stopAnalysis() {

        running = false

        handler.removeCallbacks(scanTask)

        scanStatus.text = "Analiza obrazu zatrzymana."

        updateStatus()
    }

    private fun analyzeScreen() {

        val activeView = getActiveWebView()

        if (activeView.width <= 0 ||
            activeView.height <= 0
        ) {
            scanStatus.text = "Oczekiwanie na ekran gry..."
            return
        }

        try {

            val bitmap = Bitmap.createBitmap(
                activeView.width,
                activeView.height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            activeView.draw(canvas)

            val currentFrame = Bitmap.createScaledBitmap(
                bitmap,
                64,
                64,
                true
            )

            bitmap.recycle()

            scanCount++

            val oldFrame = previousFrame

            if (oldFrame == null) {

                scanStatus.text =
                    "Pierwszy obraz zapisany. Skan: $scanCount"

            } else {

                val difference = compareFrames(
                    oldFrame,
                    currentFrame
                )

                if (difference > 5) {

                    scanStatus.text =
                        "Wykryto zmianę obrazu: " +
                        "$difference% | Skan: $scanCount"

                } else {

                    scanStatus.text =
                        "Ekran bez większych zmian | " +
                        "Skan: $scanCount"
                }

                oldFrame.recycle()
            }

            previousFrame = currentFrame

        } catch (error: Exception) {

            scanStatus.text =
                "Błąd analizy: ${error.message}"
        }
    }

    private fun compareFrames(
        oldFrame: Bitmap,
        newFrame: Bitmap
    ): Int {

        var changedPixels = 0
        val totalPixels = 64 * 64

        for (y in 0 until 64 step 2) {

            for (x in 0 until 64 step 2) {

                val oldColor = oldFrame.getPixel(x, y)
                val newColor = newFrame.getPixel(x, y)

                val redDifference =
                    kotlin.math.abs(
                        android.graphics.Color.red(oldColor) -
                        android.graphics.Color.red(newColor)
                    )

                val greenDifference =
                    kotlin.math.abs(
                        android.graphics.Color.green(oldColor) -
                        android.graphics.Color.green(newColor)
                    )

                val blueDifference =
                    kotlin.math.abs(
                        android.graphics.Color.blue(oldColor) -
                        android.graphics.Color.blue(newColor)
                    )

                val difference =
                    redDifference +
                    greenDifference +
                    blueDifference

                if (difference > 100) {
                    changedPixels++
                }
            }
        }

        return (changedPixels * 4 * 100) / totalPixels
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

        previousFrame?.recycle()

        popupWebView?.destroy()
        webView.destroy()

        super.onDestroy()
    }
}
