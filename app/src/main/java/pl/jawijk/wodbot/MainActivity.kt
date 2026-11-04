package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var webContainer: FrameLayout

    private var popupWebView: WebView? = null
    private var running = false
    private var collected = 0

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
        status.text = "Zebrane: 0 | STOP"
        status.setPadding(10, 5, 10, 5)
        layout.addView(status)

        val buttons = LinearLayout(this)

        val startButton = Button(this)
        startButton.text = "START"
        startButton.setOnClickListener {
            running = true
            updateStatus()
        }

        val stopButton = Button(this)
        stopButton.text = "STOP"
        stopButton.setOnClickListener {
            running = false
            updateStatus()
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

        val addButton = Button(this)
        addButton.text = "DODAJ SUROWIEC"
        addButton.setOnClickListener {
            collected++
            updateStatus()
        }

        layout.addView(addButton)

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

        webView.loadUrl("https://www.warofdragons.com/")
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

        if (popupWebView != null) {
            closePopup(popupWebView!!)
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

    private fun updateStatus() {

        status.text =
            "Zebrane: $collected | Status: " +
            if (running) "START" else "STOP"
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        val popup = popupWebView

        if (popup != null) {

            if
