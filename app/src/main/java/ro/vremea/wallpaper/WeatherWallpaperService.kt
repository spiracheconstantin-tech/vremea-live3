package ro.vremea.wallpaper

import android.Manifest
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.util.Calendar
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.json.JSONObject
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val DAY = listOf(
    "#1F8FE0", "#BFE6FB", "#3E9BDB", "#C9E4F5", "#5C7A95", "#CFDBE5", "#8A979F",
    "#D6DCDF", "#2F4256", "#7F97AB", "#7C93AD", "#E8EFF6", "#1E2433", "#5A6683"
).map { Color.parseColor(it) }
private val NIGHT = listOf(
    "#0A1230", "#3A5088", "#101A3A", "#3F5590", "#1E2535", "#48546A", "#2A3038",
    "#56606A", "#141C28", "#3A4A5C", "#2A3550", "#6A7C98", "#0D111B", "#2E374D"
).map { Color.parseColor(it) }

private val MARIA = arrayOf(
    floatArrayOf(-0.28f, -0.34f, 0.26f, 0.30f), floatArrayOf(0.14f, -0.30f, 0.17f, 0.28f),
    floatArrayOf(0.30f, -0.02f, 0.20f, 0.30f), floatArrayOf(-0.58f, 0.02f, 0.28f, 0.26f),
    floatArrayOf(-0.22f, 0.36f, 0.18f, 0.20f), floatArrayOf(0.62f, -0.22f, 0.10f, 0.30f),
    floatArrayOf(0.00f, 0.18f, 0.12f, 0.12f)
)
private val CRATERS = arrayOf(
    floatArrayOf(-0.12f, 0.72f, 0.07f, 1f), floatArrayOf(-0.30f, 0.05f, 0.045f, 1f),
    floatArrayOf(0.40f, 0.50f, 0.05f, 0f), floatArrayOf(0.52f, 0.28f, 0.04f, 0f),
    floatArrayOf(-0.62f, -0.38f, 0.05f, 0f), floatArrayOf(0.22f, 0.62f, 0.04f, 0f),
    floatArrayOf(-0.05f, 0.45f, 0.035f, 0f), floatArrayOf(0.70f, 0.05f, 0.035f, 0f)
)

private class P(var x: Float, var y: Float, var v: Float, var s: Float)

class WeatherWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WeatherEngine()

    inner class WeatherEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val rect = RectF()
        private val rnd = Random(System.nanoTime())
        private val showHills = true
        private var visible = false
        private var w = 1f
        private var h = 1f
        @Volatile private var code = 0
        @Volatile private var temp = 15.0
        @Volatile private var wind = 5.0
        @Volatile private var windDir = 270.0
        @Volatile private var day = true
        @Volatile private var cloud = 0
        @Volatile private var precip = 0.0
        @Volatile private var fahr = false
        @Volatile private var utcOff = 0
        @Volatile private var sunrise = 360
        @Volatile private var sunset = 1080
        @Volatile private var moonLatitude = 44.4268
        @Volatile private var moonLongitude = 26.1025
        private var moonPositionMinute = Long.MIN_VALUE
        private var cachedMoonPosition = MoonSkyPosition.Point(0.0, 0.0)
        private var parts = ArrayList<P>()
        private var frostFlakes = ArrayList<P>()
        private var hail = ArrayList<P>()
        private var clouds = ArrayList<P>()
        private var wisps = ArrayList<P>()
        private var glass = ArrayList<P>()
        private var flash = 0f
        private var boltUntil = 0L
        private val bolt = ArrayList<PointF>()
        private val bolt2 = ArrayList<PointF>()
        private var nextBolt = 0L
        private var cloudBmp: Bitmap? = null
        private var cloudKey = 0
        private var moonBmp: Bitmap? = null
        private var moonKey = -1
        private var flakeBmp: Bitmap? = null
        private var wispBmp: Bitmap? = null
        private var glassBmp: Bitmap? = null
        private var hillBmp: Bitmap? = null
        private var hillKey = -1L
        private val spn = 60
        private val spX = FloatArray(spn)
        private val spY = FloatArray(spn)
        private val spA = FloatArray(spn) { 1f }
        private val spS = FloatArray(spn)
        private var spI = 0
        private var skyShader: Shader? = null
        private var skyKey = -1L
        private var vigShader: Shader? = null
        private var vigKey = -1L
        private var lastMs = 0L
        private var dtf = 1f
        private val fx = LifeFx()
        private val skyFade = SkyFade()
        private val meteors = MeteorFx()
                private val flat = FlatSky()
        private val ground = GroundFx(this@WeatherWallpaperService)
        private var flatStyle = false
        private val air = AirFx(this@WeatherWallpaperService)
        private var locked = false
        private var lockAt = 0L
        private val fogGlass = FogGlass()
        private val birds = BirdFx()
        private val aurora = AuroraFx()
        private val butterflies = ButterflyFx()
        private val grass = GrassFx()
        private val shadows = ShadowFx()
        private val lake = LakeFx()
        private val planets = PlanetFx()
        private val stormGlow = StormGlowFx()
        private val weatherTransition = WeatherTransitionFx()
        private val seasonalFx = SeasonalFx()
        private var fxLeaves = true
        private var fxFrost = true
        private var fxBlossoms = true
        private var fxPollen = true
        private var fxFireflies = true
        private var fxDew = true
        private val fxPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("wp_fx_") == true || key == "wp_scene" || key == "wp_reduce_motion" || key == "wp_stars" || key == "wp_quality") {
                handler.removeCallbacks(reloadFxSettings)
                handler.postDelayed(reloadFxSettings, 180L)
            } else if (key == "test" || key == "test_min") {
                handler.removeCallbacks(reloadTestMode)
                handler.postDelayed(reloadTestMode, 120L)
            }
        }
        private val reloadFxSettings = Runnable { if (visible) { init(); if (reduceMotion) sm.unregisterListener(tiltL) else sm.registerListener(tiltL, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME) } }
        private val reloadTestMode = Runnable {
            if (visible) {
                if (TestMode.read(this@WeatherWallpaperService) == null) restoreLastRealWeather()
                lastLoadKey = ""
                lastLoadAt = 0L
                fetch()
            }
        }
        private var fxAurora = true
        private var fxButterflies = true
        private var fxGrass = true
        private var fxShadows = true
        private var fxLake = true
        private var fxPlanets = true
        private var fxStormGlow = true
        private var fxWeatherTransition = true
        private var fxBirds = true
        private var fxLightning = true
        private var scene = 0
        private var reduceMotion = false
        private val hills3 = Hills3D()
        private var qMul = 1f
        private var frameMs = 16L
        private var showStars = true
        private var showHillsV = true
        private var tiltX = 0f
        private var tiltY = 0f
        private var tgX = 0f
        private var tgY = 0f
        private val sm by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
        private val tiltL = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (!reduceMotion) {
                    tgX = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                    tgY = (e.values[2] / 9.81f - 0.4f).coerceIn(-1f, 1f)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

        private val tick = object : Runnable {
            override fun run() {
                val s = SystemClock.uptimeMillis()
                draw()
                if (visible) {
                    val used = SystemClock.uptimeMillis() - s
                                       val nowL = SystemClock.uptimeMillis()
                    if (nowL - lockAt > 2000L) {
                        lockAt = nowL
                        locked = (getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
                    }
                    handler.postDelayed(this, max(1L, (if (locked) 100L else frameMs) - used))
                }
            }
        }
        private val refresh = object : Runnable {
            override fun run() {
                fetch()
                if (visible) handler.postDelayed(this, 15 * 60_000L)
            }
        }

        private fun cat(): Int = when {
            code == 0 -> 0
            code <= 2 -> 1
            code == 3 -> 2
            code in 45..48 -> 3
            code in 51..67 || code in 80..82 -> 4
            code in 71..77 || code in 85..86 -> 5
            else -> 6
        }

        private fun intensity(): Float {
            val mm = precip
            if (mm > 0.05) return mm.toFloat()
            return when {
                code in 51..57 -> 0.3f
                code == 61 || code == 80 -> 1.0f
                code == 63 || code == 81 -> 3.0f
                code == 65 || code == 66 || code == 67 || code == 82 -> 8.0f
                code >= 95 -> 6.0f
                else -> 2.0f
            }
        }

        private fun rainN(): Int {
            val i = intensity()
            return (40f + 230f * (1f - exp(-i / 4f))).toInt()
        }

        private fun nowMin(): Int = (((System.currentTimeMillis() / 1000 + utcOff) / 60) % 1440).toInt()

        private fun useShader(s: Shader?) {
            paint.alpha = 255
            paint.shader = s
        }

        private fun init() {
            val k = cat()
            val heavy = code == 75 || code == 86
            if (k != 6) {
                nextBolt = 0L
                boltUntil = 0L
                flash = 0f
            }
                        val ap0 = getSharedPreferences("app", MODE_PRIVATE)
            val ql = ap0.getString("wp_quality", "normal")
            qMul = if (ql == "eco") 0.5f else if (ql == "max") 1.3f else 1f
            frameMs = if (ql == "eco") 33L else 16L
            val pmgr = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val bi = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val lvl = if (bi != null) bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 100) * 100 / max(1, bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)) else 100
            val autoEco = pmgr.isPowerSaveMode || lvl < 15
            if (pmgr.isPowerSaveMode || lvl < 20) {
                frameMs = max(frameMs, 33L)
                qMul = min(qMul, 0.7f)
            }
            if (autoEco) { frameMs = max(frameMs, 66L); qMul = min(qMul, 0.5f) }
            reduceMotion = ap0.getBoolean("wp_reduce_motion", false) || autoEco
            if (reduceMotion) {
                frameMs = max(frameMs, 66L)
                qMul = min(qMul, 0.55f)
            }
            showStars = ap0.getBoolean("wp_stars", true)
            showHillsV = ap0.getBoolean("wp_hills", true)
            fxAurora = ap0.getBoolean("wp_fx_aurora", true)
            fxButterflies = ap0.getBoolean("wp_fx_butterflies", true)
            fxGrass = ap0.getBoolean("wp_fx_grass", true)
            fxShadows = ap0.getBoolean("wp_fx_shadows", true)
            fxLake = ap0.getBoolean("wp_fx_lake", true)
            fxPlanets = ap0.getBoolean("wp_fx_planets", true)
            fxStormGlow = ap0.getBoolean("wp_fx_storm_glow", true)
            fxWeatherTransition = ap0.getBoolean("wp_fx_transition", true)
            fxBirds = ap0.getBoolean("wp_fx_birds", true)
            fxLightning = ap0.getBoolean("wp_fx_lightning", true)
            fxLeaves = ap0.getBoolean("wp_fx_leaves", true)
            fxFrost = ap0.getBoolean("wp_fx_frost", true)
            fxBlossoms = ap0.getBoolean("wp_fx_blossoms", true)
            fxPollen = ap0.getBoolean("wp_fx_pollen", true)
            fxFireflies = ap0.getBoolean("wp_fx_fireflies", true)
            fxDew = ap0.getBoolean("wp_fx_dew", true)
            if (k != 6 || !fxLightning) {
                nextBolt = 0L
                boltUntil = 0L
                flash = 0f
            }
            scene = ap0.getInt("wp_scene", 0).coerceIn(0, 2)
                        flatStyle = ap0.getBoolean("wp_flat", false)
            val n = when (k) {
                4, 6 -> rainN()
                5 -> when (code) { 71, 85 -> 78; 73 -> 145; 75, 86 -> 245; else -> 105 }
                else -> 0
            }
            parts = ArrayList()
            repeat((n * qMul).toInt()) {
                val d = rnd.nextFloat()
                parts.add(
                    if (k == 5) P(rnd.nextFloat() * w, rnd.nextFloat() * h, (1.5f + 3.5f * d) * (if (heavy) 1.5f else 1f), d)
                    else P(rnd.nextFloat() * w, rnd.nextFloat() * h, 18f + rnd.nextFloat() * 12f + intensity().coerceIn(0f, 10f) * 1.7f, d)
                )
            }
            frostFlakes = ArrayList()
            val initTempC = if (fahr) ((temp - 32.0) * 5.0 / 9.0).toFloat() else temp.toFloat()
            if (initTempC <= -8f) repeat((115 * qMul).toInt()) {
                val d = rnd.nextFloat()
                frostFlakes.add(P(rnd.nextFloat() * w, rnd.nextFloat() * h, 1.1f + 2.2f * d, d))
            }
            hail = ArrayList()
            if (k == 6 && (code == 96 || code == 99)) {
                repeat(60) { hail.add(P(rnd.nextFloat() * w, rnd.nextFloat() * h, 30f + rnd.nextFloat() * 12f, 3f + rnd.nextFloat() * 3f)) }
            }
            glass = ArrayList()
            if (k == 4 || k == 6) {
                val gl = (3f + 5f * (intensity() / 8f).coerceIn(0f, 1f)).toInt()
                repeat(gl) {
                    glass.add(P(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.9f, 0.1f + 0.7f * rnd.nextFloat(), rnd.nextFloat()))
                }
            }
            val nc = when (k) {
                0 -> 0
                1 -> max(3, (cloud / 100f * 8f).roundToInt())
                2 -> max(4, (cloud / 100f * 6f).roundToInt())
                6 -> 6
                3 -> 3
                else -> 3
            }
            clouds = ArrayList()
            repeat(nc) {
                val dpt = rnd.nextFloat()
                val sz = 0.7f + 0.9f * dpt
                clouds.add(P(rnd.nextFloat() * w, h * (0.06f + 0.30f * (1f - dpt)), 0.18f + 0.52f * dpt, sz))
            }
            wisps = ArrayList()
            if (k == 3) {
                repeat(9) { wisps.add(P(rnd.nextFloat() * w, h * (0.30f + 0.58f * rnd.nextFloat()), 0.12f + 0.32f * rnd.nextFloat(), rnd.nextFloat())) }
            }
        }

        private fun hm(s: String): Int = s.substring(11, 13).toInt() * 60 + s.substring(14, 16).toInt()
                private val wpModels = mapOf(
            "ecmwf" to "ecmwf_ifs025",
            "gfs" to "gfs_seamless",
            "icon" to "icon_seamless",
            "mf" to "meteofrance_seamless"
        )
        private var lastLoadAt = 0L
        private var lastLoadKey = ""

        private fun saveLastRealWeather() {
            getSharedPreferences("app", MODE_PRIVATE).edit()
                .putInt("wp_real_code", code).putString("wp_real_temp", temp.toString())
                .putString("wp_real_wind", wind.toString()).putString("wp_real_wind_dir", windDir.toString())
                .putInt("wp_real_cloud", cloud).putString("wp_real_precip", precip.toString())
                .putBoolean("wp_real_day", day).putBoolean("wp_real_fahr", fahr)
                .putInt("wp_real_utc_off", utcOff).putInt("wp_real_sunrise", sunrise).putInt("wp_real_sunset", sunset)
                .apply()
        }

        private fun restoreLastRealWeather() {
            val p = getSharedPreferences("app", MODE_PRIVATE)
            if (!p.contains("wp_real_code")) return
            code = p.getInt("wp_real_code", 0)
            temp = p.getString("wp_real_temp", "15")?.toDoubleOrNull() ?: 15.0
            wind = p.getString("wp_real_wind", "5")?.toDoubleOrNull() ?: 5.0
            windDir = p.getString("wp_real_wind_dir", "270")?.toDoubleOrNull() ?: 270.0
            cloud = p.getInt("wp_real_cloud", 0)
            precip = p.getString("wp_real_precip", "0")?.toDoubleOrNull() ?: 0.0
            day = p.getBoolean("wp_real_day", true)
            fahr = p.getBoolean("wp_real_fahr", false)
            utcOff = p.getInt("wp_real_utc_off", 0)
            sunrise = p.getInt("wp_real_sunrise", 360)
            sunset = p.getInt("wp_real_sunset", 1080)
            handler.post { init() }
        }
        private fun httpGet(u: String, ua: String? = null): String {
            val conn = URL(u).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
                if (ua != null) setRequestProperty("User-Agent", ua)
            }
            return conn.getInputStream().bufferedReader().readText()
        }

        private fun symCode(s: String): Int {
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

        private fun load(lat: Double, lon: Double) {
            moonLatitude = lat
            moonLongitude = lon
            moonPositionMinute = Long.MIN_VALUE
        val tm = TestMode.read(this@WeatherWallpaperService)
            if (tm != null) {
                code = tm.code
                temp = tm.temp
                wind = tm.wind
                windDir = tm.dir
                cloud = tm.cloud
                precip = tm.precip
                sunrise = 7 * 60
                sunset = 19 * 60
                day = tm.minute >= sunrise && tm.minute <= sunset
                fahr = false
                utcOff = tm.minute * 60 - ((System.currentTimeMillis() / 1000) % 86400L).toInt()
                handler.post { init() }
                return
            }
            val ap0 = getSharedPreferences("app", MODE_PRIVATE)
            val key0 = "$lat|$lon|" + ap0.getString("src", "om") + "|" + ap0.getString("units", "c")
            val nowT = SystemClock.uptimeMillis()
            if (key0 == lastLoadKey && nowT - lastLoadAt < 5 * 60_000L) return
            lastLoadKey = key0
            lastLoadAt = nowT
            Thread {
                try {
                    val ap = getSharedPreferences("app", MODE_PRIVATE)
                    val f = ap.getString("units", "c") == "f"
                    val src = ap.getString("src", "om") ?: "om"
                    val unit = if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""
                    val cols = "temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,cloud_cover,precipitation"

                    val j = JSONObject(
                        httpGet(
                            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                                "&current=$cols,is_day&daily=sunrise,sunset&forecast_days=1&timezone=auto$unit"
                        )
                    )
                    val cur = j.getJSONObject("current")
                    try {
                        val d = j.getJSONObject("daily")
                        val sr = d.getJSONArray("sunrise")
                        val ss = d.getJSONArray("sunset")
                        sunrise = hm(sr.getString(sr.length() - 1))
                        sunset = hm(ss.getString(ss.length() - 1))
                    } catch (e: Exception) { }

                    class Smp(val t: Double, val ws: Double, val wd: Double, val cl: Double, val pr: Double, val code: Int)

                    val base = Smp(
                        cur.getDouble("temperature_2m"),
                        cur.getDouble("wind_speed_10m"),
                        cur.optDouble("wind_direction_10m", 270.0),
                        cur.optDouble("cloud_cover", 0.0),
                        cur.optDouble("precipitation", 0.0),
                        cur.getInt("weather_code")
                    )
                    val extra = ArrayList<Smp>()

                    if (src == "met" || src == "avg") {
                        try {
                            val txt = httpGet(
                                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" +
                                    String.format(java.util.Locale.US, "%.4f", lat) + "&lon=" +
                                    String.format(java.util.Locale.US, "%.4f", lon),
                                "VremeaLive/1.0 github.com/spiracheconstantin-tech/vremea-live3"
                            )
                            val data = JSONObject(txt).getJSONObject("properties")
                                .getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
                            val dd = data.getJSONObject("instant").getJSONObject("details")
                            val tc = dd.optDouble("air_temperature", Double.NaN)
                            if (!tc.isNaN()) {
                                val ws = dd.optDouble("wind_speed", Double.NaN)
                                val n1 = data.optJSONObject("next_1_hours")
                                val sym = n1?.optJSONObject("summary")?.optString("symbol_code", "") ?: ""
                                val pa = n1?.optJSONObject("details")?.optDouble("precipitation_amount", Double.NaN) ?: Double.NaN
                                extra.add(
                                    Smp(
                                        if (f) tc * 9.0 / 5.0 + 32.0 else tc,
                                        ws * (if (f) 2.23694 else 3.6),
                                        dd.optDouble("wind_from_direction", Double.NaN),
                                        dd.optDouble("cloud_area_fraction", Double.NaN),
                                        pa,
                                        symCode(sym)
                                    )
                                )
                            }
                        } catch (e: Exception) { }
                    }

                    val keys: List<String> = when {
                        src == "avg" -> listOf("ecmwf", "icon", "gfs")
                        wpModels.containsKey(src) -> listOf(src)
                        else -> emptyList()
                    }
                    for (key in keys) {
                        try {
                            val mc = JSONObject(
                                httpGet(
                                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                                        "&models=" + wpModels[key] + "&current=$cols&timezone=auto$unit"
                                )
                            ).getJSONObject("current")
                            val tt = mc.optDouble("temperature_2m", Double.NaN)
                            if (!tt.isNaN()) {
                                extra.add(
                                    Smp(
                                        tt,
                                        mc.optDouble("wind_speed_10m", Double.NaN),
                                        mc.optDouble("wind_direction_10m", Double.NaN),
                                        mc.optDouble("cloud_cover", Double.NaN),
                                        mc.optDouble("precipitation", Double.NaN),
                                        mc.optInt("weather_code", -1)
                                    )
                                )
                            }
                        } catch (e: Exception) { }
                    }

                    val use: List<Smp> = when {
                        src == "avg" -> listOf(base) + extra
                        extra.isNotEmpty() -> extra
                        else -> listOf(base)
                    }
                    fun mean(g: (Smp) -> Double, def: Double): Double {
                        val v = use.map(g).filter { !it.isNaN() }
                        return if (v.isEmpty()) def else v.average()
                    }

                    val newCode = if (src != "avg" && extra.isNotEmpty() && extra[0].code >= 0) extra[0].code else base.code
                    val newDir = if (src != "avg" && extra.isNotEmpty() && !extra[0].wd.isNaN()) extra[0].wd else base.wd

                    utcOff = j.optInt("utc_offset_seconds", 0)
                    fahr = f
                    day = cur.getInt("is_day") == 1
                    temp = mean({ it.t }, base.t)
                    wind = mean({ it.ws }, base.ws)
                    windDir = newDir
                    cloud = mean({ it.cl }, base.cl).roundToInt()
                    precip = mean({ it.pr }, base.pr)
                    code = newCode
                    saveLastRealWeather()
                    handler.post { init() }
                } catch (e: Exception) {
                    lastLoadAt = 0L
                }
            }.start()
        }
        private fun loadOm(lat: Double, lon: Double) {
            Thread {
                try {
                    val f = getSharedPreferences("app", MODE_PRIVATE).getString("units", "c") == "f"
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,cloud_cover,precipitation" +
                        "&daily=sunrise,sunset&forecast_days=1&timezone=auto" +
                        (if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")
                    val conn = URL(url).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                    val j = JSONObject(conn.getInputStream().bufferedReader().readText())
                    val cur = j.getJSONObject("current")

                    try {
                        val d = j.getJSONObject("daily")
                        val sr = d.getJSONArray("sunrise")
                        val ss = d.getJSONArray("sunset")
                        sunrise = hm(sr.getString(sr.length() - 1))
                        sunset = hm(ss.getString(ss.length() - 1))
                    } catch (e: Exception) { }

                    utcOff = j.optInt("utc_offset_seconds", 0)
                    fahr = f
                    cloud = cur.optInt("cloud_cover", 0)
                    precip = cur.optDouble("precipitation", 0.0)
                    code = cur.getInt("weather_code")
                    temp = cur.getDouble("temperature_2m")
                    wind = cur.getDouble("wind_speed_10m")
                    windDir = cur.optDouble("wind_direction_10m", 270.0)
                    day = cur.getInt("is_day") == 1
                    saveLastRealWeather()
                    handler.post { init() }
                } catch (e: Exception) {
                    // fara internet: pastreaza ultima stare
                }
            }.start()
        }

        private fun saveLoc(lat: Double, lon: Double) {
            getSharedPreferences("loc", MODE_PRIVATE).edit()
                .putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
        }

        private fun loadByIp() {
            Thread {
                try {
                    val conn = URL("https://ipwho.is/").openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                    val j = JSONObject(conn.getInputStream().bufferedReader().readText())
                    if (j.optBoolean("success", false)) {
                        val lat = j.getDouble("latitude")
                        val lon = j.getDouble("longitude")
                        saveLoc(lat, lon)
                        load(lat, lon)
                    }
                } catch (e: Exception) { }
            }.start()
        }

        @Suppress("DEPRECATION")
        private fun fetch() {
            val ap = getSharedPreferences("app", MODE_PRIVATE)
            val aName = ap.getString("name", null)
            val aLat = ap.getString("lat", null)?.toDoubleOrNull()
            val aLon = ap.getString("lon", null)?.toDoubleOrNull()
            if (aName != null && aName != "Locația mea" && aLat != null && aLon != null) {
                load(aLat, aLon)
                return
            }

            val prefs = getSharedPreferences("loc", MODE_PRIVATE)
            val lat0 = prefs.getString("lat", null)?.toDoubleOrNull() ?: 44.4268
            val lon0 = prefs.getString("lon", null)?.toDoubleOrNull() ?: 26.1025
            load(lat0, lon0)

            val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val netOn = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
            if (!granted || !netOn) { loadByIp(); return }
            try {
                val last = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
                if (last != null) { saveLoc(last.latitude, last.longitude); load(last.latitude, last.longitude) } else loadByIp()
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(l: Location) { saveLoc(l.latitude, l.longitude); load(l.latitude, l.longitude) }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, Looper.getMainLooper())
            } catch (e: Exception) {
                loadByIp()
            }
        }

        private val energyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                handler.removeCallbacks(reloadFxSettings)
                handler.postDelayed(reloadFxSettings, 250L)
            }
        }
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            getSharedPreferences("app", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(fxPrefsListener)
            val energyFilter = IntentFilter().apply { addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED); addAction(android.content.Intent.ACTION_BATTERY_LOW); addAction(android.content.Intent.ACTION_BATTERY_OKAY) }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(energyReceiver, energyFilter, android.content.Context.RECEIVER_NOT_EXPORTED) else registerReceiver(energyReceiver, energyFilter)
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width.toFloat(); h = height.toFloat()
            init()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
                        if (v && !reduceMotion) {
                sm.registerListener(tiltL, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
            } else {
                sm.unregisterListener(tiltL)
            }
            handler.removeCallbacks(tick)
            handler.removeCallbacks(refresh)
            lastMs = 0L
            if (v) {
                if (TestMode.read(this@WeatherWallpaperService) == null) restoreLastRealWeather()
                init()
                if (reduceMotion) sm.unregisterListener(tiltL)
                handler.post(refresh)
                handler.post(tick)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            handler.removeCallbacks(refresh)
            super.onSurfaceDestroyed(holder)
        }
                override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == android.app.WallpaperManager.COMMAND_TAP) {
                fx.tap(x.toFloat(), y.toFloat())
                                fogGlass.tap(x.toFloat(), y.toFloat())
                if (cat() == 6) nextBolt = 0L
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }
        override fun onDestroy() {
            getSharedPreferences("app", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(fxPrefsListener)
            try { unregisterReceiver(energyReceiver) } catch (_: Exception) { }
            handler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }

        private fun draw() {
            var c: Canvas? = null
            try {
                val nowMs = SystemClock.uptimeMillis()
                val dt = if (lastMs == 0L) 16L else nowMs - lastMs
                dtf = dt.coerceIn(1L, 100L) / 33.3f
                lastMs = nowMs
                c = (if (Build.VERSION.SDK_INT >= 26) surfaceHolder.lockHardwareCanvas() else surfaceHolder.lockCanvas()) ?: return
                render(c)
            } finally {
                if (c != null) try { surfaceHolder.unlockCanvasAndPost(c) } catch (e: Exception) {}
            }
        }

        private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
        )

        private fun shade(col: Int, f: Float): Int = blend(col, Color.BLACK, 1f - f)
        private fun mixWhite(col: Int, f: Float): Int = blend(col, Color.WHITE, f)

        // Nor cu volum: luminos sus, umbrit jos
        private fun cloudBitmap(col: Int): Bitmap {
            val key = col and 0x00FFFFFF
            val cached = cloudBmp
            if (cached != null && cloudKey == key) return cached

            val bmp = Bitmap.createBitmap(440, 240, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val silhouette = Path().apply {
                moveTo(28f, 177f)
                cubicTo(34f, 159f, 57f, 150f, 82f, 154f)
                cubicTo(77f, 126f, 99f, 104f, 128f, 105f)
                cubicTo(137f, 69f, 169f, 48f, 203f, 57f)
                cubicTo(233f, 63f, 251f, 83f, 254f, 108f)
                cubicTo(272f, 82f, 304f, 77f, 328f, 94f)
                cubicTo(347f, 107f, 352f, 126f, 346f, 145f)
                cubicTo(381f, 136f, 409f, 151f, 411f, 171f)
                cubicTo(413f, 190f, 392f, 202f, 365f, 202f)
                lineTo(70f, 202f)
                cubicTo(44f, 202f, 27f, 193f, 28f, 177f)
                close()
            }
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shade(col, 0.68f)
                maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            }
            cv.save()
            cv.translate(0f, 12f)
            cv.drawPath(silhouette, shadowPaint)
            cv.restore()

            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 62f, 0f, 208f,
                    intArrayOf(mixWhite(col, 0.28f), col, shade(col, 0.78f)),
                    floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
                )
            }
            cv.drawPath(silhouette, body)

            val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(46, 255, 255, 255)
                maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
            }
            cv.drawOval(RectF(108f, 73f, 267f, 132f), highlight)

            cloudBmp = bmp
            cloudKey = key
            return bmp
        }

        private fun flakeBitmap(): Bitmap {
            val old = flakeBmp
            if (old != null) return old
            val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val softGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    24f, 24f, 23f,
                    intArrayOf(
                        Color.argb(105, 255, 255, 255),
                        Color.argb(40, 238, 246, 255),
                        Color.argb(0, 220, 235, 255)
                    ),
                    floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP
                )
            }
            Canvas(bmp).drawCircle(24f, 24f, 23f, softGlow)
            flakeBmp = bmp
            return bmp
        }

        // Picatura pe sticla: margine intunecata, reflexie luminoasa
        private fun glassBitmap(): Bitmap {
            val old = glassBmp
            if (old != null) return old
            val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                32f, 36f, 30f,
                intArrayOf(Color.argb(0, 200, 220, 255), Color.argb(40, 200, 220, 255), Color.argb(150, 30, 45, 70)),
                floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
            cv.drawOval(RectF(4f, 2f, 60f, 62f), p)
            p.shader = null
            p.color = Color.argb(200, 255, 255, 255)
            cv.drawOval(RectF(18f, 12f, 30f, 22f), p)
            p.color = Color.argb(90, 255, 255, 255)
            cv.drawOval(RectF(24f, 44f, 42f, 52f), p)
            glassBmp = bmp
            return bmp
        }

        private fun wispBitmap(): Bitmap {
            val old = wispBmp
            if (old != null) return old
            val bmp = Bitmap.createBitmap(400, 60, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.WHITE
            p.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            Canvas(bmp).drawRoundRect(RectF(50f, 23f, 350f, 37f), 8f, 8f, p)
            wispBmp = bmp
            return bmp
        }

        // Dealuri pe 3 straturi, cu ceata intre ele (desenate o singura data)
        private fun hillBitmap(k: Int, bot: Int): Bitmap {
            val key = (k * 2 + (if (day) 1 else 0)).toLong() * 100000000L + w.toLong() * 10000L + h.toLong()
            val old = hillBmp
            if (old != null && hillKey == key) return old
            val bw = max(1, w.toInt())
            val bh = max(1, (h * 0.2f).toInt())
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val ground = when {
                k == 5 -> Color.rgb(236, 241, 249)
                day -> Color.rgb(52, 98, 72)
                else -> Color.rgb(14, 24, 32)
            }
            val bwf = bw.toFloat()
            val bhf = bh.toFloat()
            for (l in 0 until 3) {
                val col = blend(bot, ground, 0.30f + 0.32f * l)
                val baseY = bhf * (0.30f + 0.26f * l)
                val amp = bhf * (0.12f - 0.025f * l)
                val f1 = 0.004f + 0.0015f * l
                val ph = 1.7f * l + 0.6f
                val path = Path()
                path.moveTo(0f, bhf)
                var x = 0f
                while (x <= bwf + 10f) {
                    val y = baseY + amp * (sin(x * f1 + ph) + 0.5f * sin(x * f1 * 2.3f + ph * 1.9f))
                    path.lineTo(x, y)
                    x += 10f
                }
                path.lineTo(bwf, bhf)
                path.close()
                p.shader = LinearGradient(0f, baseY - amp, 0f, bhf, col, shade(col, 0.72f), Shader.TileMode.CLAMP)
                cv.drawPath(path, p)
                p.shader = null
                if (l < 2) {
                    p.shader = LinearGradient(
                        0f, baseY, 0f, bhf,
                        Color.argb(if (k == 3) 112 else if (k == 4 || k == 6) 88 else 70, Color.red(bot), Color.green(bot), Color.blue(bot)),
                        Color.argb(0, Color.red(bot), Color.green(bot), Color.blue(bot)),
                        Shader.TileMode.CLAMP
                    )
                    cv.drawRect(0f, baseY, bwf, bhf, p)
                    p.shader = null
                }
            }
            hillBmp = bmp
            hillKey = key
            return bmp
        }

        private fun moonPhase(): Float {
            val days = (System.currentTimeMillis() / 1000.0 - 947182440.0) / 86400.0
            val p = (days % 29.530588853) / 29.530588853
            return (if (p < 0) p + 1 else p).toFloat()
        }

        private fun moonBitmap(phase: Float): Bitmap {
            val key = (phase * 60).toInt()
            val old = moonBmp
            if (old != null && moonKey == key) return old
            val n = 220
            val px = IntArray(n * n)
            val a = (2.0 * PI * phase.toDouble()).toFloat()
            val lx = sin(a)
            val lz = -cos(a)
            for (j in 0 until n) {
                for (i in 0 until n) {
                    val x = (i + 0.5f) / n * 2f - 1f
                    val y = (j + 0.5f) / n * 2f - 1f
                    val r2 = x * x + y * y
                    if (r2 >= 1f) continue
                    val rr = sqrt(r2)
                    val z = sqrt(max(0f, 1f - r2))
                    val kLit = ((x * lx + z * lz + 0.04f) / 0.16f).coerceIn(0f, 1f)
                    var alb = 0.92f
                    for (m in MARIA) {
                        val dx = x - m[0]
                        val dy = y - m[1]
                        alb -= m[3] * exp(-(dx * dx + dy * dy) / (m[2] * m[2]))
                    }
                    for (cr in CRATERS) {
                        val d = sqrt((x - cr[0]) * (x - cr[0]) + (y - cr[1]) * (y - cr[1]))
                        if (d < cr[2]) alb -= 0.05f * (1f - d / cr[2])
                        if (abs(d - cr[2]) < 0.012f) alb += 0.08f
                        if (cr[3] > 0f && d < cr[2] * 1.7f) alb += 0.16f * (1f - d / (cr[2] * 1.7f))
                    }
                    alb *= (0.78f + 0.22f * z)
                    val lum = alb.coerceIn(0f, 1f) * (0.08f + 0.92f * kLit)
                    val edge = ((1f - rr) / 0.025f).coerceIn(0f, 1f)
                    val alpha = (edge * (0.2f + 0.8f * kLit) * 255f).toInt().coerceIn(0, 255)
                    px[j * n + i] = Color.argb(
                        alpha,
                        (246f * lum).toInt().coerceIn(0, 255),
                        (241f * lum).toInt().coerceIn(0, 255),
                        (224f * lum).toInt().coerceIn(0, 255)
                    )
                }
            }
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, n, 0, 0, n, n)
            moonBmp = bmp
            moonKey = key
            return bmp
        }
                private fun makeBolt() {
            bolt.clear()
            bolt2.clear()
            var x = w * (0.2f + 0.6f * rnd.nextFloat())
            var y = 0f
            bolt.add(PointF(x, y))
            while (y < h * 0.55f) {
                y += h * (0.03f + 0.04f * rnd.nextFloat())
                x += (rnd.nextFloat() - 0.5f) * w * 0.12f
                bolt.add(PointF(x, y))
            }
            val m = bolt[bolt.size / 2]
            val dir = if (rnd.nextBoolean()) 1f else -1f
            var bx = m.x
            var by = m.y
            bolt2.add(PointF(bx, by))
            repeat(4) {
                bx += dir * w * 0.05f * (0.5f + rnd.nextFloat())
                by += h * 0.035f
                bolt2.add(PointF(bx, by))
            }
        }

        private fun render(c: Canvas) {
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.isDither = true
            val k = cat()
            val t = SystemClock.uptimeMillis() / 1000f
            val pal = if (day) DAY else NIGHT
            val sc = h / 1920f
            val us = w / 1080f
            val f = fahr
            val tC = (if (f) (temp - 32.0) * 5.0 / 9.0 else temp).toFloat()
            val windKmh = (if (f) wind * 1.609 else wind).toFloat()
            val heavySnow = code == 75 || code == 86

            // directia vantului: spre dreapta sau spre stanga
            val wr = Math.toRadians(windDir).toFloat()
            val hx0 = -sin(wr)
            val hx = if (abs(hx0) < 0.2f) (if (hx0 < 0f) -0.2f else 0.2f) else hx0
            val wspd = min(windKmh, 40f) / 40f
                        val kf = min(1f, 0.1f * dtf)
            tiltX += (tgX - tiltX) * kf
            tiltY += (tgY - tiltY) * kf

            // cer cu degrade fin (creat o singura data)
            val sf = skyFade.colors(pal[k * 2], pal[k * 2 + 1], dtf)
            val dusk = if (day && k <= 2) (1f - abs(nowMin() - sunset) / 100f).coerceIn(0f, 1f) else 0f
            var top = sf[0]
            var bot = sf[1]
            if (day && k == 0) {
                top = blend(top, 0xFF1687D8.toInt(), 0.22f)
                bot = blend(bot, 0xFFD6F0FF.toInt(), 0.17f)
            }
            if (dusk > 0f) {
                top = blend(top, 0xFF465B83.toInt(), dusk * 0.46f)
                bot = blend(bot, 0xFFE98E69.toInt(), dusk * 0.38f)
            }
            val skK = (k * 2 + (if (day) 1 else 0)).toLong() * 100000000L + w.toLong() * 10000L + h.toLong() + (dusk * 1000f).roundToInt()
            if (skyShader == null || skyKey != skK || skyFade.changing) {
                skyShader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(top, blend(top, bot, 0.35f), blend(top, bot, 0.8f), mixWhite(bot, 0.12f)),
                    floatArrayOf(0f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP
                )
                skyKey = skK
            }
            useShader(skyShader)
            c.drawPaint(paint)
            useShader(null)

            // nuanta dupa temperatura
            if (tC < 15f) {
                c.drawColor(Color.argb(((15f - max(tC, -10f)) / 25f * 80).toInt(), 120, 170, 255))
                if (tC <= -8f) {
                    useShader(
                        RadialGradient(
                            w * 0.5f, h * 0.48f, max(w, h) * 0.78f,
                            intArrayOf(0, Color.argb(14, 208, 237, 255), Color.argb(78, 190, 226, 250)),
                            floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP
                        )
                    )
                    c.drawRect(0f, 0f, w, h, paint)
                    useShader(null)
                }
            } else {
                c.drawColor(Color.argb(((min(tC, 40f) - 15f) / 25f * 108).toInt(), 255, 130, 50))
                if (day && tC >= 35f) c.drawColor(Color.argb(24, 255, 170, 72))
            }

            // pozitia soarelui + stralucire la rasarit/apus
            val now = nowMin()
            val span = max(1, sunset - sunrise)
            val frac = (now - sunrise).toFloat() / span
            var sx = w * 0.78f
            var sy = h * 0.14f
            if (day && frac in 0f..1f) {
                sx = w * (0.12f + 0.76f * frac)
                sy = h * (0.34f - 0.22f * sin((PI * frac).toFloat()))
            }
                        sx += tiltX * w * 0.012f
            sy += tiltY * h * 0.008f
            val dist = min(abs(now - sunrise), abs(now - sunset))
            val twilight = (1f - dist / 110f).coerceIn(0f, 1f)
            if (k <= 2 && twilight > 0f) {
                val warmA = (twilight * twilight * 46f).toInt().coerceIn(0, 46)
                useShader(
                    LinearGradient(
                        0f, h * 0.18f, 0f, h * 0.86f,
                        intArrayOf(
                            Color.argb(0, 255, 160, 100),
                            Color.argb((warmA * 0.42f).toInt(), 255, 150, 100),
                            Color.argb(warmA, 255, 118, 72),
                            Color.argb(0, 255, 118, 72)
                        ),
                        floatArrayOf(0f, 0.47f, 0.73f, 1f), Shader.TileMode.CLAMP
                    )
                )
                c.drawRect(0f, h * 0.18f, w, h * 0.86f, paint)
                useShader(null)
            }

            // soare / luna + stele
            if (k <= 1 && flatStyle && day) flat.draw(c, true, sx, sy, w, h, t)
            if (k <= 1 && (!flatStyle || !day)) {
                if (day) {
                    val warm = (1f - dist / 120f).coerceIn(0f, 1f)
                    val sunGlow = 1f - warm * 0.38f
                    val r = w * 0.062f * (1f + 0.22f * warm)
                    val halo = blend(0xFFFFF3B0.toInt(), 0xFFFF9A5A.toInt(), warm)
                    val hrad = w * 0.62f * (1f + 0.03f * sin(t)) * (1f - 0.08f * warm)
                    useShader(
                        RadialGradient(
                            sx, sy, hrad,
                            intArrayOf(
                                Color.argb((118 * sunGlow).toInt(), Color.red(halo), Color.green(halo), Color.blue(halo)),
                                Color.argb((38 * sunGlow).toInt(), Color.red(halo), Color.green(halo), Color.blue(halo)), 0
                            ),
                            floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP
                        )
                    )
                    c.drawCircle(sx, sy, hrad, paint)
                    useShader(null)

                    // Halo-ul difuz și razele scurte din jurul discului păstrează aspectul solar fără fascicule pe ecran.
                    sp.strokeWidth = w * 0.0042f
                    for (i in 0 until 12) {
                        val a = t * 0.025f + i * (2f * PI.toFloat() / 12f)
                        val len = if (i % 2 == 0) 1.72f else 1.52f
                        sp.color = Color.argb(if (i % 2 == 0) 32 else 20, 255, 247, 224)
                        c.drawLine(sx + cos(a) * r * 1.38f, sy + sin(a) * r * 1.38f, sx + cos(a) * r * len, sy + sin(a) * r * len, sp)
                    }
                    useShader(
                        RadialGradient(
                            sx - r * .3f, sy - r * .3f, r * 1.35f,
                            intArrayOf(
                                0xFFFFFDF4.toInt(),
                                blend(0xFFFFF0C4.toInt(), 0xFFFFB56E.toInt(), warm),
                                blend(0xFFFFD985.toInt(), 0xFFE98754.toInt(), warm)
                            ),
                            floatArrayOf(0f, .68f, 1f), Shader.TileMode.CLAMP
                        )
                    )
                    c.drawCircle(sx, sy, r, paint)
                    useShader(null)
                } else {
                    // Calea Lactee
                    c.save()
                    c.rotate(-28f, w / 2f, h * 0.3f)
                    useShader(
                        LinearGradient(
                            0f, h * 0.12f, 0f, h * 0.30f,
                            intArrayOf(0, Color.argb(34, 200, 210, 255), 0),
                            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
                        )
                    )
                    c.drawRect(-w * 0.3f, h * 0.12f, w * 1.3f, h * 0.30f, paint)
                    useShader(null)
                    c.restore()
                        val visibleStarCount = if (showStars) (105f * (1f - cloud.coerceIn(0, 100) / 100f)).roundToInt() else 0
                        for (i in 0 until visibleStarCount) {
                        val x = ((i * 7919) % 1000) / 1000f * w
                        val y = ((i * 104729) % 1000) / 1000f * h * 0.62f
                        val big = i % 31 == 0
                        val tw = 0.5f + 0.5f * sin(t * (0.25f + (i % 5) * 0.12f) + i)
                        paint.color = Color.argb((45 + 105 * tw).toInt(), 232, 239, if (i % 7 == 0) 255 else 245)
                        c.drawCircle(x, y, (0.45f + (i % 3) * 0.28f + (if (big) 0.35f else 0f)) * (0.9f + 0.15f * tw), paint)
                        if (big) {
                            sp.color = Color.argb((24 * tw).toInt(), 225, 235, 255)
                            sp.strokeWidth = 1f
                            val l = 3f + 2f * tw
                            c.drawLine(x - l, y, x + l, y, sp)
                            c.drawLine(x, y - l, x, y + l, sp)
                        }
                    }
                    // stea cazatoare la ~40 de secunde
                    val cyc = t % 40f
                    if (cyc < 1f) {
                        val n = (t / 40f).toInt()
                        val x0 = w * (0.35f + 0.55f * (((n * 7919) % 1000) / 1000f))
                        val y0 = h * (0.04f + 0.2f * (((n * 104729) % 1000) / 1000f))
                        val stx = x0 - cyc * w * 0.3f
                        val sty = y0 + cyc * h * 0.08f
                        sp.strokeWidth = 3f
                        sp.color = Color.argb(((1f - cyc) * 230).toInt(), 255, 255, 255)
                        c.drawLine(stx, sty, stx + w * 0.07f, sty - h * 0.018f, sp)
                    }
                    val ph = moonPhase()
                    val mb = moonBitmap(ph)
                    val mr = w * 0.075f
                    val testScenario = TestMode.read(this@WeatherWallpaperService)
                    val moonTime = if (testScenario != null) {
                        val local = Calendar.getInstance()
                        val localMinute = local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE)
                        System.currentTimeMillis() + (testScenario.minute - localMinute) * 60000L
                    } else System.currentTimeMillis()
                    val minuteKey = moonTime / 60000L
                    if (minuteKey != moonPositionMinute) {
                        cachedMoonPosition = MoonSkyPosition.calculate(moonTime, moonLatitude, moonLongitude)
                        moonPositionMinute = minuteKey
                    }
                    val moonPosition = cachedMoonPosition
                    val moonAltitude = moonPosition.altitudeDeg
                    // Scenariul de test „noapte cu lună” rămâne vizibil pentru previzualizare, indiferent de faza reală din ziua respectivă.
                    val forceMoonForTest = testScenario != null && !day && code == 0
                    val shownMoonAltitude = if (forceMoonForTest) max(moonAltitude, 8.0) else moonAltitude
                    val moonX = w * (0.5f + 0.46f * sin(Math.toRadians(moonPosition.hourAngleDeg)).toFloat())
                    val moonY = h * (0.56f - (shownMoonAltitude.coerceIn(0.0, 90.0) / 90.0).toFloat() * 0.40f)
                    val moonHorizonAlpha = ((shownMoonAltitude + 3.0) / 10.0).coerceIn(0.0, 1.0).toFloat()
                    val illum = (1f - cos((2.0 * PI * ph.toDouble()).toFloat())) / 2f
                    if (moonAltitude > -3.0 || forceMoonForTest) {
                        useShader(
                            RadialGradient(
                                moonX, moonY, mr * 4.5f,
                                intArrayOf(Color.argb((54 * illum * moonHorizonAlpha).toInt(), 220, 230, 255), 0),
                                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                            )
                        )
                        c.drawCircle(moonX, moonY, mr * 4.5f, paint)
                        useShader(null)
                        c.save()
                        c.rotate(-18f, moonX, moonY)
                        paint.alpha = (255 * moonHorizonAlpha).toInt()
                        rect.set(moonX - mr, moonY - mr, moonX + mr, moonY + mr)
                        c.drawBitmap(mb, null, rect, paint)
                        paint.alpha = 255
                        c.restore()
                    }
                }
            }

            // nori cu volum si adancime, miscati dupa vant
            val cloudCol = when {
                k == 6 -> Color.argb(210, 40, 46, 62)
                k >= 4 -> Color.argb(205, 90, 104, 120)
                !day -> Color.argb(175, 150, 160, 185)
                else -> Color.argb(225, 255, 255, 255)
            }
            val cbmp = cloudBitmap(cloudCol)
            val tintA = if (day && twilight > 0f) (twilight * 45f).toInt() else 0
            paint.colorFilter = if (tintA > 0) PorterDuffColorFilter(Color.argb(tintA, 255, 140, 80), PorterDuff.Mode.SRC_ATOP) else null
            for (cl in clouds) {
                val tt = ((cl.s - 0.7f) / 0.9f).coerceIn(0f, 1f)
                val bw = 170f * cl.s * us
                val bh = bw * 240f / 440f * (0.88f + cl.v * 0.36f)
                cl.x += cl.v * (0.45f + wspd * 2.4f) * hx * dtf
                if (cl.x > w) cl.x = -bw
                if (cl.x < -bw) cl.x = w
                paint.alpha = (Color.alpha(cloudCol) * (0.40f + 0.60f * tt)).toInt().coerceIn(0, 255)
                val ox = tiltX * w * (0.01f + 0.035f * tt)
                val oy = tiltY * h * (0.004f + 0.012f * tt)
                rect.set(cl.x + ox, cl.y + oy, cl.x + bw + ox, cl.y + bh + oy)
                if (cl.s > 1.15f) {
                    c.save()
                    c.scale(-1f, 1f, rect.centerX(), rect.centerY())
                    c.drawBitmap(cbmp, null, rect, paint)
                    c.restore()
                } else c.drawBitmap(cbmp, null, rect, paint)
            }
            paint.colorFilter = null
            paint.alpha = 255

            // dealuri pe 3 straturi (pune showHills = false sus ca sa le scoti)
            if (showHillsV) hills3.draw(c, paint, w, h, k, bot, day, tiltX, tiltY, t, wspd, hx, tC, scene)
            if (false) {
                val hb = hillBitmap(k, bot)
                val hox = tiltX * w * 0.03f
                val hoy = tiltY * h * 0.01f
                rect.set(-w * 0.04f + hox, h - hb.height - h * 0.012f + hoy, w * 1.04f + hox, h + h * 0.012f + hoy)
                c.drawBitmap(hb, null, rect, paint)
            }

            // pojghita subtire de gheata pe prim-plan la ger puternic
            if (tC <= -8f) {
                val ice = Path().apply {
                    moveTo(w * 0.53f, h * 0.93f)
                    cubicTo(w * 0.56f, h * 0.89f, w * 0.61f, h * 0.87f, w * 0.66f, h * 0.89f)
                    cubicTo(w * 0.72f, h * 0.86f, w * 0.79f, h * 0.89f, w * 0.82f, h * 0.92f)
                    cubicTo(w * 0.79f, h * 0.96f, w * 0.60f, h * 0.97f, w * 0.53f, h * 0.93f)
                    close()
                }
                paint.shader = LinearGradient(w * 0.61f, h * 0.87f, w * 0.70f, h * 0.97f,
                    intArrayOf(0xA8F4FFFF.toInt(), 0xB5A4E4F2.toInt(), 0x88D4F4FC.toInt()), null, Shader.TileMode.CLAMP)
                c.drawPath(ice, paint)
                paint.shader = null
                sp.color = Color.argb(155, 224, 250, 255)
                sp.strokeWidth = max(1.5f, w * 0.003f)
                val shine = Path().apply {
                    moveTo(w * 0.58f, h * 0.916f)
                    quadTo(w * 0.66f, h * 0.891f, w * 0.73f, h * 0.904f)
                }
                c.drawPath(shine, sp)
                sp.color = Color.argb(95, 255, 255, 255)
                sp.strokeWidth = max(1f, w * 0.0016f)
                c.drawLine(w * 0.64f, h * 0.93f, w * 0.69f, h * 0.923f, sp)
            }

            // ceata: val usor si fasii subtiri, scurte
            if (k == 3) {
                c.drawColor(Color.argb(22, 235, 240, 245))
                val wb = wispBitmap()
                for (p in wisps) {
                    val bw = w * (0.30f + 0.40f * p.s)
                    val bh = bw * 60f / 400f
                    p.x += p.v * (0.45f + wspd * 1.6f) * hx * us * dtf
                    if (p.x > w) p.x = -bw
                    if (p.x < -bw) p.x = w
                    paint.alpha = (22 + 48 * p.s).toInt()
                    rect.set(p.x, p.y, p.x + bw, p.y + bh)
                    c.drawBitmap(wb, null, rect, paint)
                }
                paint.alpha = 255
            }

            val slant = (wspd * 10f + (if (heavySnow) 4f else 0f)) * hx

            // ploaie / burnita / furtuna
            if (k == 4 || k == 6) {
                val light = code in 51..57
                val lvl = (intensity() / 8f).coerceIn(0f, 1f)
                val fl = when {
                    light -> 0.58f
                    lvl < 0.25f -> 0.78f
                    lvl < 0.65f -> 0.98f
                    else -> 1.18f
                }
                val lt = if (light) 0.86f else 0.82f + 0.18f * lvl
                val spd = 0.82f + 0.46f * lvl
                val szm = 0.82f + 0.42f * lvl
                val splashP = (0.12f + 0.72f * lvl).coerceIn(0.12f, 0.84f)
                for (p in parts) {
                    val d = p.s
                    val land = h * (0.88f + 0.11f * d)
                    p.y += p.v * sc * fl * spd * (0.6f + 0.8f * d) * dtf
                    p.x += slant * sc * (0.6f + 0.8f * d) * dtf
                    if (p.y > land) {
                        if (d > 0.25f && rnd.nextFloat() < splashP) {
                            spX[spI] = p.x
                            spY[spI] = land
                            spA[spI] = 0f
                            spS[spI] = 0.6f + 0.8f * d
                            spI = (spI + 1) % spn
                        }
                        p.y = -40f
                        p.x = rnd.nextFloat() * w
                    }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    val streak = if (light) (7f + 8f * d + 4f * lvl) * us * lt * szm else (4f + (7f + 20f * d) * lvl) * us * lt * szm
                    val ang = -atan(slant / (p.v * fl * spd)) * 57.2958f
                    sp.color = Color.argb(((if (light) 72f else 35f) + (if (light) 125f else 110f) * d).toInt().coerceIn(0, 185), 196, 224, 249)
                    sp.strokeWidth = max(0.9f, (if (light) 0.95f + 1.25f * d else 0.7f + 1.1f * d) * us)
                    c.save()
                    c.rotate(ang, p.x, p.y)
                    c.drawLine(p.x, p.y - streak, p.x, p.y, sp)
                    if (code == 65 || code >= 95) {
                        sp.color = if (code >= 95) Color.argb(215, 207, 231, 250) else Color.argb(225, 186, 218, 246)
                        c.drawCircle(p.x, p.y, max(1.1f, sp.strokeWidth * 0.72f), sp)
                    }
                    c.restore()
                }
                paint.alpha = 255
                // stropi la impact
                sp.strokeWidth = max(1f, 1.6f * us)
                for (i in 0 until spn) {
                    val a = spA[i]
                    if (a >= 1f) continue
                    val s = spS[i]
                    val ring = (5f + 16f * a) * us * s
                    val fade = 1f - a
                    sp.color = Color.argb((140 * fade).toInt(), 225, 238, 255)
                    rect.set(spX[i] - ring, spY[i] - ring * 0.28f, spX[i] + ring, spY[i] + ring * 0.28f)
                    c.drawOval(rect, sp)
                    paint.color = Color.argb((210 * fade).toInt(), 235, 245, 255)
                    for (j in 0 until 4) {
                        val dir = (j - 1.5f) * 0.7f
                        val px = spX[i] + dir * a * 22f * us * s
                        val py = spY[i] - sin((PI * a).toFloat()) * (10f + 4f * j) * us * s
                        c.drawCircle(px, py, max(1f, 1.7f * us * s * (1f - 0.5f * a)), paint)
                    }
                    spA[i] = a + 0.045f * dtf
                }
                paint.alpha = 255
                // picaturi pe sticla ecranului
                val gb = glassBitmap()
                for (g in glass) {
                    g.y += g.v * (0.5f + lvl) * dtf
                    if (g.y > h) { g.y = -30f; g.x = rnd.nextFloat() * w }
                    val gr = (3f + 7f * g.s) * us
                    paint.alpha = 125
                    rect.set(g.x - gr, g.y - gr * 1.3f, g.x + gr, g.y + gr * 1.3f)
                    c.drawBitmap(gb, null, rect, paint)
                }
                paint.alpha = 255
                useShader(
                    LinearGradient(
                        0f, h * 0.82f, 0f, h,
                        Color.argb(0, 200, 215, 235), Color.argb((25f + 55f * lvl).toInt(), 200, 215, 235), Shader.TileMode.CLAMP
                    )
                )
                c.drawRect(0f, h * 0.82f, w, h, paint)
                useShader(null)
            }

            // grindina
            if (hail.isNotEmpty()) {
                for (p in hail) {
                    p.y += p.v * sc * dtf
                    p.x += slant * sc * dtf
                    if (p.y > h) { p.y = -20f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    val r = max(2f, p.s * sc * 1.8f)
                    paint.color = Color.argb(235, 225, 236, 248)
                    c.drawCircle(p.x, p.y, r, paint)
                    paint.color = Color.argb(200, 255, 255, 255)
                    c.drawCircle(p.x - r * 0.3f, p.y - r * 0.3f, r * 0.4f, paint)
                }
            }

            // ninsoare
            if (k == 5) {
                if (heavySnow) c.drawColor(Color.argb(55, 255, 255, 255))
                val foregroundFlakes = flakeBitmap()
                for (p in parts) {
                    val d = p.s
                    p.y += p.v * sc * 1.05f * dtf
                    p.x += (sin(t * (0.45f + d * 0.35f) + p.v * 9f) * 0.7f * (0.5f + d) + slant * sc * 0.24f * (0.5f + d)) * dtf
                    if (p.y > h) { p.y = -30f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    paint.color = Color.WHITE
                    if (d > 0.82f) {
                        val depth = ((d - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        val size = (10f + 9f * depth) * sc * (if (heavySnow) 1.12f else 1f)
                        paint.alpha = (18 + 28 * depth).toInt()
                        rect.set(p.x - size / 2f, p.y - size / 2f, p.x + size / 2f, p.y + size / 2f)
                        c.drawBitmap(foregroundFlakes, null, rect, paint)
                        paint.alpha = (48 + 42 * depth).toInt()
                        c.drawCircle(p.x, p.y, 1.2f + depth * 1.6f, paint)
                    } else {
                        val size = (1.2f + 3.6f * d) * sc * (if (heavySnow) 1.12f else 1f)
                        paint.alpha = (48 + 72 * d).toInt()
                        c.drawCircle(p.x, p.y, max(0.7f, size / 2f), paint)
                    }
                }
                paint.alpha = 255
                useShader(
                    LinearGradient(
                        0f, h * 0.9f, 0f, h,
                        Color.argb(0, 255, 255, 255), Color.argb(70, 255, 255, 255), Shader.TileMode.CLAMP
                    )
                )
                c.drawRect(0f, h * 0.9f, w, h, paint)
                useShader(null)
            }

            // fulgi rari, fini, vizibili și în ger senin
            if (tC <= -8f && frostFlakes.isNotEmpty()) {
                val flake = flakeBitmap()
                for (p in frostFlakes) {
                    p.y += p.v * sc * 0.75f * dtf
                    p.x += (sin(t * 0.55f + p.v * 5f) * 0.6f + slant * sc * 0.12f) * dtf
                    if (p.y > h) { p.y = -12f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    val size = (2.2f + 5.0f * p.s) * sc
                    paint.alpha = (72 + 120 * p.s).toInt()
                    if (p.s > 0.76f) {
                        rect.set(p.x - size, p.y - size, p.x + size, p.y + size)
                        c.drawBitmap(flake, null, rect, paint)
                    } else c.drawCircle(p.x, p.y, size * 0.38f, paint)
                }
                paint.alpha = 255
            }

            // fulgere cu ramura
            if (k == 6 && fxLightning) {
                val nowMs = SystemClock.uptimeMillis()
                if (nextBolt == 0L) {
                    nextBolt = nowMs + 1800L + rnd.nextInt(6500)
                } else if (nowMs > nextBolt) {
                    nextBolt = nowMs + 5000L + rnd.nextInt(13000)
                    flash = 1f
                    boltUntil = nowMs + 230
                    makeBolt()
                }
                if (nowMs < boltUntil && bolt.size > 1) {
                    val path = Path()
                    path.moveTo(bolt[0].x, bolt[0].y)
                    for (i in 1 until bolt.size) path.lineTo(bolt[i].x, bolt[i].y)
                    sp.color = Color.argb(90, 190, 210, 255)
                    sp.strokeWidth = w / 50f
                    c.drawPath(path, sp)
                    sp.color = Color.WHITE
                    sp.strokeWidth = w / 140f
                    c.drawPath(path, sp)
                    if (bolt2.size > 1) {
                        val br = Path()
                        br.moveTo(bolt2[0].x, bolt2[0].y)
                        for (i in 1 until bolt2.size) br.lineTo(bolt2[i].x, bolt2[i].y)
                        sp.strokeWidth = w / 260f
                        c.drawPath(br, sp)
                    }
                }
                if (flash > 0.02f) {
                    c.drawColor(Color.argb((flash * 92).toInt(), 226, 236, 255))
                    if (fxStormGlow) stormGlow.draw(c, w, h, flash, cloud)
                    flash *= 0.82f.pow(dtf)
                }
            }

            if (fxAurora) aurora.draw(c, w, h, t, dtf, !day && k == 0 && cloud < 20 && tC < 14f)
            if (fxShadows) shadows.draw(c, w, h, t, dtf, k <= 2 && cloud >= 15, hx, wspd)
            if (fxButterflies) butterflies.draw(c, w, h, t, dtf, day && k <= 2 && tC in 17f..35f, tiltX)
            if (fxGrass) grass.draw(c, w, h, t, dtf, k != 5, hx, wspd, day, tiltX)
            if (fxPlanets) planets.draw(c, w, h, t, !day && k == 0 && cloud < 15)
            if (fxLake && scene != 1) lake.draw(c, w, h, t, day, cloud, k == 4 || k == 6, wspd, scene, (sx / w).coerceIn(0f, 1f))
            if (fxWeatherTransition) weatherTransition.draw(c, w, h, k, dtf, day)
            // vigneta fina (creata o singura data)
            if (fxBirds) birds.draw(c, w, h, t, day && k <= 2, hx)
            fogGlass.draw(c, w, h, dtf, k == 3 || (k == 4 && tC < 9f))
                        air.draw(c, w, h)
                        ground.draw(c, w, h, t, dtf, code, tC)
            meteors.draw(c, w, h, t, dtf, !day, k <= 1)
            fx.draw(c, w, h, t, dtf, k, day, tC, hx, wspd, now, sunrise, sunset, tiltX, tiltY)
            val localCalendar = Calendar.getInstance()
            seasonalFx.draw(c, w, h, t, dtf, localCalendar.get(Calendar.MONTH), now, tC, code, day, hx,
                reduceMotion, qMul, fxLeaves, fxFrost, fxBlossoms, fxPollen, fxFireflies, fxDew)
            // La caniculă, o pâclă tremurată înlocuiește aspectul de benzi rigide de la baza scenei.
            if (day && tC >= 35f) {
                useShader(LinearGradient(0f, h * 0.80f, 0f, h,
                    intArrayOf(0, Color.argb(14, 255, 215, 145), Color.argb(54, 255, 188, 104)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP))
                c.drawRect(0f, h * 0.80f, w, h, paint)
                useShader(null)
                sp.color = Color.argb(38, 255, 244, 205)
                sp.strokeWidth = max(1f, h * 0.0014f)
                for (i in 0..3) {
                    val y = h * (0.84f + i * 0.035f)
                    val x = w * ((i * 0.23f + (t * 0.012f) % 0.22f) % 1f)
                    val haze = Path().apply {
                        moveTo(x - w * 0.12f, y)
                        cubicTo(x - w * 0.05f, y - h * 0.012f, x + w * 0.04f, y + h * 0.008f, x + w * 0.13f, y - h * 0.006f)
                    }
                    c.drawPath(haze, sp)
                }
            }
            val vk = w.toLong() * 10000L + h.toLong()
            if (vigShader == null || vigKey != vk) {
                vigShader = RadialGradient(
                    w / 2f, h * 0.45f, h * 0.75f,
                    intArrayOf(0, 0, Color.argb(70, 0, 0, 0)),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                vigKey = vk
            }
            useShader(vigShader)
            c.drawRect(0f, 0f, w, h, paint)
            useShader(null)
        }
    }
}













