package ro.vremea.wallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun t6(a: Activity, s: String, size: Float, alpha: Float = 1f, bold: Boolean = false): TextView {
    val v = TextView(a)
    v.text = s
    v.textSize = size
    v.setTextColor(Color.WHITE)
    v.alpha = alpha
    if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
    return v
}

private fun card6(a: Activity): LinearLayout {
    val d = a.resources.displayMetrics.density
    val l = LinearLayout(a)
    l.orientation = LinearLayout.VERTICAL
    val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x55FFFFFF, 0x14FFFFFF))
    bg.cornerRadius = 18f * d
    bg.setStroke(max(1, d.toInt()), 0x44FFFFFF)
    l.background = bg
    l.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
    return l
}

private fun lp6(a: Activity): LinearLayout.LayoutParams {
    val lp = LinearLayout.LayoutParams(-1, -2)
    lp.topMargin = (12 * a.resources.displayMetrics.density).toInt()
    return lp
}

private class Sc(val name: String, base: Double) {
    var s = base
    var worst = 0.0
    var why = ""
    fun pen(v: Double, label: String) {
        if (v <= 0.0) return
        s -= v
        if (v > worst) {
            worst = v
            why = label
        }
    }
}

private fun bar(n: Int): String = "▰".repeat(n) + "▱".repeat(10 - n)

// Bare cu ploaia pe urmatoarele 2 ore
class RainBars(ctx: Context, private val v: DoubleArray) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val n = v.size
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w / n
        var mx = 1.0
        for (x in v) if (x > mx) mx = x
        p.textAlign = Paint.Align.CENTER
        p.textSize = 11f * d
        for (i in 0 until n) {
            val bh = ((v[i] / mx) * (h - 26f * d).toDouble()).toFloat()
            val x0 = i * bw + bw * 0.18f
            val x1 = (i + 1) * bw - bw * 0.18f
            p.style = Paint.Style.FILL
            p.color = 0xFF7FD0FF.toInt()
            c.drawRoundRect(RectF(x0, h - 20f * d - max(bh, 2f * d), x1, h - 20f * d), 3f * d, 3f * d, p)
            if (i % 2 == 0) {
                p.color = Color.argb(210, 255, 255, 255)
                c.drawText(if (i == 0) "acum" else "+" + (i * 15), (x0 + x1) / 2f, h - 4f * d, p)
            }
        }
    }
}

// "Bun pentru..." si bare cu ploaia pe 2 ore
fun addExtras5(a: Activity, body: LinearLayout, j: JSONObject, f: Boolean) {
    try {
        val d = a.resources.displayMetrics.density
        val cur = j.getJSONObject("current")
        val dly = j.getJSONObject("daily")
        var feels = cur.getDouble("apparent_temperature")
        var wind = cur.getDouble("wind_speed_10m")
        var gust = cur.optDouble("wind_gusts_10m", wind)
        if (f) {
            feels = (feels - 32.0) * 5.0 / 9.0
            wind *= 1.609
            gust *= 1.609
        }
        val pp = dly.getJSONArray("precipitation_probability_max").optInt(0).toDouble()
        val uv = dly.getJSONArray("uv_index_max").optDouble(0, 0.0)
        val rh = cur.optDouble("relative_humidity_2m", 50.0)
        val cl = cur.optDouble("cloud_cover", 50.0)
        val nowRain = cur.optDouble("precipitation", 0.0) > 0.1

        val run = Sc("🏃 Alergat", 10.0)
        run.pen(max(0.0, abs(feels - 12.0) - 4.0) * 0.7, "temperatura")
        run.pen(if (nowRain) 4.0 else if (pp >= 60) 3.0 else 0.0, "ploaie")
        run.pen(max(0.0, wind - 25.0) * 0.1, "vânt")
        run.pen(if (uv >= 8) 1.0 else 0.0, "UV")
        run.pen(if (rh > 85 && feels > 22) 1.0 else 0.0, "umezeală")

        val bike = Sc("🚴 Bicicletă", 10.0)
        bike.pen(max(0.0, abs(feels - 20.0) - 5.0) * 0.6, "temperatura")
        bike.pen(max(0.0, wind - 20.0) * 0.12, "vânt")
        bike.pen(if (nowRain) 5.0 else if (pp >= 40) 3.0 else 0.0, "ploaie")

        val grill = Sc("🍖 Grătar", 10.0)
        grill.pen(max(0.0, abs(feels - 24.0) - 6.0) * 0.6, "temperatura")
        grill.pen(max(0.0, wind - 25.0) * 0.15, "vânt")
        grill.pen(if (nowRain) 6.0 else if (pp >= 30) 4.0 else 0.0, "ploaie")

        val lau = Sc("👕 Rufe afară", 6.0)
        if (wind >= 8.0 && wind <= 25.0) lau.s += 2.0
        if (cl < 50.0) lau.s += 2.0
        lau.pen(if (nowRain) 6.0 else if (pp >= 40) 6.0 else 0.0, "ploaie")
        lau.pen(if (rh > 80) 2.0 else 0.0, "umezeală")
        lau.pen(if (feels < 5.0) 3.0 else 0.0, "frig")

        val drone = Sc("🚁 Dronă", 10.0)
        drone.pen(max(0.0, gust - 12.0) * 0.3, "vânt")
        drone.pen(if (nowRain) 5.0 else if (pp >= 30) 4.0 else 0.0, "ploaie")
        drone.pen(if (feels < 0.0) 2.0 else 0.0, "frig")

        val box = card6(a)
        box.addView(t6(a, "✅ Bun pentru…", 14f, 0.85f))
        for (s in listOf(run, bike, grill, lau, drone)) {
            val sc = s.s.coerceIn(1.0, 10.0).roundToInt()
            val extra = if (sc < 6 && s.why.isNotEmpty()) "  · " + s.why else ""
            val row = t6(a, s.name + "  " + bar(sc) + " " + sc + "/10" + extra, 14f)
            row.setPadding(0, (6 * d).toInt(), 0, 0)
            box.addView(row)
        }
        body.addView(box, lp6(a))

        val m = j.optJSONObject("minutely_15")
        val prc = m?.optJSONArray("precipitation")
        val tm = m?.optJSONArray("time")
        if (prc != null && tm != null) {
            val curT = cur.getString("time")
            var s = 0
            while (s < tm.length() && tm.getString(s) < curT) s++
            val n = min(8, tm.length() - s)
            if (n >= 4) {
                val v = DoubleArray(n) { prc.optDouble(s + it, 0.0) }
                var mxv = 0.0
                for (x in v) mxv = max(mxv, x)
                if (mxv >= 0.05) {
                    val box2 = card6(a)
                    box2.addView(t6(a, "☔ Ploaia în următoarele 2 ore", 14f, 0.85f))
                    box2.addView(RainBars(a, v), LinearLayout.LayoutParams(-1, (110 * d).toInt()))
                    body.addView(box2, lp6(a))
                }
            }
        }
    } catch (e: Exception) { }
}

