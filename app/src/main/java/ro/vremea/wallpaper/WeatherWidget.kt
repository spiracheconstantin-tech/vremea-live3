package ro.vremea.wallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class WD(
    val name: String, val t: Double, val feels: Double, val code: Int,
    val day: Boolean, val mx: Double, val mn: Double, val f: Boolean
)

class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        Thread {
            try { updateAll(context) } catch (e: Exception) { } finally { pr.finish() }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        refreshAll(context)
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
            val data = fetch(ctx) ?: return
            val dens = ctx.resources.displayMetrics.density
            val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val o = mgr.getAppWidgetOptions(id)
                val wdp0 = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val hdp0 = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                val wdp = if (wdp0 > 0) wdp0 else 250
                val hdp = if (hdp0 > 0) hdp0 else 110
                var wp = (wdp * dens).toInt()
                var hp = (hdp * dens).toInt()
                val big = max(wp, hp)
                if (big > 900) {
                    val s = 900f / big
                    wp = (wp * s).toInt()
                    hp = (hp * s).toInt()
                }
                wp = max(wp, 100)
                hp = max(hp, 60)
                val bmp = draw(data, wp, hp)
                val rv = RemoteViews(ctx.packageName, R.layout.widget)
                rv.setImageViewBitmap(R.id.img, bmp)
                rv.setOnClickPendingIntent(R.id.img, pi)
                mgr.updateAppWidget(id, rv)
            }
        }

        private fun fetch(ctx: Context): WD? {
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
                return WD(
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

        // scrie textul si il micsoreaza daca nu incape in latimea disponibila
        private fun put(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, maxW: Float, alpha: Int = 255) {
            p.color = Color.argb(alpha, 255, 255, 255)
            p.textSize = size
            val w = p.measureText(s)
            if (w > maxW && w > 0f && maxW > 0f) p.textSize = size * maxW / w
            c.drawText(s, x, y, p)
        }

        private fun draw(d: WD, W: Int, H: Int): Bitmap {
            val wf = W.toFloat()
            val hf = H.toFloat()
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val rad = min(wf, hf) * 0.17f
            val clip = Path()
            clip.addRoundRect(RectF(0f, 0f, wf, hf), rad, rad, Path.Direction.CW)
            c.clipPath(clip)

            val k = cat(d.code)
            val pal = if (d.day) DP else NP
            p.shader = LinearGradient(0f, 0f, 0f, hf, pal[k * 2], pal[k * 2 + 1], Shader.TileMode.CLAMP)
            c.drawPaint(p)
            p.shader = null

            val tc = (if (d.f) (d.t - 32.0) * 5.0 / 9.0 else d.t).toFloat()
            if (tc < 15f) {
                c.drawColor(Color.argb(((15f - max(tc, -10f)) / 25f * 80).toInt(), 120, 170, 255))
            } else {
                c.drawColor(Color.argb(((min(tc, 38f) - 15f) / 23f * 90).toInt(), 255, 130, 50))
            }

            val ic = weatherBitmap(d.code, d.day)
            val nm = if (d.name.length > 20) d.name.take(19) + "…" else d.name
            val temp = "${d.t.roundToInt()}°"
            val lab = label(d.code)
            val full = "Max ${d.mx.roundToInt()}° · Min ${d.mn.roundToInt()}° · Simte ${d.feels.roundToInt()}°"
            val short = "Max ${d.mx.roundToInt()}° · Min ${d.mn.roundToInt()}°"
            val aspect = wf / hf
            val shadowR = max(2f, min(wf, hf) * 0.02f)

            if (aspect > 3.2f) {
                // chenar lat si jos: o singura linie
                val m = hf * 0.08f
                val isz = hf * 0.86f
                c.drawBitmap(ic, null, RectF(m, (hf - isz) / 2f, m + isz, (hf + isz) / 2f), p)
                p.setShadowLayer(shadowR, 0f, shadowR / 3f, 0x66000000)
                p.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                val x = m + isz + m
                put(c, p, temp, x, hf * 0.74f, hf * 0.62f, wf * 0.22f)
                val x2 = x + p.measureText(temp) + m * 1.5f
                val mw = wf - x2 - m
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                put(c, p, nm, x2, hf * 0.44f, hf * 0.28f, mw)
                p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                put(c, p, lab, x2, hf * 0.80f, hf * 0.24f, mw, 230)
            } else if (aspect >= 1.45f) {
                // chenar obisnuit (de exemplu 4x2 sau 3x2)
                val m = hf * 0.10f
                val isz = min(hf * 0.80f, wf * 0.38f)
                val ir = RectF(wf - m - isz, (hf - isz) / 2f, wf - m, (hf + isz) / 2f)
                c.drawBitmap(ic, null, ir, p)
                p.setShadowLayer(shadowR, 0f, shadowR / 3f, 0x66000000)
                val maxW = ir.left - m * 1.5f
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                put(c, p, nm, m, hf * 0.20f, hf * 0.115f, maxW)
                p.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                put(c, p, temp, m - hf * 0.01f, hf * 0.60f, hf * 0.40f, maxW)
                p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                put(c, p, lab, m, hf * 0.76f, hf * 0.10f, maxW)
                put(c, p, if (maxW > hf * 1.2f) full else short, m, hf * 0.91f, hf * 0.082f, maxW, 220)
            } else {
                // chenar aproape patrat (de exemplu 2x2)
                val m = wf * 0.07f
                val base = min(wf, hf)
                val isz = base * 0.40f
                c.drawBitmap(ic, null, RectF(wf - m - isz, m, wf - m, m + isz), p)
                p.setShadowLayer(shadowR, 0f, shadowR / 3f, 0x66000000)
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                put(c, p, nm, m, m + base * 0.09f, base * 0.09f, wf - isz - 3f * m)
                p.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                put(c, p, temp, m, hf * 0.70f, base * 0.28f, wf - 2f * m)
                p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                put(c, p, lab, m, hf * 0.82f, base * 0.085f, wf - 2f * m)
                put(c, p, short, m, hf * 0.93f, base * 0.07f, wf - 2f * m, 220)
            }
            return bmp
        }
    }
}
