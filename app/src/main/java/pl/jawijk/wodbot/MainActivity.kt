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
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : Activity() {

    private data class FishResource(
        val name: String,
        val skill: String,
        val locations: String,
        val image: String
    )

    private lateinit var webView: WebView
    private lateinit var catalogWebView: WebView
    private lateinit var status: TextView
    private lateinit var scanStatus: TextView
    private lateinit var webContainer: FrameLayout
    private lateinit var fishSpinner: Spinner
    private lateinit var categorySpinner: Spinner

    private var popupWebView: WebView? = null
    private var running = false
    private var collected = 0
    private var scanCount = 0

    private val basicFish = mutableListOf<FishResource>()
    private val mystrasFish = mutableListOf(
        FishResource("Niebieska ikona w wodzie", "-", "Mystras", ""),
        FishResource("Żółta ikona w wodzie", "-", "Mystras", "")
    )

    private val handler = Handler(Looper.getMainLooper())

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (running) {
                scanPage()
                handler.postDelayed(this, 30000)
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
            "Ryby podstawowe – biblioteka",
            "Ryby Mystras"
        )

        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )

        layout.addView(categorySpinner)

        val fishTitle = TextView(this)
        fishTitle.text = "WYBIERZ SUROWIEC:"
        layout.addView(fishTitle)

        fishSpinner = Spinner(this)
        fishSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Ładowanie katalogu...")
        )
        layout.addView(fishSpinner)

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateFishList()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

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

        buttons.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stopButton, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(buttons)

        val zoomButtons = LinearLayout(this)

        val zoomOut = Button(this)
        zoomOut.text = "POMNIEJSZ −"
        zoomOut.setOnClickListener {
            (popupWebView ?: webView).zoomBy(0.8f)
        }

        val zoomIn = Button(this)
        zoomIn.text = "POWIĘKSZ +"
        zoomIn.setOnClickListener {
            (popupWebView ?: webView).zoomBy(1.25f)
        }

        zoomButtons.addView(zoomOut, LinearLayout.LayoutParams(0, -2, 1f))
        zoomButtons.addView(zoomIn, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(zoomButtons)

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

        // Ukryty WebView pobierający katalog z biblioteki gry.
        catalogWebView = WebView(this)
        catalogWebView.visibility = View.INVISIBLE

        layout.addView(
            catalogWebView,
            LinearLayout.LayoutParams(1, 1)
        )

        setContentView(layout)

        configureWebView()
        configureCatalogWebView()

        webView.loadUrl("https://www.warofdragons.com/")
        loadFishCatalog()
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

        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        CookieManager.getInstance().setAcceptCookie(true)

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
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
                popup.settings.setSupportZoom(true)
                popup.settings.builtInZoomControls = true
                popup.settings.displayZoomControls = false

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

    private fun configureCatalogWebView() {

        catalogWebView.settings.javaScriptEnabled = true
        catalogWebView.settings.domStorageEnabled = true
        catalogWebView.settings.loadsImagesAutomatically = true

        catalogWebView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (url?.contains("library/index.php") == true) {
                    readFishCatalog()
                }
            }
        }
    }

    private fun loadFishCatalog() {
        status.text = "POBIERANIE KATALOGU RYB..."
        catalogWebView.loadUrl(
            "https://www.warofdragons.com/info/library/index.php?id=351&obj=cat"
        )
    }

    private fun readFishCatalog() {

        val javascript = """
            (function() {
                var tables = document.querySelectorAll("table");
                var result = [];

                for (var t = 0; t < tables.length; t++) {

                    var table = tables[t];
                    var rows = table.querySelectorAll("tr");

                    var headerText = (
                        table.innerText || ""
                    ).toLowerCase();

                    if (
                        headerText.indexOf("skill level") === -1 &&
                        headerText.indexOf("fisherman") === -1
                    ) {
                        continue;
                    }

                    for (var r = 0; r < rows.length; r++) {

                        var cells = rows[r].querySelectorAll("td");

                        if (cells.length < 2) continue;

                        var images = cells[1].querySelectorAll("img");

                        var name = "";
                        var image = "";

                        for (var i = 0; i < images.length; i++) {
                            var img = images[i];

                            name =
                                img.getAttribute("alt") ||
                                img.getAttribute("title") ||
                                "";

                            image =
                                img.getAttribute("src") || "";

                            if (name || image) break;
                        }

                        if (!name) {
                            name = (
                                cells[1].innerText || ""
                            ).trim();
                        }

                        if (!name && !image) continue;

                        var skill = (
                            cells[0].innerText || ""
                        ).trim();

                        var locations = [];

                        for (var c = 2; c < cells.length; c++) {
                            var txt = (
                                cells[c].innerText || ""
                            ).trim();

                            if (txt) locations.push(txt);
                        }

                        result.push({
                            name: name,
                            skill: skill,
                            locations: locations.join(" / "),
                            image: image
                        });
                    }
                }

                return {
                    count: result.length,
                    items: result
                };
            })();
        """.trimIndent()

        catalogWebView.evaluateJavascript(javascript) { result ->

            try {

                if (result == null || result == "null") {
                    status.text = "Nie udało się odczytać katalogu."
                    return@evaluateJavascript
                }

                val data = JSONObject(result)
                val items = data.optJSONArray("items") ?: JSONArray()

                basicFish.clear()

                for (i in 0 until items.length()) {

                    val item = items.getJSONObject(i)

                    val name = item.optString("name").trim()

                    if (name.isNotEmpty()) {
                        basicFish.add(
                            FishResource(
                                name,
                                item.optString("skill"),
                                item.optString("locations"),
                                item.optString("image")
                            )
                        )
                    }
                }

                updateFishList()

                status.text =
                    if (basicFish.isNotEmpty()) {
                        "KATALOG: ${basicFish.size} POZYCJI"
                    } else {
                        "Biblioteka otwarta, ale nie odczytano nazw ryb."
                    }

            } catch (e: Exception) {
                status.text = "Błąd katalogu: ${e.message}"
            }
        }
    }

    private fun updateFishList() {

        val selectedCategory = categorySpinner.selectedItemPosition

        val names = if (selectedCategory == 0) {

            if (basicFish.isEmpty()) {
                listOf("Katalog jeszcze się ładuje")
            } else {
                basicFish.map {
                    "${it.name} | poziom ${it.skill}"
                }
            }

        } else {

            mystrasFish.map { it.name }
        }

        fishSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )
    }

    private fun getSelectedFishName(): String {

        val category = categorySpinner.selectedItemPosition
        val position = fishSpinner.selectedItemPosition

        if (category == 0) {
            if (position !in basicFish.indices) return ""
            return basicFish[position].name
        }

        if (position !in mystrasFish.indices) return ""

        return mystrasFish[position].name
    }

    private fun startAnalysis() {

        if (running) return

        val selectedFish = getSelectedFishName()

        if (
            selectedFish.isEmpty() ||
            selectedFish == "Katalog jeszcze się ładuje"
        ) {
            Toast.makeText(
                this,
                "Wybierz surowiec z listy.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        running = true
        scanCount = 0

        scanStatus.text =
            "Rozpoczynam analizę: $selectedFish"

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

        val activeWebView = popupWebView ?: webView
        val selectedFish = getSelectedFishName()

        if (selectedFish.isEmpty()) return

        // W Mystras wybór koloru ikony wymaga analizy obrazu mapy.
        // Ten etap nie udaje jeszcze, że rozpoznaje kolory na Canvas/WebGL.
        if (categorySpinner.selectedItemPosition == 1) {
            scanCount++

            scanStatus.text =
                "SKAN: $scanCount\n" +
                "TRYB: MYSTRAS\n" +
                "WYBRANO: $selectedFish\n\n" +
                "Oczekiwanie na moduł rozpoznawania ikon na mapie."

            status.text = "SKAN: $scanCount | POŁOWY: $collected"
            return
        }

        val escapedFish = JSONObject.quote(selectedFish)

        val javascript = """
            (function() {

                var target = $escapedFish;
                var elements = [];

                var found = document.querySelectorAll(
                    'a, button, [role="button"], [onclick], img, svg, canvas, [style*="background"]'
                );

                var targetLower = target.toLowerCase();

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

                    var matches =
                        searchable.indexOf(targetLower) !== -1;

                    if (matches) {

                        function flash(times) {

                            if (times <= 0) {
                                el.style.outline = "";
                                return;
                            }

                            el.style.outline = "4px solid red";

                            setTimeout(function() {
                                el.style.outline = "none";

                                setTimeout(function() {
                                    flash(times - 1);
                                }, 150);

                            }, 200);
                        }

                        flash(2);
                    }

                    elements.push({
                        tag: el.tagName,
                        text: text.substring(0, 80),
                        title: title,
                        alt: alt,
                        src: src,
                        matches: matches
                    });

                    if (elements.length >= 60) break;
                }

                return {
                    url: location.href,
                    target: target,
                    items: elements
                };

            })();
        """.trimIndent()

        activeWebView.evaluateJavascript(javascript) { resu
