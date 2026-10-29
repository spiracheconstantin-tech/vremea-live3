package ro.vremea.wallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        Thread {
            try { updateAll(context) } catch (e: Exception) { } finally { pr.finish() }
        }.start()
    }

    companion object {
        private val DP = listOf(
            "#1F8FE0", "#BFE6FB", "#3E9BDB", "#C9E4F5", "#5C7A95", "#CFDBE5", "#8A979F",
            "#D6DCDF", "#2F4256", "#7F97AB", "#7C93AD", "#E8EFF6", "#1E2433", "#5A6683"
        ).map { Color.parseColor(it) }
        private val NP = listOf(
            "#0A1230", "#3A5088", "#101A3A", "#3F5590", "#1E2535", "#48546A", "#2A3038",
            "#56606A", "#141C28", "#3A4A5C", "#2A3550", "#6A7C98", "#0D111B", "#2E374D"
        ).map { Color.parseColor(it) }

        fun refreshAll(ctx: Context) {
            val app = ctx.applicationContext
            Thread { try { updateAll(app) } catch (e: Exception) { } }.start()
        }

        private fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))
            if (ids.isEmpty()) return
            val bmp = build(ctx) ?: return
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val rv = RemoteViews(ctx.packageName, R.layout.widget)
                rv.setImageViewBitmap(R.id.img, bmp)
                rv.setOnClickPendingIntent(R.id.img, pi)
                mgr.updateAppWidget(id, rv)
            }
        }

        private fun build(ctx: Context): Bitmap? {
            try {
                val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                val f = ap.getString("units", "c") == "f"
                val name = ap.getString("name", null) ?: "București"
                val lat = ap.getString("lat", null) ?: "44.4268"
                val lon = ap.getString("lon", null) ?: "26.1025"
                val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto" +
                    (if (f) "&temperature_unit=fahrenheit" else "")
                val conn = URL(u).openConnection()
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val j = JSONObject(conn.getInputStream().bufferedReader().readText())
                val cur = j.getJSONObject("current")
                val d = j.getJSONObject("daily")
                return draw(
                    name,
                    cur.getDouble("temperature_2m"),
                    cur.getDouble("apparent_temperature"),
                    cur.getInt("weather_code"),
                    cur.getInt("is_day") == 1,
                    d.getJSONArray("temperature_2m_max").getDouble(0),
                    d.getJSONArray("temperature_2m_min").getDouble(0),
                    f
                )
            } catch (e: Exception) {
                return null
            }
        }

        private fun cat(code: Int): Int = when {
            code == 0 -> 0
            code <= 2 -> 1
            code == 3 -> 2
            code in 45..48 -> 3
            code in 51..67 || code in 80..82 -> 4
            code in 71..77 || code in 85..86 -> 5
            else -> 6
        }

        private fun label(code: Int): String = when {
            code == 0 -> "Senin"
            code <= 2 -> "Parțial noros"
            code == 3 -> "Noros"
            code <= 48 -> "Ceață"
            code <= 57 -> "Burniță"
            code <= 67 -> "Ploaie"
            code <= 77 -> "Ninsoare"
            code <= 82 -> "Averse"
            code <= 86 -> "Averse de zăpadă"
            else -> "Furtună"
        }

        private fun draw(name: String, t: Double, feels: Double, code: Int, day: Boolean, mx: Double, mn: Double, f: Boolean): Bitmap {
            val W = 720
            val H = 300
            val wf = W.toFloat()
            val hf = H.toFloat()
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val clip = Path()
            clip.addRoundRect(RectF(0f, 0f, wf, hf), 52f, 52f, Path.Direction.CW)
            c.clipPath(clip)

            val k = cat(code)
            val pal = if (day) DP else NP
            p.shader = LinearGradient(0f, 0f, 0f, hf, pal[k * 2], pal[k * 2 + 1], Shader.TileMode.CLAMP)
            c.drawPaint(p)
            p.shader = null

            val tc = (if (f) (t - 32.0) * 5.0 / 9.0 else t).toFloat()
            if (tc < 15f) {
                c.drawColor(Color.argb(((15f - max(tc, -10f)) / 25f * 80).toInt(), 120, 170, 255))
            } else {
                c.drawColor(Color.argb(((min(tc, 38f) - 15f) / 23f * 90).toInt(), 255, 130, 50))
            }

            val ic = weatherBitmap(code, day)
            c.drawBitmap(ic, null, RectF(440f, 20f, 700f, 280f), p)

            val nm = if (name.length > 18) name.take(17) + "…" else name
            p.color = Color.WHITE
            p.setShadowLayer(6f, 0f, 2f, 0x66000000)
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.textSize = 34f
            c.drawText(nm, 40f, 64f, p)
            p.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            p.textSize = 130f
            c.drawText("${t.roundToInt()}°", 36f, 186f, p)
            p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            p.textSize = 30f
            c.drawText(label(code), 40f, 232f, p)
            p.textSize = 24f
            p.alpha = 220
            c.drawText("Max ${mx.roundToInt()}° · Min ${mn.roundToInt()}° · Simte ${feels.roundToInt()}°", 40f, 274f, p)
            return bmp
        }
    }
}
