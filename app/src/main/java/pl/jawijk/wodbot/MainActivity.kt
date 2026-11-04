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

    private val basicFish = mutableListOf<FishResource>()
    private val mystrasFish = mutableListOf<FishResource>()

    private var analysisRunning = false
    private var scanInProgress = false
    private var scanInterval = 30000L

    private var selectedCategory = "Ryby podstawowe"
    private var selectedFish = ""

    private var basicLoaded = false
    private var mystrasLoaded = false

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!analysisRunning || scanInProgress) return
            scanPage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        createInterface()
        configureWebView()
        loadBasicCatalog()

        gameWebView.loadUrl("https://www.warofdragons.com/")
    }

    private fun createInterface() {

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.rgb(25, 25, 25))

        val title = TextView(this)
        title.text = "JAWIJK WOD BOT"
        title.textSize = 21f
        title.setTextColor(Color.WHITE)
        title.gravity = android.view.Gravity.CENTER
        title.setPadding(10, 12, 10, 12)
        layout.addView(title)

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

        layout.addView(categorySpinner)

        fishSpinner = Spinner(this)
        layout.addView(fishSpinner)

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
                    val intervals = longArrayOf(
                        1000L, 5000L, 10000L, 15000L,
                        30000L, 60000L, 120000L, 300000L
                    )

                    scanInterval = intervals[position]

                    if (analysisRunning && !scanInProgress) {
                        handler.removeCallbacks(scanRunnable)
                        handler.post(scanRunnable)
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        layout.addView(timeSpinner)

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

        layout.addView(buttons)

        scanStatus = TextView(this)
        scanStatus.text = "Pobieranie biblioteki..."
        scanStatus.textSize = 13f
        scanStatus.setTextColor(Color.GREEN)
        scanStatus.setPadding(10, 8, 10, 8)
        scanStatus.setTextIsSelectable(true)

        layout.addView(scanStatus)

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

        layout.addView(zoomButtons)

        gameFrame = FrameLayout(this)
        gameWebView = WebView(this)

        gameFrame.addView(
            gameWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        layout.addView(
            gameFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        catalogView = WebView(this)
        catalogView.visibility = View.GONE

        layout.addView(
            catalogView,
            LinearLayout.LayoutParams(1, 1)
        )

        setContentView(layout)
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

        gameWebView.webViewClient = WebViewClient()
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

                closePopup()

                val popup = WebView(this@MainActivity)

                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.databaseEnabled = true
                popup.settings.javaScriptCanOpenWindowsAutomatically = true
                popup.settings.setSupportMultipleWindows(true)

                popup.settings.useWideViewPort = true
                popup.settings.loadWithOverviewMode = true
                popup.settings.builtInZoomControls = true
                popup.settings.displayZoomControls = false

                popup.webViewClient = WebViewClient()
                popup.webChromeClient = createGameChromeClient()

                val container = FrameLayout(this@MainActivity)

                container.addView(
                    popup,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                val closeButton = Button(this@MainActivity)
                closeButton.text = "ZAMKNIJ"

                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                params.gravity =
                    android.view.Gravity.TOP or
                    android.view.Gravity.END

                container.addView(closeButton, params)

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
        } catch (_: Exception) {}

        try {
            if (container != null) {
                gameFrame.removeView(container)
            }
        } catch (_: Exception) {}
    }

    private fun loadBasicCatalog() {

        scanStatus.text = "Pobieranie ryb podstawowych..."

        catalogView.settings.javaScriptEnabled = true
        catalogView.settings.domStorageEnabled = true

        catalogView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                if (url?.contains("id=351") == true) {
                    extractBasicFish()
                }
            }
        }

        catalogView.loadUrl(
            "https://warofdragons.com/info/library/index.php?id=351&obj=cat"
        )
    }

    private fun extractBasicFish() {

        val js = """
            (function() {

                let output = [];
                let seen = new Set();

                function clean(value) {
                    return (value || "")
                        .replace(/\s+/g, " ")
                        .trim();
                }

                function add(value, detail) {

                    let name = clean(value);

                    if (!name) return;
                    if (name.length < 3 || name.length > 70) return;

                    let low = name.toLowerCase();

                    if (seen.has(low)) return;

                    let forbidden = [
                        "image",
                        "fisherman",
                        "skill level",
                        "magmar",
                        "human locations",
                        "locations",
                        "fish"
                    ];

                    if (forbidden.includes(low)) return;

                    seen.add(low);

                    output.push({
                        name: name,
                        details: detail || ""
                    });
                }

                let tables = document.querySelectorAll("table");

                tables.forEach(function(table) {

                    let tableText =
                        (table.innerText || "").toLowerCase();

                    if (
                        !tableText.includes("skill level") ||
                        !tableText.includes("magmar") ||
                        !tableText.includes("human locations")
                    ) {
                        return;
                    }

                    let rows = table.querySelectorAll("tr");

                    rows.forEach(function(row) {

                        let cells = row.querySelectorAll("td, th");

                        if (cells.length < 2) return;

                        let firstCell = clean(cells[0].innerText);

                        if (!/^[0-9]+$/.test(firstCell)) return;

                        let fishCell = cells[1];

                        let candidates = [];

                        fishCell.querySelectorAll("a").forEach(function(a) {

                            let text = clean(a.innerText || a.textContent);
                            let title = clean(a.getAttribute("title"));

                            if (text) candidates.push(text);
                            if (title) candidates.push(title);

                            a.querySelectorAll("img").forEach(function(img) {

                                let alt = clean(img.getAttribute("alt"));
                                let imgTitle = clean(img.getAttribute("title"));

                                if (alt) candidates.push(alt);
                                if (imgTitle) candidates.push(imgTitle);
                            });
                        });

                        fishCell.querySelectorAll("img").forEach(function(img) {

                            let alt = clean(img.getAttribute("alt"));
                            let title = clean(img.getAttribute("title"));

                            if (alt) candidates.push(alt);
                            if (title) candidates.push(title);
                        });

                        candidates.forEach(function(name) {
                            add(name, "Poziom umiejętności: " + firstCell);
                        });
                    });
                });

                return JSON.stringify(output);

            })();
        """

        catalogView.evaluateJavascript(js) { result ->

            try {

                val decoded =
                    JSONArray("[$result]").getString(0)

                val array = JSONArray(decoded)

                basicFish.clear()

                for (i in 0 until array.length()) {

                    val item = array.getJSONObject(i)

                    basicFish.add(
                        FishResource(
                            item.optString("name"),
                            item.optString("details")
                        )
                    )
                }

                basicLoaded = true

                loadMystrasCatalog()

            } catch (e: Exception) {

                basicFish.clear()
                basicLoaded = true

                scanStatus.text =
                    "Błąd ryb podstawowych: ${e.message}"

                loadMystrasCatalog()
            }
        }
    }

    private fun loadMystrasCatalog() {

        scanStatus.text = "Pobieranie danych Mystras..."

        catalogView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                if (url?.contains("id=470") == true) {
                    extractMystrasFish()
                }
            }
        }

        catalogView.loadUrl(
            "https://warofdragons.com/info/library/index.php?id=470&obj=cat"
        )
    }

    private fun extractMystrasFish() {

        val js = """
            (function() {

                let output = [];
                let seen = new Set();

                function clean(value) {
                    return (value || "")
                        .replace(/\s+/g, " ")
                        .trim();
                }

                function add(value) {

                    let name = clean(value);

                    if (!name) return;
                    if (name.length < 3 || name.length > 70) return;

                    let low = name.toLowerCase();

                    if (seen.has(low)) return;

                    let forbidden = [
                        "image",
                        "fisherman",
                        "herbologist",
                        "geologist",
                        "profession",
                        "resource",
                        "effect",
                        "price",
                        "skill"
                    ];

                    if (forbidden.includes(low)) return;

                    seen.add(low);

                    output.push({
                        name: name,
                        details: "Mystras - Fisherman"
                    });
                }

                let tables = document.querySelectorAll("table");

                tables.forEach(function(table) {

                    let text =
                        (table.innerText || "").toLowerCase();

                    if (
                        !text.includes("profession") ||
                        !text.includes("fisherman")
                    ) {
                        return;
                    }

                    table.querySelectorAll("tr").forEach(function(row) {

                        let rowText =
                            clean(row.innerText).toLowerCase();

                        if (!rowText.includes("fisherman")) return;

                        let cells = row.querySelectorAll("td");

                        if (cells.length < 2) return;

                        let resourceCell = cells.length >= 3
                            ? cells[2]
                            : cells[cells.length - 1];

                        resourceCell.querySelectorAll("a").forEach(function(a) {

                            let title = clean(a.getAttribute("title"));
                            let text = clean(a.innerText || a.textContent);

                            if (title) add(title);
                            if (text) add(text);

                            a.querySelectorAll("img").forEach(function(img) {

                                let alt = clean(img.getAttribute("alt"));
                                let imgTitle = clean(img.getAttribute("title"));

                                if (alt) add(alt);
                                if (imgTitle) add(imgTitle);
                            });
                        });

                        resourceCell.querySelectorAll("img").forEach(function(img) {

                            let alt = clean(img.getAttribute("alt"));
                            let title = clean(img.getAttribute("title"));

                            if (alt) add(alt);
                            if (title) add(title);
                        });
                    });
                });

                return JSON.stringify(output);

            })();
        """

        catalogView.evaluateJavascript(js) { result ->

            try {

                val decoded =
                    JSONArray("[$result]").getString(0)

                val array = JSONArray(decoded)

                mystrasFish.clear()

                for (i in 0 until array.length()) {

                    val item = array.getJSONObject(i)

                    mystrasFish.add(
                        FishResource(
                            item.optString("name"),
                            item.optString("details")
                        )
                    )
                }

                if (mystrasFish.none {
                        it.name.equals(
                            "Mystras Serpentfish",
                            ignoreCase = true
                        )
                    }
                ) {
                    mystrasFish.add(
                        FishResource(
                            "Mystras Serpentfish",
                            "Mystras - Fisherman"
                        )
                    )
                }

                mystrasLoaded = true

                showFishList()

                scanStatus.text =
                    "Biblioteka pobrana. Podstawowe: ${basicFish.size}, Mystras: ${mystrasFish.size}"

            } catch (e: Exception) {

                mystrasFish.clear()

                mystrasFish.add(
                    FishResource(
                        "Mystras Serpentfish",
                        "Mystras - Fisherman"
                    )
                )

                mystrasLoaded = true
                showFishList()

                scanStatus.text =
                    "Mystras: pobrano tylko potwierdzoną nazwę. ${e.message}"
            }
        }
    }
        private fun showFishList() {

        if (!::fishSpinner.isInitialized) return

        val source = if (
            selectedCategory == "Ryby Mystras"
        ) {
            mystrasFish
        } else {
            basicFish
        }

        val names = source.map { it.name }.distinct()

        val displayNames = if (names.isEmpty()) {
            listOf("Brak rozpoznanych pozycji")
        } else {
            names
        }

        fishSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            displayNames
        )

        fishSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    selectedFish =
                        if (names.isNotEmpty()) names[position] else ""

                    scanStatus.text =
                        if (selectedFish.isNotEmpty()) {
                            "Wybrany surowiec: $selectedFish"
                        } else {
                            "Brak surowców w tej kategorii."
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
        scanInProgress = false

        scanStatus.text = "Analiza uruchomiona."

        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
    }

    private fun stopAnalysis() {

        analysisRunning = false
        scanInProgress = false

        handler.removeCallbacks(scanRunnable)

        clearHighlights()

        scanStatus.text = "Analiza zatrzymana."
    }

    private fun scanPage() {

        if (!analysisRunning || scanInProgress) return

        scanInProgress = true

        val js = """
            (function() {

                let images = [];
                let canvases = 0;
                let frames = 0;

                function search(doc) {

                    try {

                        doc.querySelectorAll("img").forEach(function(img) {

                            if (
                                img.complete &&
                                img.naturalWidth > 0 &&
                                img.naturalHeight > 0
                            ) {

                                let r = img.getBoundingClientRect();

                                if (r.width > 5 && r.height > 5) {
                                    images.push(img);
                                }
                            }
                        });

                        canvases +=
                            doc.querySelectorAll("canvas").length;

                        doc.querySelectorAll("iframe").forEach(function(frame) {

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

                images.forEach(function(img) {
                    img.style.removeProperty("outline");
                    img.style.removeProperty("box-shadow");
                });

                let index = Math.floor(
                    Math.random() * images.length
                );

                let img = images[index];

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
                    flash();
                }, 300);

                setTimeout(function() {
                    clear();
                }, 900);

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
                    scanStatus.text = "Brak odpowiedzi skanera."
                } else {

                    val decoded =
                        JSONArray("[$result]").getString(0)

                    val data = JSONObject(decoded)

                    val count = data.optInt("count")
                    val canvases = data.optInt("canvases")
                    val frames = data.optInt("frames")
                    val marked = data.optBoolean("marked")

                    scanStatus.text = if (marked) {

                        "OZNACZONO JEDEN OBRAZEK DWUKROTNIE\n" +
                        "Obrazki na stronie: $count\n" +
                        "Oznaczony numer: ${data.optInt("selected")}\n" +
                        "Canvas: $canvases\n" +
                        "Ramki: $frames\n" +
                        "Wybrany surowiec: $selectedFish"

                    } else {

                        "Nie znaleziono obrazków.\n" +
                        "Canvas: $canvases\n" +
                        "Ramki: $frames\n" +
                        "Wybrany surowiec: $selectedFish"
                    }
                }

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd skanowania: ${e.message}"

            } finally {

                scanInProgress = false

                if (analysisRunning) {
                    handler.removeCallbacks(scanRunnable)

                    handler.postDelayed(
                        scanRunnable,
                        scanInterval
                    )
                }
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
        scanInProgress = false

        handler.removeCallbacks(scanRunnable)

        closePopup()

        try {
            gameWebView.destroy()
            catalogView.destroy()
        } catch (_: Exception) {}

        super.onDestroy()
    }
}
