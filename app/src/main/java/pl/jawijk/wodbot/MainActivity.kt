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

                var all = document.querySelectorAll('*');
                var images = document.querySelectorAll('img');
                var canvases = document.querySelectorAll('canvas');
                var svgs = document.querySelectorAll('svg');
                var iframes = document.querySelectorAll('iframe');
                var selects = document.querySelectorAll('select');

                var visible = 0;
                var clickable = 0;
                var candidates = [];
                var imageInfo = [];
                var canvasInfo = [];
                var svgInfo = [];
                var selectInfo = [];

                function isVisible(el) {
                    var r = el.getBoundingClientRect();
                    var s = window.getComputedStyle(el);

                    return r.width > 0 &&
                           r.height > 0 &&
                           s.display !== 'none' &&
                           s.visibility !== 'hidden' &&
                           s.opacity !== '0';
                }

                for (var i = 0; i < all.length; i++) {

                    var el = all[i];

                    if (isVisible(el)) visible++;

                    if (
                        el.tagName === 'BUTTON' ||
                        el.tagName === 'A' ||
                        el.onclick ||
                        el.getAttribute('role') === 'button'
                    ) {
                        clickable++;
                    }

                    if (candidates.length < 30) {

                        var text = (
                            (el.innerText || '') + ' ' +
                            (el.title || '') + ' ' +
                            (el.getAttribute('alt') || '') + ' ' +
                            (el.id || '') + ' ' +
                            (typeof el.className === 'string' ? el.className : '')
                        ).trim();

                        if (
                            text &&
                            /ryb|fish|surow|resource|poziom|level|woda/i.test(text)
                        ) {
                            candidates.push({
                                tag: el.tagName,
                                text: text.substring(0, 120),
                                id: el.id || '',
                                className: typeof el.className === 'string'
                                    ? el.className.substring(0, 100)
                                    : ''
                            });
                        }
                    }
                }

                for (var j = 0; j < images.length; j++) {

                    var img = images[j];
                    var rect = img.getBoundingClientRect();

                    imageInfo.push({
                        src: (img.src || '').substring(0, 180),
                        width: img.naturalWidth,
                        height: img.naturalHeight,
                        visible: isVisible(img),
                        screenWidth: Math.round(rect.width),
                        screenHeight: Math.round(rect.height)
                    });
                }

                for (var k = 0; k < canvases.length; k++) {

                    var canvas = canvases[k];
                    var rect = canvas.getBoundingClientRect();

                    canvasInfo.push({
                        width: canvas.width,
                        height: canvas.height,
                        screenWidth: Math.round(rect.width),
                        screenHeight: Math.round(rect.height),
                        visible: isVisible(canvas),
                        id: canvas.id || '',
                        className: typeof canvas.className === 'string
                            ? canvas.className
                            : ''
                    });
                }

                for (var m = 0; m < svgs.length; m++) {

                    var svg = svgs[m];
                    var rect = svg.getBoundingClientRect();

                    svgInfo.push({
                        width: Math.round(rect.width),
                        height: Math.round(rect.height),
                        visible: isVisible(svg),
                        id: svg.id || ''
                    });
                }

                for (var n = 0; n < selects.length; n++) {

                    var select = selects[n];
                    var options = [];

                    for (var p = 0; p < select.options.length; p++) {
                        options.push({
                            text: select.options[p].text,
                            value: select.options[p].value,
                            selected: select.options[p].selected
                        });
                    }

                    selectInfo.push({
                        name: select.name || '',
                        id: select.id || '',
                        value: select.value || '',
                        options: options
                    });
                }

                var bodyText = document.body
                    ? (document.body.innerText || '').substring(0, 600)
                    : '';

                var resources = performance.getEntriesByType('resource');
                var resourceUrls = [];

                for (var q = 0; q < resources.length && resourceUrls.length < 20; q++) {

                    var url = resources[q].name || '';

                    if (/game|map|fish|resource|image|sprite|webgl|canvas/i.test(url)) {
                        resourceUrls.push(url.substring(0, 180));
                    }
                }

                return {
                    url: location.href,
                    title: document.title,
                    ready: document.readyState,

                    totalElements: all.length,
                    visibleElements: visible,
                    clickableElements: clickable,

                    imageCount: images.length,
                    images: imageInfo,

                    canvasCount: canvases.length,
                    canvases: canvasInfo,

                    svgCount: svgs.length,
                    svgs: svgInfo,

                    iframeCount: iframes.length,
                    selectCount: selects.length,
                    selects: selectInfo,

                    candidates: candidates,
                    bodyText: bodyText,
                    resources: resourceUrls
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

                output.append("SKAN NR: $scanCount\n")
                output.append("ANALIZA STRONY ZAKONCZONA\n\n")

                output.append("STRONA:\n")
                output.append("Tytuł: ${data.optString("title")}\n")
                output.append("Stan: ${data.optString("ready")}\n")
                output.append("Adres: ${data.optString("url")}\n\n")

                output.append("ELEMENTY STRONY:\n")
                output.append("Wszystkie: ${data.optInt("totalElements")}\n")
                output.append("Widoczne: ${data.optInt("visibleElements")}\n")
                output.append("Klikalne: ${data.optInt("clickableElements")}\n\n")

                output.append("OBRAZY:\n")
                output.append("Liczba obrazów: ${data.optInt("imageCount")}\n")

                val images = data.optJSONArray("images")

                if (images != null) {
                    for (i in 0 until images.length()) {

                        val img = images.getJSONObject(i)

                        output.append(
                            "${i + 1}. ${img.optInt("width")}x${img.optInt("height")} " +
                            "widoczny: ${img.optBoolean("visible")}\n"
                        )

                        output.append(
                            "   ${img.optString("src")}\n"
                        )
                    }
                }

                output.append("\nOBSZARY CANVAS:\n")
                output.append("Liczba: ${data.optInt("canvasCount")}\n")

                val canvases = data.optJSONArray("canvases")

                if (canvases != null) {
                    for (i in 0 until canvases.length()) {

                        val canvas = canvases.getJSONObject(i)

                        output.append(
                            "${i + 1}. ${canvas.optInt("width")}x" +
                            "${canvas.optInt("height")} " +
                            "widoczny: ${canvas.optBoolean("visible")}\n"
                        )
                    }
                }
                           output.append("\nELEMENTY SVG:\n")
                output.append("Liczba: ${data.optInt("svgCount")}\n")

                output.append("\nRAMKI I LISTY:\n")
                output.append("IFRAME: ${data.optInt("iframeCount")}\n")
                output.append("LISTY: ${data.optInt("selectCount")}\n")

                val selects = data.optJSONArray("selects")

                if (selects != null) {

                    for (i in 0 until selects.length()) {

                        val select = selects.getJSONObject(i)

                        output.append(
                            "\nLista ${i + 1}: ${select.optString("name")}\n"
                        )

                        val options = select.optJSONArray("options")

                        if (options != null) {

                            for (j in 0 until options.length()) {

                                val option = options.getJSONObject(j)

                                output.append(
                                    "- ${option.optString("text")} " +
                                    "(${option.optString("value")})\n"
                                )
                            }
                        }
                    }
                }

                output.append("\nMOŻLIWE ELEMENTY SUROWCÓW:\n")

                val candidates = data.optJSONArray("candidates")

                if (candidates != null && candidates.length() > 0) {

                    for (i in 0 until candidates.length()) {

                        val item = candidates.getJSONObject(i)

                        output.append(
                            "${i + 1}. ${item.optString("tag")} " +
                            "${item.optString("text")}\n"
                        )
                    }

                } else {
                    output.append("Nie znaleziono nazw surowców w HTML.\n")
                }

                output.append("\nZASOBY GRY:\n")

                val resources = data.optJSONArray("resources")

                if (resources != null && resources.length() > 0) {

                    for (i in 0 until resources.length()) {
                        output.append(
                            "${i + 1}. ${resources.getString(i)}\n"
                        )
                    }

                } else {
                    output.append("Brak pasujących adresów zasobów.\n")
                }

                output.append("\nTEKST STRONY:\n")
                output.append(data.optString("bodyText"))

                output.append("\n\nKOLEJNY SKAN ZA 20 SEKUND.")

                scanStatus.text = output.toString()
                status.text = "ANALIZA AKTYWNA | SKAN: $scanCount"

            } catch (e: Exception) {

                scanStatus.text = "BŁĄD ANALIZY: ${e.message}"

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
