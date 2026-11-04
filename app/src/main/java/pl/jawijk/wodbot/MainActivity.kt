package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.graphics.Color

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView

    private var running = false
    private var collected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "JAWIJK WAR OF DRAGONS"
        title.textSize = 20f
        title.setTextColor(Color.BLACK)
        title.setPadding(16, 16, 16, 8)
        layout.addView(title)

        status = TextView(this)
        status.text = "Zebrane: 0 | Status: STOP"
        status.setTextColor(Color.BLACK)
        status.setPadding(16, 8, 16, 8)
        layout.addView(status)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL

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

        buttons.addView(startButton,
            LinearLayout.LayoutParams(0, -2, 1f))

        buttons.addView(stopButton,
            LinearLayout.LayoutParams(0, -2, 1f))

        layout.addView(buttons)

        val addButton = Button(this)
        addButton.text = "DODAJ ZEBRANY SUROWIEC"
        addButton.setOnClickListener {
            collected++
            updateStatus()
        }
        layout.addView(addButton)

        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode =
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString =
                WebSettings.getDefaultUserAgent(this@MainActivity)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()

        layout.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)

        webView.loadUrl("https://www.warofdragons.com/")
    }

    private fun updateStatus() {
        status.text =
            "Zebrane: $collected | Status: " +
            if (running) "START" else "STOP"
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }
}
