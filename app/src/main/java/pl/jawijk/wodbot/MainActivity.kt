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
import android.widget.*
import android.graphics.Color
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

data class FishResource(
    val name: String,
    val details: String = ""
)

class MainActivity : Activity() {

    private lateinit var gameWebView: WebView
    private lateinit var catalogView: WebView
    private lateinit var gameFrame: FrameLayout

    private lateinit var scanStatus: TextView
    private lateinit var fishSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var timeSpinner: Spinner

    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var popupWebView: WebView? = null
    private var popupContainer: FrameLayout? = null

    private val handler = Handler(Looper.getMainLooper())

    private var scanInterval = 30000L
    private var analysisRunning = false

    private var selectedCategory = "Ryby podstawowe"
    private var selectedFish = ""

    private val fishList = mutableListOf<FishResource>()

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!analysisRunning) return

            scanPage()

            handler.postDelayed(this, scanInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        createInterface()
        configureWebView()

        loadFishCatalog()

        gameWebView.loadUrl(
            "https://www.warofdragons.com/"
        )
    }

    private fun createInterface() {

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setBackgroundColor(Color.rgb(25, 25, 25))

        val title = TextView(this)
        title.text = "JAWIJK WOD BOT"
        title.textSize = 21f
        title.setTextColor(Color.WHITE)
        title.setPadding(10, 12, 10, 12)
        title.gravity = android.view.Gravity.CENTER

        mainLayout.addView(title)

        categorySpinner = Spinner(this)

        val categories = arrayOf(
            "Ryby podstawowe",
            "Ryby Mystras"
        )

        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedCategory = categories[position]
                    showFishList()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        mainLayout.addView(categorySpinner)

        fishSpinner = Spinner(this)
        mainLayout.addView(fishSpinner)

        timeSpinner = Spinner(this)

        val times = arrayOf(
            "1 sekunda",
            "5 sekund",
            "10 sekund",
            "15 sekund",
            "30 sekund",
            "60 sekund",
            "2 minuty",
            "5 minut"
        )

        timeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            times
        )

        timeSpinner.setSelection(4)

        timeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val values = longArrayOf(
                        1000L,
                        5000L,
                        10000L,
                        15000L,
                        30000L,
                        60000L,
                        120000L,
                        300000L
                    )

                    scanInterval = values[position]

                    if (::scanStatus.isInitialized) {
                        scanStatus.text =
                            "Odstęp analizy: ${times[position]}"
                    }

                    if (analysisRunning) {
                        handler.removeCallbacks(scanRunnable)
                        handler.post(scanRunnable)
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        mainLayout.addView(timeSpinner)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL

        startButton = Button(this)
        startButton.text = "START"

        stopButton = Button(this)
        stopButton.text = "STOP"

        startButton.setOnClickListener {
            startAnalysis()
        }

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

        mainLayout.addView(buttons)

        scanStatus = TextView(this)
        scanStatus.text = "Oczekiwanie na skanowanie..."
        scanStatus.textSize = 13f
        scanStatus.setTextColor(Color.GREEN)
        scanStatus.setPadding(10, 8, 10, 8)
        scanStatus.setTextIsSelectable(true)

        mainLayout.addView(scanStatus)

        val zoomButtons = LinearLayout(this)
        zoomButtons.orientation = LinearLayout.HORIZONTAL

        val zoomOut = Button(this)
        zoomOut.text = "−"

        val zoomIn = Button(this)
        zoomIn.text = "+"

        zoomOut.setOnClickListener {
            gameWebView.zoomOut()
        }

        zoomIn.setOnClickListener {
            gameWebView.zoomIn()
        }

        zoomButtons.addView(
            zoomOut,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        zoomButtons.addView(
            zoomIn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        mainLayout.addView(zoomButtons)

        gameFrame = FrameLayout(this)

        gameWebView = WebView(this)

        gameFrame.addView(
            gameWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        mainLayout.addView(
            gameFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        catalogView = WebView(this)
        catalogView.visibility = View.GONE

        mainLayout.addView(
            catalogView,
            LinearLayout.LayoutParams(1, 1)
        )

        setContentView(mainLayout)
    }
        private fun configureWebView() {

        val settings = gameWebView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.allowFileAccess = true
        settings.allowContentAccess = true

        gameWebView.isFocusable = true
        gameWebView.isFocusableInTouchMode = true
        gameWebView.isClickable = true

        gameWebView.setBackgroundColor(Color.BLACK)

        gameWebView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                scanStatus.text = "Strona załadowana."
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                return false
            }
        }

        gameWebView.webChromeClient = createGameChromeClient()
    }

    private fun createGameChromeClient(): WebChromeClient {

        return object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {

                if (popupWebView != null) {
                    closePopup()
                }

                val popup = WebView(this@MainActivity)

                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.databaseEnabled = true
                popup.settings.loadsImagesAutomatically = true
                popup.settings.javaScriptCanOpenWindowsAutomatically = true
                popup.settings.setSupportMultipleWindows(true)

                popup.settings.useWideViewPort = true
                popup.settings.loadWithOverviewMode = true

                popup.settings.builtInZoomControls = true
                popup.settings.displayZoomControls = false

                popup.isFocusable = true
                popup.isFocusableInTouchMode = true
                popup.isClickable = true

                popup.webViewClient = WebViewClient()

                popup.webChromeClient = createGameChromeClient()

                val container = FrameLayout(this@MainActivity)

                container.setBackgroundColor(Color.WHITE)

                container.addView(
                    popup,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                val closeButton = Button(this@MainActivity)
                closeButton.text = "ZAMKNIJ"

                val closeParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                closeParams.gravity =
                    android.view.Gravity.TOP or
                    android.view.Gravity.END

                container.addView(closeButton, closeParams)

                closeButton.setOnClickListener {
                    closePopup()
                }

                gameFrame.addView(
                    container,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                container.bringToFront()

                popupWebView = popup
                popupContainer = container

                val transport =
                    resultMsg?.obj as? WebView.WebViewTransport

                if (transport == null) {
                    closePopup()
                    return false
                }

                transport.webView = popup
                resultMsg.sendToTarget()

                return true
            }

            override fun onCloseWindow(window: WebView?) {
                closePopup()
            }
        }
    }

    private fun closePopup() {

        val popup = popupWebView
        val container = popupContainer

        popupWebView = null
        popupContainer = null

        try {
            popup?.stopLoading()
            popup?.destroy()
        } catch (e: Exception) {
        }

        try {
            if (container != null) {
                gameFrame.removeView(container)
            }
        } catch (e: Exception) {
        }
    }

    private fun loadFishCatalog() {

        scanStatus.text = "Pobieranie biblioteki ryb..."

        catalogView.settings.javaScriptEnabled = true
        catalogView.settings.domStorageEnabled = true

        catalogView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                readFishCatalog()
            }
        }

        catalogView.loadUrl(
            "https://warofdragons.com/info/library/index.php?id=351&obj=cat"
        )
    }

    private fun readFishCatalog() {

        val js = """
            (function() {

                let result = [];

                let rows = document.querySelectorAll(
                    "tr, .item, .object, .library-item"
                );

                rows.forEach(function(row) {

                    let name = (
                        row.innerText ||
                        row.textContent ||
                        ""
                    ).trim();

                    name = name.replace(/\s+/g, " ");

                    if (name.length > 2 && name.length < 180) {
                        result.push({
                            name: name,
                            details: name
                        });
                    }
                });

                if (result.length === 0) {

                    document.querySelectorAll("a").forEach(function(a) {

                        let name = (
                            a.innerText ||
                            a.textContent ||
                            a.getAttribute("title") ||
                            ""
                        ).trim();

                        if (name.length > 2 && name.length < 100) {
                            result.push({
                                name: name,
                                details: name
                            });
                        }
                    });
                }

                return JSON.stringify(result);

            })();
        """

        catalogView.evaluateJavascript(js) { result ->

            try {

                val decoded =
                    JSONArray("[$result]").getString(0)

                val array = JSONArray(decoded)

                fishList.clear()

                val uniqueNames = mutableSetOf<String>()

                for (i in 0 until array.length()) {

                    val item = array.getJSONObject(i)

                    val name =
                        item.optString("name", "").trim()

                    val details =
                        item.optString("details", "").trim()

                    if (
                        name.isNotEmpty() &&
                        uniqueNames.add(name.lowercase())
                    ) {
                        fishList.add(
                            FishResource(
                                name = name,
                                details = details
                            )
                        )
                    }
                }

                showFishList()

                scanStatus.text =
                    "Biblioteka pobrana. Pozycje: ${fishList.size}"

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd biblioteki: ${e.message}"
            }
        }
    }

    private fun showFishList() {

        if (!::fishSpinner.isInitialized) return

        val filtered = if (
            selectedCategory == "Ryby Mystras"
        ) {

            fishList.filter {
                it.name.contains("Mystras", ignoreCase = true) ||
                it.details.contains("Mystras", ignoreCase = true)
            }

        } else {

            fishList.filter {
                !it.name.contains("Mystras", ignoreCase = true) &&
                !it.details.contains("Mystras", ignoreCase = true)
            }
        }

        val names = if (filtered.isEmpty()) {
            listOf("Brak pozycji w tej kategorii")
        } else {
            filtered.map { it.name }
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        fishSpinner.adapter = adapter

        fishSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (filtered.isNotEmpty()) {

                        selectedFish = filtered[position].name

                        scanStatus.text =
                            "Wybrany surowiec: $selectedFish"

                    } else {

                        selectedFish = ""
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }
    }

    private fun startAnalysis() {

        if (analysisRunning) return

        analysisRunning = true

        scanStatus.text =
            "ANALIZA URUCHOMIONA\nWybrany surowiec: $selectedFish"

        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
    }

    private fun stopAnalysis() {

        analysisRunning = false

        handler.removeCallbacks(scanRunnable)

        clearHighlights()

        scanStatus.text = "Analiza zatrzymana."
    }
        private fun scanPage() {

        val js = """
            (function() {

                let images = [];
                let canvases = 0;
                let frames = 0;

                function search(doc) {

                    try {

                        let imgs = doc.querySelectorAll("img");

                        imgs.forEach(function(img) {

                            if (
                                img.complete &&
                                img.naturalWidth > 0 &&
                                img.naturalHeight > 0
                            ) {

                                let r = img.getBoundingClientRect();

                                if (
                                    r.width > 1 &&
                                    r.height > 1
                                ) {
                                    images.push(img);
                                }
                            }
                        });

                        canvases +=
                            doc.querySelectorAll("canvas").length;

                        let iframeList =
                            doc.querySelectorAll("iframe");

                        iframeList.forEach(function(frame) {

                            frames++;

                            try {
                                if (frame.contentDocument) {
                                    search(frame.contentDocument);
                                }
                            } catch(e) {}
                        });

                    } catch(e) {}
                }

                search(document);

                if (images.length === 0) {

                    return JSON.stringify({
                        count: 0,
                        canvases: canvases,
                        frames: frames,
                        marked: false
                    });
                }

                let index = Math.floor(
                    Math.random() * images.length
                );

                let img = images[index];

                try {
                    img.scrollIntoView({
                        behavior: "smooth",
                        block: "center",
                        inline: "center"
                    });
                } catch(e) {}

                let oldOutline = img.style.outline;
                let oldShadow = img.style.boxShadow;

                function flash() {

                    img.style.setProperty(
                        "outline",
                        "5px solid red",
                        "important"
                    );

                    img.style.setProperty(
                        "box-shadow",
                        "0 0 20px red",
                        "important"
                    );
                }

                function clear() {

                    img.style.outline = oldOutline;
                    img.style.boxShadow = oldShadow;
                }

                flash();

                setTimeout(function() {
                    clear();
                }, 200);

                setTimeout(function() {
                    flash();
                }, 400);

                setTimeout(function() {
                    clear();
                }, 800);

                return JSON.stringify({

                    count: images.length,
                    canvases: canvases,
                    frames: frames,
                    marked: true,
                    selected: index + 1

                });

            })();
        """

        gameWebView.evaluateJavascript(js) { result ->

            try {

                if (result == null || result == "null") {

                    scanStatus.text =
                        "Brak odpowiedzi skanera."

                    return@evaluateJavascript
                }

                val decoded =
                    JSONArray("[$result]").getString(0)

                val data = JSONObject(decoded)

                val count = data.optInt("count")
                val canvases = data.optInt("canvases")
                val frames = data.optInt("frames")
                val marked = data.optBoolean("marked")

                if (marked) {

                    scanStatus.text =
                        "ANALIZA ZAKOŃCZONA\n" +
                        "Znalezione obrazki: $count\n" +
                        "Ramki iframe: $frames\n" +
                        "Elementy Canvas: $canvases\n" +
                        "Oznaczony obraz: ${data.optInt("selected")}\n" +
                        "Wybrany surowiec: $selectedFish"

                } else {

                    scanStatus.text =
                        "NIE ZNALEZIONO OBRAZKÓW\n" +
                        "Znalezione obrazki: 0\n" +
                        "Ramki iframe: $frames\n" +
                        "Elementy Canvas: $canvases\n" +
                        "Wybrany surowiec: $selectedFish"
                }

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd analizy: ${e.message}"
            }
        }
    }

    private fun clearHighlights() {

        val js = """
            (function() {

                document.querySelectorAll("img").forEach(function(img) {

                    img.style.removeProperty("outline");
                    img.style.removeProperty("box-shadow");

                });

                return true;

            })();
        """

        gameWebView.evaluateJavascript(js, null)
        popupWebView?.evaluateJavascript(js, null)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        val popup = popupWebView

        if (popup != null && popup.canGoBack()) {
            popup.goBack()
            return
        }

        if (popup != null) {
            closePopup()
            return
        }

        if (gameWebView.canGoBack()) {
            gameWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {

        analysisRunning = false

        handler.removeCallbacks(scanRunnable)

        closePopup()

        if (::gameWebView.isInitialized) {
            clearHighlights()
            gameWebView.destroy()
        }

        if (::catalogView.isInitialized) {
            catalogView.destroy()
        }

        super.onDestroy()
    }
}
