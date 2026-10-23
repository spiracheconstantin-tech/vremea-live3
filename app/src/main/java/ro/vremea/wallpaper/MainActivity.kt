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
        val scroll = ScrollView(this).apply { addView(body) }
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
            .setNegativeButton("Anulează", null).show()
    }

    private fun search(q: String) {
        if (q.isEmpty()) return
        Thread {
            try {
                val u = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(q, "UTF-8") + "&count=8&language=ro"
                val res = JSONObject(URL(u).readText()).optJSONArray("results")
                runOnUiThread {
                    if (res == null || res.length() == 0) { toast("Niciun rezultat"); return@runOnUiThread }
                    val names = Array(res.length()) {
                        val o = res.getJSONObject(it)
                        listOf(o.optString("name"), o.optString("admin1"), o.optString("country")).filter { s -> s.isNotEmpty() }.joinToString(", ")
                    }
                    AlertDialog.Builder(this@MainActivity).setTitle("Alege orașul").setItems(names) { _, i ->
                        val o = res.getJSONObject(i)
                        choose(names[i], o.getDouble("latitude"), o.getDouble("longitude"))
                    }.show()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Căutarea a eșuat. Verifică internetul.") }
            }
        }.start()
    }

    private fun showFavs() {
        val fa = favs()
        if (fa.length() == 0) { toast("Nu ai orașe salvate. Caută unul."); return }
        val names = Array(fa.length()) { fa.getJSONObject(it).getString("name") }
        AlertDialog.Builder(this).setTitle("Orașele mele").setItems(names) { _, i ->
            val o = fa.getJSONObject(i)
            setCurrent(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))
        }.show()
    }

    private fun removeCurrent() {
        val cur = prefs.getString("name", "")
        val fa = favs()
        val nf = JSONArray()
        for (i in 0 until fa.length()) if (fa.getJSONObject(i).getString("name") != cur) nf.put(fa.getJSONObject(i))
        prefs.edit().putString("favs", nf.toString()).apply()
        toast("Șters din listă")
    }

    private fun myLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (l != null) { setCurrent("Locația mea", l.latitude, l.longitude); return }
            } catch (e: Exception) { }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        byIp()
    }

    private fun byIp() {
        Thread {
            try {
                val j = JSONObject(URL("https://ipwho.is/").readText())
                if (j.optBoolean("success", false)) {
              runOnUiThread { setCurrent("Locația mea", j.getDouble("latitude"), j.getDouble("longitude")) }
                    return@Thread
                }
            } catch (e: Exception) { }
            runOnUiThread { if (!prefs.contains("lat")) setCurrent("București", 44.4268, 26.1025) }
        }.start()
    }

    // ---------- date meteo ----------
    private fun loadOld() {
        val lat = prefs.getString("lat", null) ?: return
        val lon = prefs.getString("lon", null) ?: return
        val f = prefs.getString("units", "c") == "f"
        titleTv.text = prefs.getString("name", "")
        Thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=7" + (if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")
                val j = JSONObject(URL(url).readText())
                runOnUiThread { render(j, f) }
            } catch (e: Exception) {
                runOnUiThread { toast("Nu pot încărca datele. Verifică internetul.") }
            }
        }.start()
    }
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
        val tC = d.optDouble("air_temperature", Double.NaN)
        if (!tC.isNaN()) {
            mix("temperature_2m", if (f) tC * 9.0 / 5.0 + 32.0 else tC)
            cur.put("apparent_temperature", cur.getDouble("apparent_temperature") + (cur.getDouble("temperature_2m") - oldT))
        }
        val ws = d.optDouble("wind_speed", Double.NaN)
        if (!ws.isNaN()) mix("wind_speed_10m", ws * ms)
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

    private fun load() {
        val lat = prefs.getString("lat", null) ?: return
        val lon = prefs.getString("lon", null) ?: return
        val f = prefs.getString("units", "c") == "f"
        val src = prefs.getString("src", "om") ?: "om"
        titleTv.text = prefs.getString("name", "")
        Thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=7" + (if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")
                val j = JSONObject(URL(url).readText())
                var label = "Open-Meteo"
                if (src != "om") {
                    val met = fetchMet(lat, lon)
                    if (met != null) {
                        mergeMet(j, met, src == "avg", f)
                        label = if (src == "avg") "Open-Meteo + MET Norway (medie)" else "MET Norway (yr.no), prognoză Open-Meteo"
                    } else {
                        runOnUiThread { toast("MET Norway indisponibil, folosesc Open-Meteo") }
                    }
                }
                j.put("src", label)
                runOnUiThread { render(j, f) }
            } catch (e: Exception) {
                runOnUiThread { toast("Nu pot încărca datele. Verifică internetul.") }
            }
        }.start()
    }
    private fun cell(t: String, w: Float, end: Boolean = false) = tv(t, 15f).apply {
        if (end) gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams(0, -2, w)
    }

    private fun render(j: JSONObject, f: Boolean) {
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val hr = j.getJSONObject("hourly")
        val day = cur.getInt("is_day") == 1
        val (k, label, icon) = info(cur.getInt("weather_code"), day)
        setBg(k, day)
        val tu = if (f) "°F" else "°C"
        val su = if (f) "mph" else "km/h"
        body.removeAllViews()

        add(tv(icon, 72f).apply { gravity = Gravity.CENTER })
        add(tv("${cur.getDouble("temperature_2m").roundToInt()}°", 88f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        add(tv(label, 22f).apply { gravity = Gravity.CENTER })
        val mx = d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
        val mn = d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
        add(tv("Max $mx°  ·  Min $mn°", 16f, alpha = .9f).apply { gravity = Gravity.CENTER }, 4)

        // carduri cu date
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SV", "V", "NV")
        val dir = dirs[((cur.getDouble("wind_direction_10m") + 22.5) / 45).toInt() % 8]
        val uv = d.getJSONArray("uv_index_max").getDouble(0)
        val uvTxt = when { uv < 3 -> "Scăzut"; uv < 6 -> "Moderat"; uv < 8 -> "Ridicat"; uv < 11 -> "Foarte ridicat"; else -> "Extrem" }
        val cards = listOf(
            Triple("💨 Vânt", "${cur.getDouble("wind_speed_10m").roundToInt()} $su", "Din $dir · rafale ${cur.getDouble("wind_gusts_10m").roundToInt()}"),
            Triple("🌡️ RealFeel", "${cur.getDouble("apparent_temperature").roundToInt()}$tu", "Temperatura resimțită"),
            Triple("💧 Umiditate", "${cur.getInt("relative_humidity_2m")}%", "Aer relativ"),
            Triple("🧭 Presiune", "${cur.getDouble("pressure_msl").roundToInt()} hPa", "La nivelul mării"),
            Triple("☁️ Nori", "${cur.getInt("cloud_cover")}%", "Acoperire"),
            Triple("☔ Precipitații", "${cur.getDouble("precipitation")} mm", "Șanse azi: ${d.getJSONArray("precipitation_probability_max").optInt(0)}%"),
            Triple("🔆 Index UV", "%.1f".format(uv), uvTxt),
            Triple("🌅 Soare", "↑ " + d.getJSONArray("sunrise").getString(0).substring(11), "↓ " + d.getJSONArray("sunset").getString(0).substring(11))
        )
        for (r in cards.chunked(2)) {
            val row = LinearLayout(this)
            for (c in r) {
                val p = panel()
                p.addView(tv(c.first, 13f, alpha = .85f))
                p.addView(tv(c.second, 24f, true))
                p.addView(tv(c.third, 12f, alpha = .85f))
                row.addView(p, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            add(row, 8)
        }

        // următoarele 24 de ore
        val hp = panel()
        hp.addView(tv("Următoarele 24 de ore", 14f, alpha = .85f))
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val hl = LinearLayout(this)
        val times = hr.getJSONArray("time")
        val nowH = cur.getString("time").substring(0, 13)
        var s = 0
        while (s < times.length() - 1 && times.getString(s).substring(0, 13) < nowH) s++
        for (i in s until minOf(s + 24, times.length())) {
            val hh = times.getString(i).substring(11, 13).toInt()
            val ic = info(hr.getJSONArray("weather_code").getInt(i), hh in 6..19).third
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(4))
            }
            col.addView(tv("%02d:00".format(hh), 13f, alpha = .85f))
            col.addView(tv(ic, 26f))
            col.addView(tv("${hr.getJSONArray("temperature_2m").getDouble(i).roundToInt()}°", 16f, true))
            val pp = hr.getJSONArray("precipitation_probability").optInt(i)
            col.addView(tv(if (pp > 0) "💧$pp%" else " ", 11f, alpha = .85f))
            hl.addView(col)
        }
        hs.addView(hl)
        hp.addView(hs)
        add(hp, 12)

        // 7 zile
        val dpn = panel()
        dpn.addView(tv("Următoarele 7 zile", 14f, alpha = .85f))
        val inF = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outF = SimpleDateFormat("EEEE", Locale("ro"))
        val dates = d.getJSONArray("time")
        for (i in 0 until dates.length()) {
            val nm = if (i == 0) "Azi" else outF.format(inF.parse(dates.getString(i))!!).replaceFirstChar { it.uppercase() }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(2)) }
            row.addView(cell(nm, 2.2f))
            row.addView(cell(info(d.getJSONArray("weather_code").getInt(i), true).third, 0.8f))
            row.addView(cell("💧${d.getJSONArray("precipitation_probability_max").optInt(i)}%", 1.4f))
            row.addView(cell("${d.getJSONArray("temperature_2m_min").getDouble(i).roundToInt()}° / ${d.getJSONArray("temperature_2m_max").getDouble(i).roundToInt()}°", 2f, true))
            dpn.addView(row)
        }
        add(dpn, 12)
        add(tv(""src", "Open-Meteo", 11f, alpha = .6f).apply { gravity = Gravity.CENTER }, 14)
    }
}
