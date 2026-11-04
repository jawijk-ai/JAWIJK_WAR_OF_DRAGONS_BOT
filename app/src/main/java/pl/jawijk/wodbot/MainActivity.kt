package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

        val title = TextView(this)
        title.text = "JAWIJK WAR OF DRAGONS"
        title.textSize = 20f
        title.setPadding(12, 12, 12, 8)
        layout.addView(title)

        status = TextView(this)
        status.text = "Zebrane: 0 | STOP"
        status.setPadding(12, 8, 12, 8)
        layout.addView(status)

        val buttons = LinearLayout(this)

        val start = Button(this)
        start.text = "START"
        start.setOnClickListener {
            running = true
            updateStatus()
        }

        val stop = Button(this)
        stop.text = "STOP"
        stop.setOnClickListener {
            running = false
            updateStatus()
        }

        buttons.addView(start, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(buttons)

        val add = Button(this)
        add.text = "DODAJ SUROWIEC"
        add.setOnClickListener {
            collected++
            updateStatus()
        }
        layout.addView(add)

        webView = WebView(this)

        webView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isLongClickable = true
            isVerticalScrollBarEnabled = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                mixedContentMode =
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.performClick()
                    MotionEvent.ACTION_UP -> view.performClick()
                }
                false
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        layout.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)

        webView.requestFocus()
        webView.loadUrl("https://www.warofdragons.com/")
    }

    private fun updateStatus() {
        status.text =
            "Zebrane: $collected | " +
            if (running) "START" else "STOP"
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }
}
