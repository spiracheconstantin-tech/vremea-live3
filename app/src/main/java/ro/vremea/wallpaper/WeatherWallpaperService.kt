package ro.vremea.wallpaper

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.json.JSONObject
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Perechi (sus, jos) pentru categoriile: 0 senin, 1 partial noros, 2 noros, 3 ceata, 4 ploaie, 5 ninsoare, 6 furtuna
private val DAY = listOf(
    "#1F8FE0", "#BFE6FB", "#3E9BDB", "#C9E4F5", "#5C7A95", "#CFDBE5", "#8A979F",
    "#D6DCDF", "#2F4256", "#7F97AB", "#7C93AD", "#E8EFF6", "#1E2433", "#5A6683"
).map { Color.parseColor(it) }
private val NIGHT = listOf(
    "#0A1230", "#3A5088", "#101A3A", "#3F5590", "#1E2535", "#48546A", "#2A3038",
    "#56606A", "#141C28", "#3A4A5C", "#2A3550", "#6A7C98", "#0D111B", "#2E374D"
).map { Color.parseColor(it) }
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
        private var parts = ArrayList<P>()
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

        private fun init() {
            val k = cat()
            val n = when (k) { 4 -> 130; 6 -> 230; 5 -> 110; else -> 0 }
            parts = ArrayList()
            repeat(n) {
                parts.add(
                    if (k == 5) P(rnd.nextFloat() * w, rnd.nextFloat() * h, 2f + rnd.nextFloat() * 3f, 2f + rnd.nextFloat() * 4f)
                    else P(rnd.nextFloat() * w, rnd.nextFloat() * h, 22f + rnd.nextFloat() * 14f, rnd.nextFloat())
                )
            }
            val nc = when (k) { 0 -> 0; 1 -> 3; 6 -> 7; else -> 6 }
            clouds = ArrayList()
            repeat(nc) {
                clouds.add(P(rnd.nextFloat() * w, h * (0.05f + rnd.nextFloat() * 0.35f), 0.3f + rnd.nextFloat() * 0.8f, 1f + rnd.nextFloat() * 1.5f))
            }
        }

        private fun fetch() {
            Thread {
                try {
                    var lat = 44.4268
                    var lon = 26.1025
                    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
                        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                            val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
                            if (l != null) { lat = l.latitude; lon = l.longitude; break }
                        }
                    }
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day,wind_speed_10m&timezone=auto"
                    val conn = URL(url).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                    val cur = JSONObject(conn.getInputStream().bufferedReader().readText()).getJSONObject("current")
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

            // fundal dupa vreme
            paint.shader = LinearGradient(0f, 0f, 0f, h, pal[k * 2], pal[k * 2 + 1], Shader.TileMode.CLAMP)
            c.drawPaint(paint)
            paint.shader = null

            // nuanta dupa temperatura: rece = albastru, cald = portocaliu
            val tt = temp.toFloat()
            if (tt < 15f) {
                c.drawColor(Color.argb(((15f - max(tt, -10f)) / 25f * 80).toInt(), 120, 170, 255))
            } else {
                c.drawColor(Color.argb(((min(tt, 38f) - 15f) / 23f * 90).toInt(), 255, 130, 50))
            }

            // soare / luna + stele
            if (k <= 1) {
                val cx = w * 0.78f
                val cy = h * 0.14f
                if (day) {
                    val r = w * 0.55f * (1f + 0.03f * sin(t))
                    paint.shader = RadialGradient(
                        cx, cy, r,
                        intArrayOf(0xCCFFF3B0.toInt(), 0x55FFD54F.toInt(), 0),
                        floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawCircle(cx, cy, r, paint)
                    paint.shader = null
                    paint.color = 0xFFFFE680.toInt()
                    c.drawCircle(cx, cy, w * 0.09f, paint)
                } else {
                    for (i in 0 until 70) {
                        val x = ((i * 7919) % 1000) / 1000f * w
                        val y = ((i * 104729) % 1000) / 1000f * h * 0.6f
                        paint.color = Color.argb(120 + (100 * sin(t * 2f + i)).toInt(), 255, 255, 255)
                        c.drawCircle(x, y, 1.5f + (i % 3), paint)
                    }
                    paint.color = 0xFFF5F0D0.toInt()
                    c.drawCircle(cx, cy, w * 0.07f, paint)
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

            val slant = min(wind.toFloat(), 40f) / 40f * 10f

            // ploaie / furtuna
            if (k == 4 || k == 6) {
                sp.color = Color.argb(150, 200, 220, 255)
                sp.strokeWidth = max(2f, w / 400f)
                for (p in parts) {
                    p.y += p.v * sc
                    p.x += slant * sc
                    if (p.y > h) { p.y = -40f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    val len = (24f + p.s * 20f) * sc
                    c.drawLine(p.x, p.y, p.x - slant * 2f * sc, p.y - len, sp)
                }
            }

            // ninsoare
            if (k == 5) {
                paint.color = Color.argb(225, 255, 255, 255)
                for (p in parts) {
                    p.y += p.v * sc
                    p.x += sin(t * 1.5f + p.y * 0.01f) * 1.2f + slant * 0.2f
                    if (p.y > h) { p.y = -20f; p.x = rnd.nextFloat() * w }
                    if (p.x > w) p.x -= w
                    if (p.x < 0f) p.x += w
                    c.drawCircle(p.x, p.y, max(1.5f, p.s * sc * 1.6f), paint)
                }
            }

            // fulgere
            if (k == 6) {
                val now = SystemClock.uptimeMillis()
                if (now > nextBolt) {
                    nextBolt = now + 2500 + rnd.nextInt(6000)
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

            // temperatura (sterge blocul asta daca nu o vrei pe ecran)
            paint.color = Color.argb(190, 255, 255, 255)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = w * 0.10f
            paint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            c.drawText("${temp.roundToInt()}°", w / 2f, h * 0.86f, paint)
        }
    }
}
