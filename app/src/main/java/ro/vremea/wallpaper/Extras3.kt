package ro.vremea.wallpaper

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun t3(a: Activity, s: String, size: Float, alpha: Float = 1f, bold: Boolean = false): TextView {
    val v = TextView(a)
    v.text = s
    v.textSize = size
    v.setTextColor(Color.WHITE)
    v.alpha = alpha
    if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
    return v
}

private fun card3(a: Activity): LinearLayout {
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

// Busola: sageata arata incotro bate vantul
class WindCompass(ctx: Context, private val from: Float, private val speed: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 20f * d
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = Color.argb(150, 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        for (i in 0 until 16) {
            c.save()
            c.rotate(i * 22.5f, cx, cy)
            val big = i % 4 == 0
            p.strokeWidth = (if (big) 2.5f else 1.2f) * d
            c.drawLine(cx, cy - r, cx, cy - r + (if (big) 9f else 5f) * d, p)
            c.restore()
        }
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = 13f * d
        p.color = Color.WHITE
        c.drawText("N", cx, cy - r - 5f * d, p)
        c.drawText("S", cx, cy + r + 15f * d, p)
        c.drawText("E", cx + r + 11f * d, cy + 5f * d, p)
        c.drawText("V", cx - r - 11f * d, cy + 5f * d, p)
        c.save()
        c.rotate(from + 180f, cx, cy)
        val ar = Path()
        ar.moveTo(cx, cy - r * 0.85f)
        ar.lineTo(cx + r * 0.22f, cy - r * 0.2f)
        ar.lineTo(cx, cy - r * 0.35f)
        ar.lineTo(cx - r * 0.22f, cy - r * 0.2f)
        ar.close()
        p.color = 0xFF7FD0FF.toInt()
        c.drawPath(ar, p)
        c.restore()
        p.color = Color.WHITE
        p.textSize = 16f * d
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        c.drawText(speed, cx, cy + 6f * d, p)
    }
}

fun addDaily(a: Activity, body: LinearLayout, j: JSONObject, f: Boolean) {
    try {
        val d = a.resources.displayMetrics.density
        val cur = j.getJSONObject("current")
        val day = j.getJSONObject("daily")
        var feels = cur.getDouble("apparent_temperature")
        var wind = cur.getDouble("wind_speed_10m")
        if (f) {
            feels = (feels - 32.0) * 5.0 / 9.0
            wind *= 1.609
        }
        val pp = day.getJSONArray("precipitation_probability_max").optInt(0)
        val uv = day.getJSONArray("uv_index_max").optDouble(0, 0.0)
        val lines = ArrayList<String>()
        lines.add(
            when {
                feels <= 0 -> "🧥 Haină groasă, căciulă și mănuși"
                feels <= 8 -> "🧥 Jachetă groasă"
                feels <= 15 -> "🧥 Jachetă ușoară"
                feels <= 22 -> "👕 Mânecă lungă sau pulover subțire"
                feels <= 28 -> "👕 Tricou"
                else -> "🩳 Haine ușoare și multă apă"
            }
        )
        if (pp >= 50) lines.add("☂️ Ia umbrela (șanse de ploaie $pp%)")
        else if (pp >= 30) lines.add("🌦️ Poate ploua: ia o umbrelă mică ($pp%)")
        if (uv >= 6) lines.add("🧴 Cremă de protecție și ochelari de soare")
        else if (uv >= 3) lines.add("🕶️ Ochelari de soare")
        if (wind >= 40) lines.add("💨 Vânt puternic: atenție la obiectele ușoare")
        if (feels <= -10) lines.add("🥶 Frig sever: limitează timpul afară")
        else if (feels >= 33) lines.add("🥵 Caniculă: evită soarele între 11 și 17")

        val box = card3(a)
        box.addView(t3(a, "🎒 Ce să port azi", 14f, 0.85f))
        for (l in lines) {
            val tv = t3(a, l, 15f)
            tv.setPadding(0, (6 * d).toInt(), 0, 0)
            box.addView(tv)
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = (12 * d).toInt()
        body.addView(box, lp)

        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SV", "V", "NV")
        val from = cur.optDouble("wind_direction_10m", 0.0)
        val nm = dirs[((from + 22.5) / 45).toInt() % 8]
        val unit = if (f) "mph" else "km/h"
        val shown = cur.getDouble("wind_speed_10m").roundToInt()
        val gust = cur.optDouble("wind_gusts_10m", 0.0).roundToInt()
        val box2 = card3(a)
        box2.addView(t3(a, "🧭 Vânt", 14f, 0.85f))
        val row = LinearLayout(a)
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(WindCompass(a, from.toFloat(), "$shown"), LinearLayout.LayoutParams((130 * d).toInt(), (130 * d).toInt()))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((14 * d).toInt(), 0, 0, 0)
        col.addView(t3(a, "Dinspre $nm", 17f, 1f, true))
        col.addView(t3(a, "$shown $unit · rafale $gust", 13f, 0.85f))
        row.addView(col)
        box2.addView(row)
        val lp2 = LinearLayout.LayoutParams(-1, -2)
        lp2.topMargin = (12 * d).toInt()
        body.addView(box2, lp2)
    } catch (e: Exception) { }
}
