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

    private fun stopAnalysis() {

        scanning = false

        handler.removeCallbacks(scanRunnable)

        startButton.isEnabled = true
        stopButton.isEnabled = false

        clearHighlights()

        scanStatus.text =
            "Analiza zatrzymana. Liczba skanów: $scanCount"
    }
        private fun scanPage() {

        val fish = selectedFish ?: return

        val javascript = """
            (function() {

                const images = Array.from(
                    document.querySelectorAll("img")
                );

                let marked = 0;

                images.forEach(img => {

                    if (
                        img.naturalWidth < 10 ||
                        img.naturalHeight < 10 ||
                        img.getBoundingClientRect().width <= 0 ||
                        img.getBoundingClientRect().height <= 0
                    ) {
                        return;
                    }

                    img.style.outline = "4px solid red";
                    img.style.outlineOffset = "2px";
                    img.style.boxShadow = "0 0 8px red";

                    marked++;
                });

                return JSON.stringify({
                    images: images.length,
                    marked: marked
                });

            })();
        """.trimIndent()

        fun scanWindow(
            webView: WebView,
            name: String
        ) {

            webView.evaluateJavascript(javascript) { result ->

                try {

                    if (result == null || result == "null") {
                        return@evaluateJavascript
                    }

                    val decoded =
                        JSONArray("[$result]").getString(0)

                    val data = JSONObject(decoded)

                    scanCount++

                    scanStatus.text =
                        "OKNO: $name\n" +
                        "Wybrany surowiec: ${fish.name}\n" +
                        "Obrazki: ${data.optInt("images")}\n" +
                        "Oznaczone: ${data.optInt("marked")}\n" +
                        "Czas między analizami: ${scanInterval / 1000} s\n" +
                        "Skan: $scanCount"

                } catch (e: Exception) {

                    scanStatus.text =
                        "Błąd: ${e.message}"
                }
            }
        }

        scanWindow(gameWebView, "GŁÓWNE")

        popupWebView?.let {
            scanWindow(it, "DODATKOWE")
        }
    }

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

        gameWebView.evaluateJavascript(javascript, null)

        popupWebView?.evaluateJavascript(
            javascript,
            null
        )
    }

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
