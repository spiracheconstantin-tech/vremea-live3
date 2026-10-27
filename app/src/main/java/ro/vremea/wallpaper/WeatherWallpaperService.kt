package ro.vremea.wallpaper

import android.Manifest
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
private val RB = listOf("#FF4D4D", "#FF9F43", "#FFE74C", "#4CD964", "#4DA3FF", "#5B5BFF", "#A25BFF")
    .map { Color.parseColor(it) }

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
        private var visible = false
        private var w = 1f
        private var h = 1f
        @Volatile private var code = 0
        @Volatile private var temp = 15.0
        @Volatile private var wind = 5.0
        @Volatile private var day = true
        @Volatile private var cloud = 0
        @Volatile private var precip = 0.0
        @Volatile private var fahr = false
        @Volatile private var utcOff = 0
        @Volatile private var sunrise = 360
        @Volatile private var sunset = 1080
        @Volatile private var recentRain = 0.0
        private var parts = ArrayList<P>()
        private var hail = ArrayList<P>()
        private var clouds = ArrayList<P>()
        private var wisps = ArrayList<P>()
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
        private var dropBmp2: Bitmap? = null
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

        private val tick = object : Runnable {
            override fun run() {
                val s = SystemClock.uptimeMillis()
                draw()
                if (visible) {
                    val used = SystemClock.uptimeMillis() - s
                    handler.postDelayed(this, max(1L, 16L - used))
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

        // Intensitatea ploii in mm: cea reala daca exista, altfel estimata din cod
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

        private fun init() {
            val k = cat()
            val heavy = code == 75 || code == 86
            val n = when (k) {
                4, 6 -> rainN()
                5 -> when (code) { 71, 85 -> 70; 73 -> 130; 75, 86 -> 260; else -> 90 }
                else -> 0
            }
            parts = ArrayList()
            repeat(n) {
                val d = rnd.nextFloat()
                parts.add(
                    if (k == 5) P(rnd.nextFloat() * w, rnd.nextFloat() * h, (1.5f + 3.5f * d) * (if (heavy) 1.5f else 1f), d)
                    else P(rnd.nextFloat() * w, rnd.nextFloat() * h, 22f + rnd.nextFloat() * 14f, d)
                )
            }
            hail = ArrayList()
            if (k == 6 && (code == 96 || code == 99)) {
                repeat(60) { hail.add(P(rnd.nextFloat() * w, rnd.nextFloat() * h, 30f + rnd.nextFloat() * 12f, 3f + rnd.nextFloat() * 3f)) }
            }
            val nc = when (k) {
                0 -> 0
                1 -> max(1, (cloud / 100f * 4f).roundToInt())
                2 -> max(3, (cloud / 100f * 5f).roundToInt())
                6 -> 5
                3 -> 2
                else -> 4
            }
            clouds = ArrayList()
            repeat(nc) {
                clouds.add(P(rnd.nextFloat() * w, h * (0.05f + rnd.nextFloat() * 0.35f), 0.3f + rnd.nextFloat() * 0.8f, 0.8f + rnd.nextFloat() * 0.8f))
            }
            wisps = ArrayList()
            if (k == 3) {
                repeat(7) { wisps.add(P(rnd.nextFloat() * w, h * (0.22f + 0.65f * rnd.nextFloat()), 0.15f + 0.4f * rnd.nextFloat(), rnd.nextFloat())) }
            }
        }

        private fun hm(s: String): Int = s.substring(11, 13).toInt() * 60 + s.substring(14, 16).toInt()

        private fun load(lat: Double, lon: Double) {
            Thread {
                try {
                    val f = getSharedPreferences("app", MODE_PRIVATE).getString("units", "c") == "f"
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day,wind_speed_10m,cloud_cover,precipitation" +
                        "&hourly=precipitation&daily=sunrise,sunset&past_days=1&forecast_days=1&timezone=auto" +
                        (if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")
                    val conn = URL(url).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                    val j = JSONObject(conn.getInputStream().bufferedReader().readText())
                    val cur = j.getJSONObject("current")

                    var rr = 0.0
                    try {
                        val hr = j.getJSONObject("hourly")
                        val times = hr.getJSONArray("time")
                        val pr = hr.getJSONArray("precipitation")
                        val key = cur.getString("time").substring(0, 13)
                        var idx = -1
                        for (i in 0 until times.length()) if (times.getString(i).substring(0, 13) == key) { idx = i; break }
                        if (idx > 0) for (i in max(0, idx - 3) until idx) rr += pr.optDouble(i, 0.0)
                    } catch (e: Exception) { }

                    try {
                        val d = j.getJSONObject("daily")
                        val sr = d.getJSONArray("sunrise")
                        val ss = d.getJSONArray("sunset")
                        sunrise = hm(sr.getString(sr.length() - 1))
                        sunset = hm(ss.getString(ss.length() - 1))
                    } catch (e: Exception) { }

                    utcOff = j.optInt("utc_offset_seconds", 0)
                    recentRain = rr
                    fahr = f
                    cloud = cur.optInt("cloud_cover", 0)
                    precip = cur.optDouble("precipitation", 0.0)
                    code = cur.getInt("weather_code")
                    temp = cur.getDouble("temperature_2m")
                    wind = cur.getDouble("wind_speed_10m")
                    day = cur.getInt("is_day") == 1
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

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width.toFloat(); h = height.toFloat()
            init()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(tick)
            handler.removeCallbacks(refresh)
            lastMs = 0L
            if (v) { handler.post(refresh); handler.post(tick) }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            handler.removeCallbacks(refresh)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
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

        private fun cloudBitmap(col: Int): Bitmap {
            val key = col and 0x00FFFFFF
            val old = cloudBmp
            if (old != null && cloudKey == key) return old
            val bmp = Bitmap.createBitmap(440, 240, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val puffs = arrayOf(
                floatArrayOf(110f, 130f, 46f), floatArrayOf(165f, 100f, 58f),
                floatArrayOf(230f, 85f, 66f), floatArrayOf(295f, 105f, 58f),
                floatArrayOf(345f, 135f, 44f), floatArrayOf(215f, 130f, 58f),
                floatArrayOf(145f, 140f, 46f), floatArrayOf(285f, 142f, 48f)
            )
            fun layer(color: Int, dx: Float, dy: Float, k: Float, blur: Float, alpha: Int, base: Boolean) {
                p.color = color
                p.alpha = alpha
                p.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                for (u in puffs) cv.drawCircle(u[0] + dx, u[1] + dy, u[2] * k, p)
                if (base) cv.drawRoundRect(RectF(80f + dx, 140f + dy, 380f + dx, 185f + dy), 40f, 40f, p)
            }
            layer(shade(col, 0.78f), 0f, 12f, 1f, 16f, 255, true)
            layer(col, 0f, 0f, 1f, 9f, 255, true)
            layer(mixWhite(col, 0.55f), -10f, -14f, 0.7f, 12f, 150, false)
            p.maskFilter = null
            cloudBmp = bmp
            cloudKey = key
            return bmp
        }

        private fun flakeBitmap(): Bitmap {
            val old = flakeBmp
            if (old != null) return old
            val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                24f, 24f, 24f,
                intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(190, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
            )
            Canvas(bmp).drawCircle(24f, 24f, 24f, p)
            flakeBmp = bmp
            return bmp
        }

        // Picatura
