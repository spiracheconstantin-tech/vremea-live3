package ro.vremea.wallpaper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var titleTv: TextView
    private lateinit var body: LinearLayout
    private val prefs by lazy { getSharedPreferences("app", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { load(); handler.postDelayed(this, 600000L) }
    }
    private val dayP = arrayOf(
        "#1F8FE0", "#BFE6FB", "#3E9BDB", "#C9E4F5", "#5C7A95", "#CFDBE5", "#8A979F",
        "#D6DCDF", "#2F4256", "#7F97AB", "#7C93AD", "#E8EFF6", "#1E2433", "#5A6683"
    )
    private val nightP = arrayOf(
        "#0A1230", "#3A5088", "#101A3A", "#3F5590", "#1E2535", "#48546A", "#2A3038",
        "#56606A", "#141C28", "#3A4A5C", "#2A3550", "#6A7C98", "#0D111B", "#2E374D"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float, bold: Boolean = false, alpha: Float = 1f) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.WHITE)
        this.alpha = alpha
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun glass() = GradientDrawable().apply {
        setColor(0x33FFFFFF)
        cornerRadius = dp(18).toFloat()
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glass()
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun add(v: View, top: Int = 0) {
        body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun setBg(k: Int, day: Boolean) {
        val p = if (day) dayP else nightP
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(p[k * 2]), Color.parseColor(p[k * 2 + 1]))
        )
    }

    private fun info(c: Int, day: Boolean): Triple<Int, String, String> = when {
        c == 0 -> Triple(0, "Senin", if (day) "☀️" else "🌙")
        c <= 2 -> Triple(1, "Parțial noros", if (day) "⛅" else "☁️")
        c == 3 -> Triple(2, "Noros", "☁️")
        c <= 48 -> Triple(3, "Ceață", "🌫️")
        c <= 57 -> Triple(4, "Burniță", "🌦️")
        c <= 67 -> Triple(4, "Ploaie", "🌧️")
        c <= 77 -> Triple(5, "Ninsoare", "❄️")
        c <= 82 -> Triple(4, "Averse", "🌧️")
        c <= 86 -> Triple(5, "Averse de zăpadă", "🌨️")
        else -> Triple(6, "Furtună", "⛈️")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(36), dp(8), dp(8))
        }
        titleTv = tv("", 22f, true)
        top.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
        val menuBtn = tv("☰", 28f)
        menuBtn.setPadding(dp(16), dp(4), dp(16), dp(4))
        menuBtn.setOnClickListener { showMenu(it) }
        top.addView(menuBtn)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(32))
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(top)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
        setBg(0, true)
        if (!prefs.contains("lat")) myLocation()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 600000L)
        load()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) myLocation()
    }

    // ---------- meniu ----------
    private fun showMenu(v: View) {
        val f = prefs.getString("units", "c") == "f"
        val m = PopupMenu(this, v)
        m.menu.add(0, 1, 0, "🔍 Caută oraș")
        m.menu.add(0, 2, 1, "⭐ Orașele mele")
        m.menu.add(0, 3, 2, "📍 Locația mea")
        m.menu.add(0, 4, 3, "🗑️ Șterge orașul curent din listă")
        m.menu.add(0, 5, 4, "🌡️ Unități acum: " + (if (f) "°F, mph" else "°C, km/h") + " (schimbă)")
        m.menu.add(0, 6, 5, "🔄 Reîmprospătează")
        m.menu.add(0, 7, 6, "🖼️ Setează fundal animat")
        m.menu.add(0, 8, 7, "📡 Sursă date: " + srcName() + " (schimbă)")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> searchDialog()
                2 -> showFavs()
                3 -> myLocation()
                4 -> removeCurrent()
                5 -> { prefs.edit().putString("units", if (f) "c" else "f").apply(); load() }
                6 -> load()
                7 -> openChooser()
                8 -> pickSource()
            }
            true
        }
        m.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun openChooser() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, WeatherWallpaperService::class.java)
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    // ---------- surse de date ----------
    private fun srcName(): String = when (prefs.getString("src", "om")) {
        "met" -> "MET Norway"
        "avg" -> "Medie"
        else -> "Open-Meteo"
    }

    private fun pickSource() {
        val opts = arrayOf("Open-Meteo (implicit)", "MET Norway (yr.no)", "Medie Open-Meteo + MET Norway")
        val keys = arrayOf("om", "met", "avg")
        AlertDialog.Builder(this).setTitle("Sursa datelor meteo").setItems(opts) { _, i ->
            prefs.edit().putString("src", keys[i]).apply()
            load()
        }.show()
    }

    private fun symToCode(s: String): Int {
        val x = s.lowercase()
        return when {
            x.contains("thunder") -> 95
            x.contains("sleet") -> 67
            x.contains("snow") -> when { x.contains("heavy") -> 75; x.contains("light") -> 71; else -> 73 }
            x.contains("rain") -> when { x.contains("heavy") -> 65; x.contains("light") -> 61; else -> 63 }
            x.contains("fog") -> 45
            x.startsWith("clearsky") -> 0
            x.startsWith("fair") -> 1
            x.startsWith("partlycloudy") -> 2
            x.startsWith("cloudy") -> 3
            else -> -1
        }
    }

    private fun fetchMet(lat: String, lon: String): JSONObject? {
        return try {
            val u = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" +
                "%.4f".format(Locale.US, lat.toDouble()) + "&lon=" + "%.4f".format(Locale.US, lon.toDouble())
            val conn = URL(u).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "VremeaLive/1.0 github.com/spiracheconstantin-tech/vremea-live3")
            }
            JSONObject(conn.getInputStream().bufferedReader().readText())
                .getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
        } catch (e: Exception) {
            null
        }
    }

    private fun mergeMet(j: JSONObject, met: JSONObject, avg: Boolean, f: Boolean) {
        val cur = j.getJSONObject("current")
        val d = met.getJSONObject("instant").getJSONObject("details")
        fun mix(key: String, v: Double) {
            val old = cur.optDouble(key, v)
            cur.put(key, if (avg) (old + v) / 2.0 else v)
        }
        val ms = if (f) 2.23694 else 3.6
        val oldT = cur.getDouble("temperature_2m")
        val oldW = cur.optDouble("wind_speed_10m", 0.0)
        j.put("cmp_om_t", oldT)
        j.put("cmp_om_w", oldW)
        val tC = d.optDouble("air_temperature", Double.NaN)
        if (!tC.isNaN()) {
            val tv2 = if (f) tC * 9.0 / 5.0 + 32.0 else tC
            j.put("cmp_met_t", tv2)
            mix("temperature_2m", tv2)
            cur.put("apparent_temperature", cur.getDouble("apparent_temperature") + (cur.getDouble("temperature_2m") - oldT))
        }
        val ws = d.optDouble("wind_speed", Double.NaN)
        if (!ws.isNaN()) {
            j.put("cmp_met_w", ws * ms)
            mix("wind_speed_10m", ws * ms)
        }
        val wg = d.optDouble("wind_speed_of_gust", Double.NaN)
        if (!wg.isNaN()) mix("wind_gusts_10m", wg * ms)
        val wd = d.optDouble("wind_from_direction", Double.NaN)
        if (!wd.isNaN() && !avg) cur.put("wind_direction_10m", wd)
        val rh = d.optDouble("relative_humidity", Double.NaN)
        if (!rh.isNaN()) mix("relative_humidity_2m", rh)
        val pr = d.optDouble("air_pressure_at_sea_level", Double.NaN)
        if (!pr.isNaN()) mix("pressure_msl", pr)
        val cl = d.optDouble("cloud_area_fraction", Double.NaN)
        if (!cl.isNaN()) mix("cloud_cover", cl)
        if (!avg) {
            val n1 = met.optJSONObject("next_1_hours")
            val sym = n1?.optJSONObject("summary")?.optString("symbol_code", "") ?: ""
            val code = symToCode(sym)
            if (code >= 0) cur.put("weather_code", code)
            val pa = n1?.optJSONObject("details")?.optDouble("precipitation_amount", Double.NaN) ?: Double.NaN
            if (!pa.isNaN()) cur.put("precipitation", pa)
        }
    }

    // ---------- orașe ----------
    private fun favs(): JSONArray = try { JSONArray(prefs.getString("favs", "[]")) } catch (e: Exception) { JSONArray() }

    private fun setCurrent(name: String, lat: Double, lon: Double) {
        prefs.edit().putString("name", name).putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
        load()
    }

    private fun choose(name: String, lat: Double, lon: Double) {
        val fa = favs()
        var has = false
        for (i in 0 until fa.length()) if (fa.getJSONObject(i).getString("name") == name) has = true
        if (!has) fa.put(JSONObject().put("name", name).put("lat", lat).put("lon", lon))
        prefs.edit().putString("favs", fa.toString()).apply()
        setCurrent(name, lat, lon)
    }

    private fun searchDialog() {
        val et = EditText(this)
        et.hint = "Nume oraș"
        AlertDialog.Builder(this).setTitle("Caută oraș").setView(et)
            .setPositiveButton("Caută") { _, _ -> search(et.text.toString().trim()) }
