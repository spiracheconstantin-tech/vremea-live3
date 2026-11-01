package ro.vremea.wallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun t5(a: Activity, s: String, size: Float, alpha: Float = 1f, bold: Boolean = false): TextView {
    val v = TextView(a)
    v.text = s
    v.textSize = size
    v.setTextColor(Color.WHITE)
    v.alpha = alpha
    if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
    return v
}

private val CUM = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

// Compara ziua de azi cu media aceleiasi zile (+-3 zile) din ultimii 10 ani
fun addNormal(a: Activity, body: LinearLayout, j: JSONObject, f: Boolean) {
    val pr0 = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val lat = pr0.getString("lat", "") ?: ""
    val lon = pr0.getString("lon", "") ?: ""
    if (lat.isEmpty() || lon.isEmpty()) return
    val dly = j.getJSONObject("daily")
    val mean = (dly.getJSONArray("temperature_2m_max").getDouble(0) + dly.getJSONArray("temperature_2m_min").getDouble(0)) / 2.0
    Thread {
        try {
            val cal = Calendar.getInstance()
            val mo = cal.get(Calendar.MONTH)
            val dm = cal.get(Calendar.DAY_OF_MONTH)
            val key = lat + "|" + lon + "|" + mo + "-" + dm + "|" + (if (f) "f" else "c")
            val np = a.getSharedPreferences("norm", Context.MODE_PRIVATE)
            var avg = if (np.getString("key", "") == key) np.getFloat("avg", Float.NaN).toDouble() else Double.NaN
            if (avg.isNaN()) {
                val y = cal.get(Calendar.YEAR)
                val u = "https://archive-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lon" +
                    "&start_date=${y - 10}-01-01&end_date=${y - 1}-12-31&daily=temperature_2m_mean&timezone=auto" +
                    (if (f) "&temperature_unit=fahrenheit" else "")
                val dd = JSONObject(URL(u).readText()).getJSONObject("daily")
                val tm = dd.getJSONArray("time")
                val tv = dd.getJSONArray("temperature_2m_mean")
                val tOrd = CUM[mo] + dm
                var sum = 0.0
                var cnt = 0
                for (i in 0 until tm.length()) {
                    if (tv.isNull(i)) continue
                    val s = tm.getString(i)
                    val ord = CUM[s.substring(5, 7).toInt() - 1] + s.substring(8, 10).toInt()
                    val diff = abs(ord - tOrd)
                    if (min(diff, 365 - diff) <= 3) {
                        sum += tv.getDouble(i)
                        cnt++
                    }
                }
                if (cnt > 0) avg = sum / cnt
                if (!avg.isNaN()) np.edit().putString("key", key).putFloat("avg", avg.toFloat()).apply()
            }
            val av = avg
            a.runOnUiThread {
                val now = a.getSharedPreferences("app", Context.MODE_PRIVATE).getString("lat", "")
                if (now == lat && !av.isNaN()) {
                    val diff = mean - av
                    val head = if (abs(diff) < 1.0) "Aproape de normal"
                    else if (diff > 0) "Cu ${diff.roundToInt()}° mai cald decât normal"
                    else "Cu ${(-diff).roundToInt()}° mai rece decât normal"
                    val d = a.resources.displayMetrics.density
                    val box = LinearLayout(a)
                    box.orientation = LinearLayout.VERTICAL
                    box.tag = "normal"
                    val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x55FFFFFF, 0x14FFFFFF))
                    bg.cornerRadius = 18f * d
                    bg.setStroke(max(1, d.toInt()), 0x44FFFFFF)
                    box.background = bg
                    box.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                    box.addView(t5(a, "📊 Față de normal", 14f, 0.85f))
                    box.addView(t5(a, head, 18f, 1f, true))
                    box.addView(t5(a, "Azi: ${mean.roundToInt()}° · media zilei în ultimii 10 ani: ${av.roundToInt()}°", 13f, 0.85f))
                    val old = body.findViewWithTag<android.view.View>("normal")
                    if (old != null) body.removeView(old)
                    val lp = LinearLayout.LayoutParams(-1, -2)
                    lp.topMargin = (12 * d).toInt()
                    body.addView(box, max(0, body.childCount - 1), lp)
                }
            }
        } catch (e: Exception) { }
    }.start()
}
