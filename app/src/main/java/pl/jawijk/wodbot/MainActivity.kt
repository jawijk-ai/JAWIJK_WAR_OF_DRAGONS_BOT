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
    private lateinit var fishName: EditText
    private lateinit var categorySpinner: Spinner

    private var popupWebView: WebView? = null

    private var running = false
    private var collected = 0
    private var scanCount = 0
    private var scanInProgress = false

    private val handler = Handler(Looper.getMainLooper())

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

        val fishTitle = TextView(this)
        fishTitle.text = "NAZWA RYBY DO WYKRYCIA:"
        layout.addView(fishTitle)

        fishName = EditText(this)
        fishName.hint = "Wpisz nazwę ryby"
        fishName.textSize = 14f
        fishName.setSingleLine(true)
        layout.addView(fishName)

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

        val countButton = Button(this)
        countButton.text = "DODAJ POTWIERDZONY POŁÓW"

        countButton.setOnClickListener {
            collected++
            updateStatus()
        }

        layout.addView(countButton)

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

        val selectedFish = fishName.text.toString().trim()

        if (selectedFish.isEmpty()) {
            Toast.makeText(
                this,
                "Najpierw wpisz nazwę ryby",
                Toast.LENGTH_LONG
            ).show()
            return
        }

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
        val selectedFish = fishName.text.toString().trim()

        if (selectedFish.isEmpty()) return

        scanInProgress = true

        val escapedFish = JSONObject.quote(selectedFish)

        val javascript = """
            (function() {

                var target = $escapedFish;
                var targetLower = target.toLowerCase();

                var found = document.querySelectorAll(
                    'a, button, [role="button"], [onclick], img, svg, [style*="background"]'
                );

                var matchedElement = null;
                var matchedData = null;

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

                    var text = (
                        el.innerText ||
                        el.textContent ||
                        ""
                    ).trim();

                    var title = el.getAttribute("title") || "";
                    var alt = el.getAttribute("alt") || "";
                    var src = el.getAttribute("src") || "";

                    var searchable = (
                        text + " " + title + " " + alt + " " + src
                    ).toLowerCase();

                    if (searchable.indexOf(targetLower) !== -1) {

                        matchedElement = el;

                        matchedData = {
                            tag: el.tagName,
                            text: text.substring(0, 80),
                            title: title,
                            alt: alt,
                            src: src
                        };

                        break;
                    }
                }

                var clicked = false;

                if (matchedElement) {

                    var rect = matchedElement.getBoundingClientRect();

                    var x = rect.left + rect.width / 2;
                    var y = rect.top + rect.height / 2;

                    function clickElement() {

                        if (!document.contains(matchedElement)) return;

                        var clickEvents = [
                            "mousedown",
                            "mouseup",
                            "click"
                        ];

                        clickEvents.forEach(function(type) {

                            matchedElement.dispatchEvent(
                                new MouseEvent(type, {
                                    bubbles: true,
                                    cancelable: true,
                                    view: window,
                                    clientX: x,
                                    clientY: y,
                                    button: 0
                                })
                            );

                        });
                    }

                    clickElement();

                    setTimeout(function() {
                        clickElement();
                    }, 150);

                    clicked = true;
                }

                return {
                    target: target,
                    found: matchedElement !== null,
                    clicked: clicked,
                    item: matchedData
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

                val found = data.optBoolean("found")

                val output = StringBuilder()

                output.append("SKAN: $scanCount\n")
                output.append("SZUKANA RYBA: $selectedFish\n")
                output.append(
                    "KATEGORIA: ${categorySpinner.selectedItem}\n\n"
                )

                if (found) {

                    val item = data.optJSONObject("item")

                    output.append("ZNALEZIONO RYBĘ!\n")
                    output.append("Wykryto pasujący element.\n")
                    output.append(
                        "Typ: ${item?.optString("tag")}\n"
                    )
                    output.append(
                        "Tekst: ${item?.optString("text")}\n"
                    )
                    output.append(
                        "Nazwa: ${item?.optString("title")}\n"
                    )
                    output.append(
                        "Obraz: ${item?.optString("alt")}\n"
                    )
                    output.append(
                        "Adres: ${item?.optString("src")}\n"
                    )
                    output.append(
                        "Wysłano dwa kliknięcia JavaScript.\n"
                    )

                } else {

                    output.append(
                        "Nie znaleziono pasującego elementu DOM.\n"
                    )
                    output.append(
                        "Możliwe, że ryba jest rysowana na mapie Canvas."
                    )
                }

                scanStatus.text = output.toString()

                status.text =
                    "SKAN: $scanCount | POŁOWY: $collected"

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd analizy: ${e.message}"

            } finally {

                scanInProgress = false

                if (running) {
                    handler.postDelayed(scanRunnable, 1000)
                }
            }
        }
    }

    private fun updateStatus() {

        status.text =
            "POŁOWY: $collected | SKANY: $scanCount"
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
