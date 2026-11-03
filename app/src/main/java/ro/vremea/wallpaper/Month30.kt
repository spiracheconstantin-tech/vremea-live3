package ro.vremea.wallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MonthChart(ctx: Context, private val mx: DoubleArray, private val mn: DoubleArray, private val dt: List<String>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val n = mx.size
        if (n < 2) return
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val l = 34f * d
        val r = 10f * d
        val top = 14f * d
        val bot = 24f * d
        var lo = mn[0]
        var hi = mx[0]
        for (v in mn) if (v < lo) lo = v
        for (v in mx) if (v > hi) hi = v
        if (hi - lo < 2.0) {
            hi += 1.0
            lo -= 1.0
        }
        val cw = w - l - r
        val ch = h - top - bot
        val lo2 = lo
        val hi2 = hi
        fun px(i: Int): Float = l + cw * i / (n - 1)
        fun py(v: Double): Float = top + ch * (1f - ((v - lo2) / (hi2 - lo2)).toFloat())

        p.textSize = 10f * d
        for (g in 0..2) {
            val v = hi2 - (hi2 - lo2) * g / 2.0
            val y = py(v)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f * d
            p.color = Color.argb(50, 255, 255, 255)
            c.drawLine(l, y, w - r, y, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.RIGHT
            p.color = Color.argb(200, 255, 255, 255)
            c.drawText(v.roundToInt().toString() + "°", l - 4f * d, y + 3.5f * d, p)
        }
        fun line(arr: DoubleArray, col: Int) {
            val path = Path()
            path.moveTo(px(0), py(arr[0]))
            for (i in 1 until n) path.lineTo(px(i), py(arr[i]))
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * d
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            p.color = col
            c.drawPath(path, p)
        }
        line(mx, 0xFFFFB347.toInt())
        line(mn, 0xFF7FD0FF.toInt())
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = Color.argb(200, 255, 255, 255)
        for (i in intArrayOf(0, n / 2, n - 1)) {
            val s = dt[i]
            c.drawText(s.substring(8, 10) + "." + s.substring(5, 7), px(i), h - 6f * d, p)
        }
    }
}

// Temperaturile ultimelor 30 de zile
fun addMonth(a: Activity, body: LinearLayout, f: Boolean) {
    val pr0 = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val lat = pr0.getString("lat", "") ?: ""
    val lon = pr0.getString("lon", "") ?: ""
    if (lat.isEmpty() || lon.isEmpty()) return
    Thread {
        try {
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val key = day + "|" + lat + "|" + lon + "|" + (if (f) "f" else "c")
            val cp = a.getSharedPreferences("month", Context.MODE_PRIVATE)
            var txt: String? = if (cp.getString("key", "") == key) cp.getString("data", null) else null
            if (txt == null) {
                val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&daily=temperature_2m_max,temperature_2m_min&past_days=30&forecast_days=1&timezone=auto" +
                    (if (f) "&temperature_unit=fahrenheit" else "")
                txt = URL(u).readText()
                cp.edit().putString("key", key).putString("data", txt).apply()
            }
            val data: String = txt ?: return@Thread
            val dd = JSONObject(data).getJSONObject("daily")
            val tm = dd.getJSONArray("time")
            val amx = dd.getJSONArray("temperature_2m_max")
            val amn = dd.getJSONArray("temperature_2m_min")
            val dates = ArrayList<String>()
            val hx = ArrayList<Double>()
            val ln = ArrayList<Double>()
            for (i in 0 until tm.length()) {
                if (amx.isNull(i) || amn.isNull(i)) continue
                dates.add(tm.getString(i))
                hx.add(amx.getDouble(i))
                ln.add(amn.getDouble(i))
            }
            if (dates.size < 5) return@Thread
            val ax = hx.toDoubleArray()
            val an = ln.toDoubleArray()
            val avMax = ax.average().roundToInt()
            val avMin = an.average().roundToInt()
            var hottest = ax[0]
            for (v in ax) if (v > hottest) hottest = v
            var coldest = an[0]
            for (v in an) if (v < coldest) coldest = v
            a.runOnUiThread {
                val now = a.getSharedPreferences("app", Context.MODE_PRIVATE).getString("lat", "")
                if (now == lat) {
                    val d = a.resources.displayMetrics.density
                    val box = LinearLayout(a)
                    box.orientation = LinearLayout.VERTICAL
                    box.tag = "month"
                    val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x55FFFFFF, 0x14FFFFFF))
                    bg.cornerRadius = 18f * d
                    bg.setStroke(max(1, d.toInt()), 0x44FFFFFF)
                    box.background = bg
                    box.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                    val t1 = TextView(a)
                    t1.text = "📆 Ultimele 30 de zile"
                    t1.textSize = 14f
                    t1.alpha = 0.85f
                    t1.setTextColor(Color.WHITE)
                    box.addView(t1)
                    box.addView(MonthChart(a, ax, an, dates), LinearLayout.LayoutParams(-1, (170 * d).toInt()))
                    val t2 = TextView(a)
                    t2.text = "Medie: max $avMax° · min $avMin°  ·  cea mai caldă zi ${hottest.roundToInt()}° · cea mai rece noapte ${coldest.roundToInt()}°"
                    t2.textSize = 13f
                    t2.alpha = 0.85f
                    t2.setTextColor(Color.WHITE)
                    t2.setTypeface(t2.typeface, Typeface.NORMAL)
                    box.addView(t2)
                    val old = body.findViewWithTag<View>("month")
                    if (old != null) body.removeView(old)
                    val lp = LinearLayout.LayoutParams(-1, -2)
                    lp.topMargin = (12 * d).toInt()
                    body.addView(box, max(0, body.childCount - 1), lp)
                }
            }
        } catch (e: Exception) { }
    }.start()
}
