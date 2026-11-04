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
    private lateinit var scanStatus: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val basicFish = mutableListOf<FishResource>()
    private val mystrasFish = mutableListOf<FishResource>()

    private var selectedFish: FishResource? = null
    private var scanning = false
    private var scanCount = 0

    private val gameUrl = "https://www.warofdragons.com/"
    private val catalogUrl =
        "https://warofdragons.com/info/library/index.php?id=351&obj=cat"

    private var popupWebView: WebView? = null

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!scanning) return

            scanPage()
            handler.postDelayed(this, 30000)
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

        val categoryTitle = TextView(this)
        categoryTitle.text = "WYBIERZ KATEGORIĘ RYB"
        categoryTitle.setPadding(10, 5, 10, 5)
        root.addView(categoryTitle)

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

        val resourceTitle = TextView(this)
        resourceTitle.text = "WYBIERZ SUROWIEC"
        resourceTitle.setPadding(10, 5, 10, 5)
        root.addView(resourceTitle)

        resourceSpinner = Spinner(this)
        root.addView(resourceSpinner)

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(parent: AdapterView<*>?) {}

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

                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val list = if (categorySpinner.selectedItemPosition == 0) {
                        basicFish
                    } else {
                        mystrasFish
                    }

                    selectedFish = list.getOrNull(position)

                    selectedFish?.let {
                        scanStatus.text =
                            "Wybrano: ${it.name}\nPoziom umiejętności: ${it.skill}\nLokacje: ${it.locations}"
                    }
                }
            }

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

        zoomOut.setOnClickListener {
            gameWebView.zoomBy(0.8f)
        }

        zoomIn.setOnClickListener {
            gameWebView.zoomBy(1.25f)
        }

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

    private fun loadFishCatalog() {

        val catalogView = WebView(this)

        catalogView.settings.javaScriptEnabled = true
        catalogView.settings.domStorageEnabled = true

        catalogView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                readFishCatalog(catalogView)
            }
        }

        catalogView.loadUrl(catalogUrl)
    }

    private fun readFishCatalog(view: WebView) {

        val javascript = """
            (function() {
                const result = [];

                const tables = Array.from(document.querySelectorAll("table"));

                let fishTable = null;

                for (const table of tables) {
                    const headers = Array.from(
                        table.querySelectorAll("tr:first-child th, tr:first-child td")
                    ).map(e => (e.innerText || "").trim().toLowerCase());

                    const hasSkill = headers.some(x => x.includes("skill level"));
                    const hasFish = headers.some(x => x === "fish");
                    const hasMagmar = headers.some(x => x.includes("magmar"));
                    const hasHuman = headers.some(x => x.includes("human"));

                    if (hasSkill && hasFish && hasMagmar && hasHuman) {
                        fishTable = table;
                        break;
                    }
                }

                if (!fishTable) {
                    return JSON.stringify({
                        error: "Nie znaleziono właściwej tabeli Fisherman resources",
                        items: []
                    });
                }

                const rows = Array.from(fishTable.querySelectorAll("tr"));

                for (const row of rows.slice(1)) {
                    const cells = Array.from(row.querySelectorAll("td"));

                    if (cells.length < 4) continue;

                    const skill = (cells[0].innerText || "").trim();

                    if (!skill || !Array.from(skill).every(
                        ch => ch >= "0" && ch <= "9"
                    )) {
                        continue;
                    }

                    const fishCell = cells[1];

                    const img = fishCell.querySelector("img");

                    const link = fishCell.querySelector("a");

                    let name = "";

                    if (img) {
                        name = img.getAttribute("alt") ||
                               img.getAttribute("title") || "";
                    }

                    if (!name && link) {
                        name = link.innerText || link.getAttribute("title") || "";
                    }

                    if (!name && img) {
                        const src = img.getAttribute("src") || "";
                        name = src.split("/").pop().split("?")[0];
                    }

                    if (!name) {
                        name = "Ryba – poziom " + skill;
                    }

                    const image = img
                        ? img.src
                        : "";

                    const magmar = (cells[2].innerText || "").trim();
                    const human = (cells[3].innerText || "").trim();

                    result.push({
                        name: name,
                        skill: skill,
                        locations: "Magmar: " + magmar +
                                   " | Ludzie: " + human,
                        image: image
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
                    scanStatus.text = "Biblioteka nie zwróciła danych."
                    return@evaluateJavascript
                }

                val decodedString = JSONArray("[$result]").getString(0)
                val json = JSONObject(decodedString)

                val error = json.optString("error")
                val items = json.optJSONArray("items")

                basicFish.clear()

                if (items != null) {
                    for (i in 0 until items.length()) {

                        val item = items.getJSONObject(i)

                        basicFish.add(
                            FishResource(
                                name = item.optString("name"),
                                skill = item.optString("skill"),
                                locations = item.optString("locations"),
                                image = item.optString("image")
                            )
                        )
                    }
                }

                if (basicFish.isEmpty()) {
                    scanStatus.text =
                        "Nie pobrano ryb z biblioteki.\n$error"
                } else {
                    showFishList(basicFish)

                    scanStatus.text =
                        "Pobrano ryby z biblioteki: ${basicFish.size}"
                }

            } catch (e: Exception) {
                scanStatus.text =
                    "Błąd odczytu biblioteki: ${e.message}"
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

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        resourceSpinner.adapter = adapter

        if (list.isNotEmpty()) {
            selectedFish = list[0]
        } else {
            selectedFish = null
        }
    }

    private fun startAnalysis() {

        if (selectedFish == null) {
            scanStatus.text = "Najpierw wybierz surowiec."
            return
        }

        scanning = true
        scanCount = 0

        startButton.isEnabled = false
        stopButton.isEnabled = true

        scanStatus.text =
            "ANALIZA URUCHOMIONA\nWybrano: ${selectedFish!!.name}"

        scanPage()

        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 30000)
    }

    private fun stopAnalysis() {

        scanning = false

        handler.removeCallbacks(scanRunnable)

        startButton.isEnabled = true
        stopButton.isEnabled = false

        clearHighlights()

        scanStatus.text =
            "Analiza zatrzymana.\nWykonane skany: $scanCount"
    }

    private fun scanPage() {

        val fish = selectedFish ?: return

        val targetName = JSONObject.quote(fish.name)
        val targetImage = JSONObject.quote(fish.image)

        val javascript = """
            (function() {

                const targetName = $targetName.toLowerCase();
                const targetImage = $targetImage.toLowerCase();

                const elements = Array.from(
                    document.querySelectorAll(
                        "a, button, img, [role='button'], [title], [alt]"
                    )
                );

                let found = [];

                for (const el of elements) {

                    const text = (
                        (el.innerText || "") + " " +
                        (el.getAttribute("title") || "") + " " +
                        (el.getAttribute("alt") || "") + " " +
                        (el.getAttribute("src") || "") + " " +
                        (el.getAttribute("href") || "")
                    ).toLowerCase();

                    let match = false;

                    if (targetName && text.includes(targetName)) {
                        match = true;
                    }

                    if (targetImage && targetImage.length > 0 &&
                        text.includes(targetImage)) {
                        match = true;
                    }

                    if (match) {
                        found.push(el);
                    }
                }

                if (window.jawijkFlashTimer) {
                    clearInterval(window.jawijkFlashTimer);
                }

                found.forEach(el => {
                    el.dataset.jawijkOldOutline =
                        el.style.outline || "";

                    el.style.outline = "4px solid red";
                    el.style.outlineOffset = "2px";
                });

                let visible = true;

                window.jawijkFlashTimer = setInterval(function() {
                    visible = !visible;

                    found.forEach(el => {
                        el.style.outline = visible
                            ? "4px solid red"
                            : "none";
                    });
                }, 250);

                return found.length;
            })();
        """.trimIndent()

        gameWebView.evaluateJavascript(javascript) { result ->

            scanCount++

            val found = result?.toIntOrNull() ?: 0

            scanStatus.text =
                "ANALIZA: ${fish.name}\n" +
                "Znalezione elementy: $found\n" +
                "Liczba skanów: $scanCount\n" +
                "Kolejny skan za 30 sekund."
        }
    }

    private fun clearHighlights() {

        val javascript = """
            (function() {

                if (window.jawijkFlashTimer) {
                    clearInterval(window.jawijkFlashTimer);
                }

                document.querySelectorAll("*").forEach(el => {
                    if (el.style.outline === "none" ||
                        el.style.outline.includes("red")) {
                        el.style.outline = "";
                        el.style.outlineOffset = "";
                    }
                });

            })();
        """.trimIndent()

        gameWebView.evaluateJavascript(javascript, null)
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
