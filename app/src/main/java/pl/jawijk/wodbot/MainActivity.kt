package pl.jawijk.wodbot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

data class FishResource(
    val name: String,
    val skill: String,
    val locations: String,
    val image: String
)

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var gameWebView: WebView

    private lateinit var categorySpinner: Spinner
    private lateinit var resourceSpinner: Spinner
    private lateinit var timeSpinner: Spinner

    private lateinit var scanStatus: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val basicFish = mutableListOf<FishResource>()
    private val mystrasFish = mutableListOf<FishResource>()

    private var selectedFish: FishResource? = null
    private var scanning = false
    private var scanCount = 0

    private var scanInterval = 30000L

    private val gameUrl =
        "https://www.warofdragons.com/"

    private val catalogUrl =
        "https://warofdragons.com/info/library/index.php?id=351&obj=cat"

    private var popupWebView: WebView? = null

    private val scanRunnable = object : Runnable {

        override fun run() {

            if (!scanning) return

            scanPage()

            handler.postDelayed(
                this,
                scanInterval
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        configureWebView()

        gameWebView.loadUrl(gameUrl)

        loadFishCatalog()
        loadMystrasFish()
    }

    private fun createInterface() {

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "JAWIJK WOD – ASYSTENT RYBAKA"
        title.textSize = 18f
        title.setTextColor(Color.BLACK)
        title.setPadding(10, 12, 10, 12)
        title.gravity = android.view.Gravity.CENTER

        root.addView(title)

        categorySpinner = Spinner(this)

        val categories = listOf(
            "Ryby podstawowe – biblioteka",
            "Ryby Mystras"
        )

        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )

        root.addView(categorySpinner)

        resourceSpinner = Spinner(this)
        root.addView(resourceSpinner)

        // WYBÓR CZASU ANALIZY

        val timeTitle = TextView(this)
        timeTitle.text = "CZAS MIĘDZY ANALIZAMI:"
        timeTitle.textSize = 13f
        timeTitle.setTextColor(Color.BLACK)
        timeTitle.setPadding(10, 6, 10, 2)

        root.addView(timeTitle)

        timeSpinner = Spinner(this)

        val timeOptions = listOf(
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
            timeOptions
        )

        timeSpinner.setSelection(4)

        root.addView(timeSpinner)

        timeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    scanInterval = when (position) {
                        0 -> 1000L
                        1 -> 5000L
                        2 -> 10000L
                        3 -> 15000L
                        4 -> 30000L
                        5 -> 60000L
                        6 -> 120000L
                        7 -> 300000L
                        else -> 30000L
                    }

                    scanStatus.text =
                        "Ustawiony czas analizy: " +
                        timeOptions[position]

                    if (scanning) {

                        handler.removeCallbacks(scanRunnable)

                        handler.postDelayed(
                            scanRunnable,
                            scanInterval
                        )
                    }
                }
            }

        // START I STOP

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL

        startButton = Button(this)
        startButton.text = "START"

        stopButton = Button(this)
        stopButton.text = "STOP"
        stopButton.isEnabled = false

        buttons.addView(
            startButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        buttons.addView(
            stopButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(buttons)

        startButton.setOnClickListener {
            startAnalysis()
        }

        stopButton.setOnClickListener {
            stopAnalysis()
        }

        // ZOOM

        val zoomButtons = LinearLayout(this)
        zoomButtons.orientation = LinearLayout.HORIZONTAL

        val zoomOut = Button(this)
        zoomOut.text = "A−"

        val zoomIn = Button(this)
        zoomIn.text = "A+"

        zoomButtons.addView(
            zoomOut,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        zoomButtons.addView(
            zoomIn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(zoomButtons)

        scanStatus = TextView(this)
        scanStatus.text = "Ładowanie biblioteki rybaka..."
        scanStatus.textSize = 12f
        scanStatus.setPadding(10, 8, 10, 8)
        scanStatus.setTextColor(Color.BLACK)
        scanStatus.setTextIsSelectable(true)

        root.addView(scanStatus)

        webContainer = FrameLayout(this)

        root.addView(
            webContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        gameWebView = WebView(this)

        webContainer.addView(
            gameWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        zoomOut.setOnClickListener {
            gameWebView.zoomBy(0.8f)
        }

        zoomIn.setOnClickListener {
            gameWebView.zoomBy(1.25f)
        }

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (position == 0) {
                        showFishList(basicFish)
                    } else {
                        showFishList(mystrasFish)
                    }
                }
            }

        resourceSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val list =
                        if (categorySpinner.selectedItemPosition == 0) {
                            basicFish
                        } else {
                            mystrasFish
                        }

                    selectedFish = list.getOrNull(position)
                }
            }
    }
        private fun configureWebView() {

        val settings = gameWebView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        CookieManager.getInstance().setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(gameWebView, true)

        gameWebView.webViewClient = WebViewClient()

        gameWebView.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {

                val popup = WebView(this@MainActivity)

                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.setSupportMultipleWindows(true)

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

                transport?.webView = popup
                resultMsg?.sendToTarget()

                return true
            }

            override fun onCloseWindow(window: WebView?) {

                window?.let {
                    webContainer.removeView(it)
                    it.destroy()
                }

                popupWebView = null
            }
        }
    }

    // BIBLIOTEKA RYBAKA

    private fun loadFishCatalog() {

        val catalogView = WebView(this)

        catalogView.settings.javaScriptEnabled = true
        catalogView.settings.domStorageEnabled = true

        catalogView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                readFishCatalog(catalogView)
            }
        }

        catalogView.loadUrl(catalogUrl)
    }

    private fun readFishCatalog(view: WebView) {

        val javascript = """
            (function() {

                const result = [];

                const tables = Array.from(
                    document.querySelectorAll("table")
                );

                let fishTable = null;

                for (const table of tables) {

                    const headers = Array.from(
                        table.querySelectorAll(
                            "tr:first-child th, tr:first-child td"
                        )
                    ).map(e =>
                        (e.innerText || "").trim().toLowerCase()
                    );

                    if (
                        headers.some(x => x.includes("skill level")) &&
                        headers.some(x => x === "fish") &&
                        headers.some(x => x.includes("magmar")) &&
                        headers.some(x => x.includes("human"))
                    ) {
                        fishTable = table;
                        break;
                    }
                }

                if (!fishTable) {

                    return JSON.stringify({
                        error: "Nie znaleziono tabeli Fisherman resources",
                        items: []
                    });
                }

                const rows = Array.from(
                    fishTable.querySelectorAll("tr")
                );

                for (const row of rows.slice(1)) {

                    const cells = Array.from(
                        row.querySelectorAll("td")
                    );

                    if (cells.length < 4) continue;

                    const skill =
                        (cells[0].innerText || "").trim();

                    if (
                        !skill ||
                        !Array.from(skill).every(
                            ch => ch >= "0" && ch <= "9"
                        )
                    ) continue;

                    const fishCell = cells[1];
                    const img = fishCell.querySelector("img");
                    const link = fishCell.querySelector("a");

                    let name = "";

                    if (img) {
                        name =
                            img.getAttribute("alt") ||
                            img.getAttribute("title") ||
                            "";
                    }

                    if (!name && link) {
                        name =
                            link.innerText ||
                            link.getAttribute("title") ||
                            "";
                    }

                    if (!name && img) {
                        name = img.src.split("/").pop();
                    }

                    if (!name) {
                        name = "Ryba – poziom " + skill;
                    }

                    result.push({

                        name: name,
                        skill: skill,

                        locations:
                            "Magmar: " +
                            (cells[2].innerText || "").trim() +
                            " | Ludzie: " +
                            (cells[3].innerText || "").trim(),

                        image: img ? img.src : ""
                    });
                }

                return JSON.stringify({
                    error: "",
                    items: result
                });

            })();
        """.trimIndent()

        view.evaluateJavascript(javascript) { result ->

            try {

                if (result == null || result == "null") {

                    scanStatus.text =
                        "Biblioteka nie zwróciła danych."

                    return@evaluateJavascript
                }

                val decoded =
                    JSONArray("[$result]").getString(0)

                val json = JSONObject(decoded)

                val items = json.optJSONArray("items")

                basicFish.clear()

                if (items != null) {

                    for (i in 0 until items.length()) {

                        val item = items.getJSONObject(i)

                        basicFish.add(
                            FishResource(
                                item.optString("name"),
                                item.optString("skill"),
                                item.optString("locations"),
                                item.optString("image")
                            )
                        )
                    }
                }

                showFishList(basicFish)

                scanStatus.text =
                    if (basicFish.isEmpty()) {

                        "Nie pobrano ryb: ${json.optString("error")}"

                    } else {

                        "Pobrano ryby: ${basicFish.size}"
                    }

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd biblioteki: ${e.message}"
            }
        }
    }

    // RYBY MYSTRAS

    private fun loadMystrasFish() {

        mystrasFish.clear()

        mystrasFish.add(
            FishResource(
                "Golden Shams",
                "Mystras",
                "Mystras",
                ""
            )
        )

        mystrasFish.add(
            FishResource(
                "Mystras Serpentfish",
                "Mystras",
                "Mystras",
                ""
            )
        )

        mystrasFish.add(
            FishResource(
                "Akduff the Multiped",
                "Mystras",
                "Mystras",
                ""
            )
        )
    }

    // LISTA SUROWCÓW

    private fun showFishList(list: List<FishResource>) {

        val names = if (list.isEmpty()) {

            listOf("Brak dostępnych surowców")

        } else {

            list.map {
                "${it.name} – poziom ${it.skill}"
            }
        }

        resourceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        selectedFish = list.firstOrNull()
    }

    // START

    private fun startAnalysis() {

        if (selectedFish == null) {

            scanStatus.text =
                "Najpierw wybierz surowiec."

            return
        }

        scanning = true
        scanCount = 0

        startButton.isEnabled = false
        stopButton.isEnabled = true

        handler.removeCallbacks(scanRunnable)

        scanStatus.text =
            "ANALIZA URUCHOMIONA: ${selectedFish!!.name}"

        scanPage()

        handler.postDelayed(
            scanRunnable,
            scanInterval
        )
    }

    // STOP

    private fun stopAnalysis() {

        scanning = false

        handler.removeCallbacks(scanRunnable)

        startButton.isEnabled = true
        stopButton.isEnabled = false

        clearHighlights()

        scanStatus.text =
            "Analiza zatrzymana. Liczba skanów: $scanCount"
    }
        // LOSOWE OZNACZANIE JEDNEGO OBRAZKA

    private fun scanPage() {

        val fish = selectedFish ?: return

        val javascript = """
            (function() {

                const images = Array.from(
                    document.querySelectorAll("img")
                ).filter(img => {

                    const rect = img.getBoundingClientRect();

                    return (
                        img.complete &&
                        img.naturalWidth >= 10 &&
                        img.naturalHeight >= 10 &&
                        rect.width > 0 &&
                        rect.height > 0
                    );
                });

                if (images.length === 0) {

                    return JSON.stringify({
                        found: 0,
                        selected: -1
                    });
                }

                // LOSOWANIE JEDNEGO OBRAZKA

                const index = Math.floor(
                    Math.random() * images.length
                );

                const selected = images[index];

                // PRZEWIJANIE DO OBRAZKA

                selected.scrollIntoView({
                    behavior: "smooth",
                    block: "center",
                    inline: "center"
                });

                // PIERWSZE OZNACZENIE

                selected.style.outline = "5px solid red";
                selected.style.outlineOffset = "4px";
                selected.style.boxShadow = "0 0 15px red";

                // USUNIĘCIE PIERWSZEGO OZNACZENIA

                setTimeout(function() {

                    selected.style.outline = "none";
                    selected.style.boxShadow = "none";

                }, 200);

                // DRUGIE OZNACZENIE

                setTimeout(function() {

                    selected.style.outline = "5px solid red";
                    selected.style.outlineOffset = "4px";
                    selected.style.boxShadow = "0 0 15px red";

                }, 400);

                // ZAKOŃCZENIE OZNACZENIA

                setTimeout(function() {

                    selected.style.outline = "none";
                    selected.style.boxShadow = "none";

                }, 800);

                return JSON.stringify({

                    found: images.length,
                    selected: index + 1

                });

            })();
        """.trimIndent()

        gameWebView.evaluateJavascript(javascript) { result ->

            try {

                if (result == null || result == "null") {

                    scanStatus.text =
                        "Brak wyniku analizy"

                    return@evaluateJavascript
                }

                val decoded =
                    JSONArray("[$result]").getString(0)

                val data = JSONObject(decoded)

                scanCount++

                if (data.optInt("found") == 0) {

                    scanStatus.text =
                        "Nie znaleziono obrazków do oznaczenia."

                } else {

                    scanStatus.text =
                        "WYBRANY SUROWIEC: ${fish.name}\n" +
                        "Znalezione obrazki: ${data.optInt("found")}\n" +
                        "Wylosowany obrazek: ${data.optInt("selected")}\n" +
                        "Oznaczenie: DWUKROTNE\n" +
                        "Czas analizy: ${scanInterval / 1000} sekund\n" +
                        "Liczba analiz: $scanCount"
                }

            } catch (e: Exception) {

                scanStatus.text =
                    "Błąd oznaczania: ${e.message}"
            }
        }
    }

    // USUWANIE OZNACZEŃ

    private fun clearHighlights() {

        val javascript = """
            (function() {

                document.querySelectorAll("img").forEach(el => {

                    el.style.outline = "";
                    el.style.outlineOffset = "";
                    el.style.boxShadow = "";

                });

            })();
        """.trimIndent()

        gameWebView.evaluateJavascript(
            javascript,
            null
        )

        popupWebView?.evaluateJavascript(
            javascript,
            null
        )
    }

    // POWRÓT

    override fun onBackPressed() {

        val popup = popupWebView

        if (popup != null && popup.canGoBack()) {

            popup.goBack()
            return
        }

        if (gameWebView.canGoBack()) {

            gameWebView.goBack()
            return
        }

        super.onBackPressed()
    }

    // ZAMYKANIE

    override fun onDestroy() {

        scanning = false

        handler.removeCallbacksAndMessages(null)

        popupWebView?.destroy()
        popupWebView = null

        gameWebView.stopLoading()
        gameWebView.destroy()

        super.onDestroy()
    }
}