// Cat de sigura e prognoza: diferenta dintre modelele ECMWF, GFS si ICON
fun addConfidence(a: Activity, body: LinearLayout, f: Boolean) {
    val pr0 = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val lat = pr0.getString("lat", "") ?: ""
    val lon = pr0.getString("lon", "") ?: ""
    if (lat.isEmpty() || lon.isEmpty()) return
    Thread {
        try {
            val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&models=ecmwf_ifs025,gfs_seamless,icon_seamless&daily=temperature_2m_max&forecast_days=3&timezone=auto" +
                (if (f) "&temperature_unit=fahrenheit" else "")
            val dd = JSONObject(URL(u).readText()).getJSONObject("daily")
            val series = ArrayList<JSONArray>()
            val it = dd.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (k.startsWith("temperature_2m_max")) series.add(dd.getJSONArray(k))
            }
            if (series.size < 2) return@Thread
            val n = min(3, series[0].length())
            val sp = DoubleArray(n)
            for (i in 0 until n) {
                var lo = 1e9
                var hi = -1e9
                for (s in series) {
                    if (s.isNull(i)) continue
                    val x = s.getDouble(i)
                    lo = min(lo, x)
                    hi = max(hi, x)
                }
                sp[i] = if (hi < lo) 0.0 else hi - lo
            }
            val avgC = sp.average() * (if (f) 5.0 / 9.0 else 1.0)
            val label = if (avgC <= 1.5) "Mare" else if (avgC <= 3.5) "Medie" else "Scăzută"
            val dot = if (avgC <= 1.5) "🟢" else if (avgC <= 3.5) "🟡" else "🔴"
            val names = arrayOf("Azi", "Mâine", "Poimâine")
            val parts = ArrayList<String>()
            for (i in 0 until n) parts.add(names[i] + " " + sp[i].roundToInt() + "°")
            a.runOnUiThread {
                val now = a.getSharedPreferences("app", Context.MODE_PRIVATE).getString("lat", "")
                if (now == lat) {
                    val box = card6(a)
                    box.tag = "conf"
                    box.addView(t6(a, "🎯 Cât de sigură e prognoza", 14f, 0.85f))
                    box.addView(t6(a, "$dot $label", 20f, 1f, true))
                    box.addView(t6(a, "Diferența dintre modele: " + parts.joinToString(" · "), 13f, 0.85f))
                    val old = body.findViewWithTag<View>("conf")
                    if (old != null) body.removeView(old)
                    body.addView(box, max(0, body.childCount - 1), lp6(a))
                }
            }
        } catch (e: Exception) { }
    }.start()
}
