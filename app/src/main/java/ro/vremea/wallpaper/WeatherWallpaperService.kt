package ro.vremea.wallpaper

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
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

private class P(var x: Float, var y: Float, var v: Float, var s: Float)

class WeatherWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WeatherEngine()

    inner class WeatherEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
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
        private var flash = 0f
        private var boltFrames = 0
        private val bolt = ArrayList<PointF>()
        private var nextBolt = 0L

        private val tick = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
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
            code in 51..57 -> 50
            code == 61 || code == 80 -> 90
            code == 63 || code == 81 -> 140
            code == 65 || code == 66 || code == 67 || code == 82 -> 220
            code >= 95 -> 230
            else -> 130
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
                parts.add(
                    if (k == 5) P(
                        rnd.nextFloat() * w, rnd.nextFloat() * h,
                        (2f + rnd.nextFloat() * 3f) * (if (heavy) 1.6f else 1f), 2f + rnd.nextFloat() * 4f
                    )
                    else P(rnd.nextFloat() * w, rnd.nextFloat() * h, 22f + rnd.nextFloat() * 14f, rnd.nextFloat())
                )
            }
            hail = ArrayList()
            if (k == 6 && (code == 96 || code == 99)) {
                repeat(60) { hail.add(P(rnd.nextFloat() * w, rnd.nextFloat() * h, 30f + rnd.nextFloat() * 12f, 3f + rnd.nextFloat() * 3f)) }
            }
            val nc = when (k) {
                0 -> 0
                1 -> max(1, (cloud / 100f * 8f).roundToInt())
                2 -> max(5, (cloud / 100f * 8f).roundToInt())
                6 -> 8
                else -> 6
            }
            clouds = ArrayList()
            repeat(nc) {
                clouds.add(P(rnd.nextFloat() * w, h * (0.05f + rnd.nextFloat() * 0.35f), 0.3f + rnd.nextFloat() * 0.8f, 1f + rnd.nextFloat() * 1.5f))
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
            // orasul ales in aplicatie
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
                c = surfaceHolder.lockCanvas() ?: return
                render(c)
            } finally {
                if (c != null) try { surfaceHolder.unlockCanvasAndPost(c) } catch (e: Exception) {}
            }
        }

        private fun cloud(c: Canvas, x: Float, y: Float, s: Float, col: Int) {
            paint.color = col
            c.drawCircle(x, y, 36 * s, paint)
            c.drawCircle(x + 50 * s, y - 22 * s, 48 * s, paint)
            c.drawCircle(x + 100 * s, y - 4 * s, 38 * s, paint)
            c.drawRoundRect(RectF(x - 30 * s, y, x + 130 * s, y + 34 * s), 17 * s, 17 * s, paint)
        }

        private fun makeBolt() {
            bolt.clear()
            var x = w * (0.2f + 0.6f * rnd.nextFloat())
            var y = 0f
            bolt.add(PointF(x, y))
            while (y < h * 0.55f) {
                y += h * (0.03f + 0.04f * rnd.nextFloat())
                x += (rnd.nextFloat() - 0.5f) * w * 0.12f
                bolt.add(PointF(x, y))
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

            paint.shader = LinearGradient(0f, 0f, 0f, h, pal[k * 2], pal[k * 2 + 1], Shader.TileMode.CLAMP)
            c.drawPaint(paint)
            paint.shader = null

            // nuanta dupa temperatura
            if (tC < 15f) {
                c.drawColor(Color.argb(((15f - max(tC, -10f)) / 25f * 80).toInt(), 120, 170, 255))
            } else {
                c.drawColor(Color.argb(((min(tC, 38f) - 15f) / 23f * 90).toInt(), 255, 130, 50))
            }

            // pozitia soarelui dupa ora reala + stralucire la rasarit/apus
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
                    val r = w * 0.55f * (1f + 0.03f * sin(t))
                    paint.shader = RadialGradient(
                        sx, sy, r,
                        intArrayOf(0xCCFFF3B0.toInt(), 0x55FFD54F.toInt(), 0),
                        floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawCircle(sx, sy, r, paint)
                    paint.shader = null
                    paint.color = 0xFFFFE680.toInt()
                    c.drawCircle(sx, sy, w * 0.09f, paint)
                } else {
                    for (i in 0 until 70) {
                        val x = ((i * 7919) % 1000) / 1000f * w
                        val y = ((i * 104729) % 1000) / 1000f * h * 0.6f
                        paint.color = Color.argb(120 + (100 * sin(t * 2f + i)).toInt(), 255, 255, 255)
                        c.drawCircle(x, y, 1.5f + (i % 3), paint)
                    }
                    paint.color = 0xFFF5F0D0.toInt()
                    c.drawCircle(sx, sy, w * 0.07f, paint)
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

            // nori
            val cloudCol = when {
                k == 6 -> Color.argb(190, 40, 46, 62)
                k >= 4 -> Color.argb(170, 90, 104, 120)
                !day -> Color.argb(120, 150, 160, 185)
                else -> Color.argb(190, 255, 255, 255)
            }
            for (cl in clouds) {
                cl.x += cl.v
                if (cl.x > w + 200f * cl.s) cl.x = -250f * cl.s
                cloud(c, cl.x, cl.y, cl.s * (w / 700f), cloudCol)
            }

            // ceata
            if (k == 3) {
                paint.color = Color.argb(60, 255, 255, 255)
                for (i in 0..4) {
                    val y = h * (0.3f + 0.12f * i)
                    val off = (t * 20f * (i + 1)) % (w * 1.2f)
                    c.drawRoundRect(RectF(off - w * 1.2f, y, off, y + h * 0.06f), 60f, 60f, paint)
                    c.drawRoundRect(RectF(off, y, off + w * 1.2f, y + h * 0.06f), 60f, 60f, paint)
                }
            }

            val slant = min(windKmh, 40f) / 40f * 10f + (if (heavySnow) 4f else 0f)

            // ploaie / furtuna
            if (k == 4 || k == 6) {
                sp.color = Color.argb(150, 200, 220, 255)
                sp.strokeWidth = if (code in 51..57) max(1.5f, w / 600f) else max(2f, w / 400f)
                for (p in parts) {
                    p.y += p.v * sc
                    p.x += slant * sc
                    if (p.y > h) { p.y = -40f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    val len = (24f + p.s * 20f) * sc
                    c.drawLine(p.x, p.y, p.x - slant * 2f * sc, p.y - len, sp)
                }
            }

            // grindina
            if (hail.isNotEmpty()) {
                paint.color = Color.argb(235, 235, 245, 255)
                for (p in hail) {
                    p.y += p.v * sc
                    p.x += slant * sc
                    if (p.y > h) { p.y = -20f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    c.drawCircle(p.x, p.y, max(2f, p.s * sc * 1.8f), paint)
                }
            }

            // ninsoare
            if (k == 5) {
                if (heavySnow) c.drawColor(Color.argb(55, 255, 255, 255))
                paint.color = Color.argb(225, 255, 255, 255)
                for (p in parts) {
                    p.y += p.v * sc
                    p.x += sin(t * 1.5f + p.y * 0.01f) * 1.2f + slant * (if (heavySnow) 0.6f else 0.2f)
                    if (p.y > h) { p.y = -20f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    c.drawCircle(p.x, p.y, max(1.5f, p.s * sc * 1.6f), paint)
                }
            }

            // fulgere
            if (k == 6) {
                val nowMs = SystemClock.uptimeMillis()
                if (nowMs > nextBolt) {
                    nextBolt = nowMs + 2500 + rnd.nextInt(6000)
                    flash = 1f
                    boltFrames = 7
                    makeBolt()
                }
                if (boltFrames > 0 && bolt.size > 1) {
                    val path = Path()
                    path.moveTo(bolt[0].x, bolt[0].y)
                    for (i in 1 until bolt.size) path.lineTo(bolt[i].x, bolt[i].y)
                    sp.color = Color.argb(90, 190, 210, 255)
                    sp.strokeWidth = w / 50f
                    c.drawPath(path, sp)
                    sp.color = Color.WHITE
                    sp.strokeWidth = w / 140f
                    c.drawPath(path, sp)
                    boltFrames--
                }
                if (flash > 0.02f) {
                    c.drawColor(Color.argb((flash * 140).toInt(), 255, 255, 255))
                    flash *= 0.82f
                }
            }

            // temperatura
            paint.color = Color.argb(190, 255, 255, 255)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = w * 0.10f
            paint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            c.drawText("${temp.roundToInt()}°" + (if (f) "F" else ""), w / 2f, h * 0.86f, paint)
        }
    }
}
