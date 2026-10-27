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
        private var dropBmp: Bitmap? = null
        private var dropBmp2: Bitmap? = null
        private val spX = FloatArray(40)
        private val spY = FloatArray(40)
        private val spA = FloatArray(40) { 1f }
        private val spS = FloatArray(40)
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

        private fun rainN(): Int = when {
            code in 51..57 -> 70
            code == 61 || code == 80 -> 110
            code == 63 || code == 81 -> 170
            code == 65 || code == 66 || code == 67 || code == 82 -> 260
            code >= 95 -> 270
            else -> 150
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
                        "&current=temperature_2m,weather_code,is_day,wind_speed_10m,cloud_cover" +
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

        // Picatura mica, rotunjita, cu un punct luminos (desenata o singura data)
        private fun dropBitmap(): Bitmap {
            val old = dropBmp
            if (old != null) return old
            val bmp = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 2f, 0f, 28f,
                Color.argb(0, 225, 238, 255), Color.argb(235, 225, 238, 255), Shader.TileMode.CLAMP
            )
            p.maskFilter = BlurMaskFilter(1.4f, BlurMaskFilter.Blur.NORMAL)
            cv.drawOval(RectF(5f, 2f, 15f, 28f), p)
            p.shader = null
            p.maskFilter = null
            p.color = Color.argb(210, 255, 255, 255)
            cv.drawCircle(8.5f, 21f, 2.2f, p)
            dropBmp = bmp
            return bmp
        }
                private fun dropBitmap2(): Bitmap {
            val old = dropBmp2
            if (old != null) return old
            val bmp = Bitmap.createBitmap(24, 40, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val path = Path()
            path.moveTo(12f, 2f)
            path.cubicTo(13f, 11f, 20f, 19f, 20f, 29f)
            path.arcTo(RectF(4f, 21f, 20f, 37f), 0f, 180f)
            path.cubicTo(4f, 19f, 11f, 11f, 12f, 2f)
            path.close()
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(
                4f, 0f, 20f, 0f,
                intArrayOf(Color.argb(150, 205, 225, 250), Color.argb(70, 205, 225, 250), Color.argb(170, 225, 238, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            cv.drawPath(path, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f
            p.color = Color.argb(200, 240, 247, 255)
            cv.drawPath(path, p)
            p.style = Paint.Style.FILL
            p.color = Color.argb(230, 255, 255, 255)
            cv.drawOval(RectF(7f, 25f, 11f, 31f), p)
            dropBmp2 = bmp
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
            val k = cat()
            val t = SystemClock.uptimeMillis() / 1000f
            val pal = if (day) DAY else NIGHT
            val sc = h / 1920f
            val f = fahr
            val tC = (if (f) (temp - 32.0) * 5.0 / 9.0 else temp).toFloat()
            val windKmh = (if (f) wind * 1.609 else wind).toFloat()
            val heavySnow = code == 75 || code == 86

            // cer cu degrade fin (creat o singura data)
            val top = pal[k * 2]
            val bot = pal[k * 2 + 1]
            val skK = (k * 2 + (if (day) 1 else 0)).toLong() * 100000000L + w.toLong() * 10000L + h.toLong()
            if (skyShader == null || skyKey != skK) {
                skyShader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(top, blend(top, bot, 0.35f), blend(top, bot, 0.8f), mixWhite(bot, 0.12f)),
                    floatArrayOf(0f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP
                )
                skyKey = skK
            }
            paint.shader = skyShader
            c.drawPaint(paint)
            paint.shader = null

            // nuanta dupa temperatura
            if (tC < 15f) {
                c.drawColor(Color.argb(((15f - max(tC, -10f)) / 25f * 80).toInt(), 120, 170, 255))
            } else {
                c.drawColor(Color.argb(((min(tC, 38f) - 15f) / 23f * 90).toInt(), 255, 130, 50))
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
            val dist = min(abs(now - sunrise), abs(now - sunset))
            if (k <= 2 && dist < 50) {
                val a = 1f - dist / 50f
                paint.shader = LinearGradient(
                    0f, h * 0.35f, 0f, h,
                    Color.argb(0, 255, 110, 60), Color.argb((a * 150).toInt(), 255, 110, 60), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, h * 0.35f, w, h, paint)
                paint.shader = null
            }

            // soare / luna + stele
            if (k <= 1) {
                if (day) {
                    val warm = (1f - dist / 90f).coerceIn(0f, 1f)
                    val r = w * 0.085f * (1f + 0.4f * warm)
                    val halo = blend(0xFFFFF3B0.toInt(), 0xFFFF9A5A.toInt(), warm)
                    val hrad = w * 0.62f * (1f + 0.03f * sin(t))
                    paint.shader = RadialGradient(
                        sx, sy, hrad,
                        intArrayOf(
                            Color.argb(170, Color.red(halo), Color.green(halo), Color.blue(halo)),
                            Color.argb(60, Color.red(halo), Color.green(halo), Color.blue(halo)), 0
                        ),
                        floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawCircle(sx, sy, hrad, paint)
                    paint.shader = null
                    // raze scurte
                    sp.strokeWidth = w * 0.012f
                    for (i in 0 until 14) {
                        val a = t * 0.04f + i * (2f * PI.toFloat() / 14f)
                        val len = if (i % 2 == 0) 2.6f else 2.0f
                        sp.color = Color.argb(if (i % 2 == 0) 42 else 26, 255, 244, 190)
                        c.drawLine(sx + cos(a) * r * 1.4f, sy + sin(a) * r * 1.4f, sx + cos(a) * r * len, sy + sin(a) * r * len, sp)
                    }
                    paint.shader = RadialGradient(
                        sx - r * .3f, sy - r * .3f, r * 1.35f,
                        intArrayOf(
                            0xFFFFFFF0.toInt(),
                            blend(0xFFFFE680.toInt(), 0xFFFF8A3D.toInt(), warm),
                            blend(0xFFFFC43D.toInt(), 0xFFE8431C.toInt(), warm)
                        ),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawCircle(sx, sy, r, paint)
                    paint.shader = null
                } else {
                    // Calea Lactee
                    c.save()
                    c.rotate(-28f, w / 2f, h * 0.3f)
                    paint.shader = LinearGradient(
                        0f, h * 0.12f, 0f, h * 0.30f,
                        intArrayOf(0, Color.argb(34, 200, 210, 255), 0),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawRect(-w * 0.3f, h * 0.12f, w * 1.3f, h * 0.30f, paint)
                    paint.shader = null
                    c.restore()
                    for (i in 0 until 140) {
                        val x = ((i * 7919) % 1000) / 1000f * w
                        val y = ((i * 104729) % 1000) / 1000f * h * 0.62f
                        val big = i % 9 == 0
                        val tw = 0.5f + 0.5f * sin(t * (1.2f + (i % 5) * 0.4f) + i)
                        paint.color = Color.argb((80 + 160 * tw).toInt(), 255, if (i % 4 == 0) 232 else 255, if (i % 7 == 0) 205 else 255)
                        c.drawCircle(x, y, (0.7f + (i % 3) * 0.6f + (if (big) 0.9f else 0f)) * (0.8f + 0.4f * tw), paint)
                        if (big) {
                            sp.color = Color.argb((70 * tw).toInt(), 255, 255, 255)
                            sp.strokeWidth = 1.5f
                            val l = 7f + 6f * tw
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
                        val hx = x0 - cyc * w * 0.3f
                        val hy = y0 + cyc * h * 0.08f
                        sp.strokeWidth = 3f
                        sp.color = Color.argb(((1f - cyc) * 230).toInt(), 255, 255, 255)
                        c.drawLine(hx, hy, hx + w * 0.07f, hy - h * 0.018f, sp)
                    }
                    val ph = moonPhase()
                    val mb = moonBitmap(ph)
                    val mr = w * 0.075f
                    val illum = (1f - cos((2.0 * PI * ph.toDouble()).toFloat())) / 2f
                    paint.shader = RadialGradient(
                        sx, sy, mr * 4.5f,
                        intArrayOf(Color.argb((70 * illum).toInt(), 220, 230, 255), 0),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawCircle(sx, sy, mr * 4.5f, paint)
                    paint.shader = null
                    c.save()
                    c.rotate(-18f, sx, sy)
                    rect.set(sx - mr, sy - mr, sx + mr, sy + mr)
                    c.drawBitmap(mb, null, rect, paint)
                    c.restore()
                }
            }

            // curcubeu dupa ploaie
            if (day && k <= 2 && recentRain > 0.3) {
                sp.strokeWidth = w * 0.02f
                val cx = w * 0.4f
                val cy = h * 0.72f
                for (i in RB.indices) {
                    val r = w * 0.62f - i * w * 0.02f
                    sp.color = (RB[i] and 0x00FFFFFF) or (0x55 shl 24)
                    c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false, sp)
                }
            }

            // nori pufosi, mici si putini
            val cloudCol = when {
                k == 6 -> Color.argb(190, 40, 46, 62)
                k >= 4 -> Color.argb(170, 90, 104, 120)
                !day -> Color.argb(120, 150, 160, 185)
                else -> Color.argb(190, 255, 255, 255)
            }
            val cbmp = cloudBitmap(cloudCol)
            val tintA = if (day && dist < 50) ((1f - dist / 50f) * 90f).toInt() else 0
            paint.colorFilter = if (tintA > 0) PorterDuffColorFilter(Color.argb(tintA, 255, 140, 80), PorterDuff.Mode.SRC_ATOP) else null
            for (cl in clouds) {
                val bw = 120f * cl.s * (w / 1080f)
                val bh = bw * 240f / 440f
                cl.x += cl.v * dtf
                if (cl.x > w) cl.x = -bw
                paint.alpha = (Color.alpha(cloudCol) * (0.55f + min(cl.s, 2f) * 0.22f)).toInt().coerceIn(0, 255)
                rect.set(cl.x, cl.y, cl.x + bw, cl.y + bh)
                c.drawBitmap(cbmp, null, rect, paint)
            }
            paint.colorFilter = null
            paint.alpha = 255

            // ceata: val usor si fasii subtiri, scurte
            if (k == 3) {
                c.drawColor(Color.argb(45, 235, 240, 245))
                val wb = wispBitmap()
                for (p in wisps) {
                    val bw = w * (0.22f + 0.28f * p.s)
                    val bh = bw * 60f / 400f
                    p.x += p.v * (w / 1080f) * dtf
                    if (p.x > w) p.x = -bw
                    paint.alpha = (35 + 45 * p.s).toInt()
                    rect.set(p.x, p.y, p.x + bw, p.y + bh)
                    c.drawBitmap(wb, null, rect, paint)
                }
                paint.alpha = 255
            }

            val slant = min(windKmh, 40f) / 40f * 10f + (if (heavySnow) 4f else 0f)
                        // ploaie / burnita / furtuna: picaturi in forma de lacrima + stropi
            if (k == 4 || k == 6) {
                val light = code in 51..57
                val fl = if (light) 0.6f else 1f
                val lt = if (light) 0.85f else 1f
                val db = dropBitmap2()
                val us = w / 1080f
                for (p in parts) {
                    val d = p.s
                    val land = h * (0.88f + 0.11f * d)
                    p.y += p.v * sc * fl * (0.6f + 0.8f * d) * dtf
                    p.x += slant * sc * (0.6f + 0.8f * d) * dtf
                    if (p.y > land) {
                        if (d > 0.25f && rnd.nextFloat() < 0.7f) {
                            spX[spI] = p.x
                            spY[spI] = land
                            spA[spI] = 0f
                            spS[spI] = 0.6f + 0.8f * d
                            spI = (spI + 1) % 40
                        }
                        p.y = -40f
                        p.x = rnd.nextFloat() * w
                    }
                    if (p.x > w) p.x -= w
                    val bh = (12f + 20f * d) * us * lt
                    val dh = bh * (1f + 0.45f * d)
                    val dw = bh * 0.55f * (1f - 0.18f * d)
                    val ang = -atan(slant / (p.v * fl)) * 57.2958f
                    paint.alpha = ((90 + 140 * d) * lt).toInt()
                    c.save()
                    c.rotate(ang, p.x, p.y)
                    rect.set(p.x - dw / 2f, p.y - dh, p.x + dw / 2f, p.y)
                    c.drawBitmap(db, null, rect, paint)
                    c.restore()
                }
                paint.alpha = 255
                // stropi la impact
                sp.strokeWidth = max(1f, 1.6f * us)
                for (i in 0 until 40) {
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
                paint.shader = LinearGradient(
                    0f, h * 0.82f, 0f, h,
                    Color.argb(0, 200, 215, 235), Color.argb(if (k == 6 || code >= 65) 70 else 40, 200, 215, 235), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, h * 0.82f, w, h, paint)
                paint.shader = null
            }
          

            // grindina
            if (hail.isNotEmpty()) {
                for (p in hail) {
                    p.y += p.v * sc * dtf
                    p.x += slant * sc * dtf
                    if (p.y > h) { p.y = -20f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
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
                val fb = flakeBitmap()
                for (p in parts) {
                    val d = p.s
                    p.y += p.v * sc * 1.6f * dtf
                    p.x += (sin(t * (0.7f + d * 0.5f) + p.v * 9f) * 0.9f * (0.5f + d) + slant * sc * 0.3f * (0.5f + d)) * dtf
                    if (p.y > h) { p.y = -30f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    val size = (6f + 20f * d) * sc * 1.8f
                    paint.alpha = (110 + 120 * d).toInt()
                    rect.set(p.x - size / 2f, p.y - size / 2f, p.x + size / 2f, p.y + size / 2f)
                    c.drawBitmap(fb, null, rect, paint)
                }
                paint.alpha = 255
                paint.shader = LinearGradient(
                    0f, h * 0.9f, 0f, h,
                    Color.argb(0, 255, 255, 255), Color.argb(70, 255, 255, 255), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, h * 0.9f, w, h, paint)
                paint.shader = null
            }

            // fulgere cu ramura
            if (k == 6) {
                val nowMs = SystemClock.uptimeMillis()
                if (nowMs > nextBolt) {
                    nextBolt = nowMs + 2500 + rnd.nextInt(6000)
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
                    c.drawColor(Color.argb((flash * 140).toInt(), 255, 255, 255))
                    flash *= 0.82f.pow(dtf)
                }
            }

            // vigneta fina (creata o singura data)
            val vk = w.toLong() * 10000L + h.toLong()
            if (vigShader == null || vigKey != vk) {
                vigShader = RadialGradient(
                    w / 2f, h * 0.45f, h * 0.75f,
                    intArrayOf(0, 0, Color.argb(70, 0, 0, 0)),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                vigKey = vk
            }
            paint.shader = vigShader
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            // temperatura
            paint.color = Color.argb(190, 255, 255, 255)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = w * 0.10f
            paint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            c.drawText("${temp.roundToInt()}°" + (if (f) "F" else ""), w / 2f, h * 0.86f, paint)
        }
    }
}
