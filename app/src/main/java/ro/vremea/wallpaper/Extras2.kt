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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun txt2(a: Activity, s: String, size: Float, alpha: Float = 1f, bold: Boolean = false): TextView {
    val v = TextView(a)
    v.text = s
    v.textSize = size
    v.setTextColor(Color.WHITE)
    v.alpha = alpha
    if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
    return v
}

private fun card2(a: Activity): LinearLayout {
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

private fun moonPhaseNow(): Float {
    val days = (System.currentTimeMillis() / 1000.0 - 947182440.0) / 86400.0
    val p = (days % 29.530588853) / 29.530588853
    return (if (p < 0) p + 1 else p).toFloat()
}

// Arcul soarelui: de la rasarit la apus, cu pozitia curenta
class SunArc(ctx: Context, private val rise: Int, private val sset: Int, private val now: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fmt(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val l = 34f * d
        val r = w - 34f * d
        val baseY = h - 34f * d
        val rx = (r - l) / 2f
        val ry = baseY - 14f * d
        val cx = (l + r) / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = Color.argb(120, 255, 255, 255)
        c.drawLine(l - 14f * d, baseY, r + 14f * d, baseY, p)
        val oval = RectF(cx - rx, baseY - ry, cx + rx, baseY + ry)
        p.pathEffect = DashPathEffect(floatArrayOf(6f * d, 6f * d), 0f)
        c.drawArc(oval, 180f, 180f, false, p)
        p.pathEffect = null
        val frac = ((now - rise).toFloat() / max(1, sset - rise)).coerceIn(0f, 1f)
        val up = now in rise..sset
        if (up) {
            p.strokeWidth = 3.5f * d
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFFFFD21F.toInt()
            c.drawArc(oval, 180f, 180f * frac, false, p)
        }
        val sx = cx - rx * cos(PI * frac).toFloat()
        val sy = baseY - ry * sin(PI * frac).toFloat()
        p.style = Paint.Style.FILL
        if (up) {
            p.shader = RadialGradient(sx, sy, 20f * d, intArrayOf(0x99FFD54F.toInt(), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, 20f * d, p)
            p.shader = null
            p.color = 0xFFFFC72C.toInt()
            c.drawCircle(sx, sy, 8f * d, p)
        } else {
            p.color = Color.argb(150, 220, 230, 255)
            c.drawCircle(if (now < rise) l else r, baseY, 5f * d, p)
        }
        p.textAlign = Paint.Align.CENTER
        p.textSize = 12f * d
        p.color = Color.WHITE
        c.drawText("↑ " + fmt(rise), l, baseY + 20f * d, p)
        c.drawText("↓ " + fmt(sset), r, baseY + 20f * d, p)
    }
}

// Luna cu faza reala
class MoonView(ctx: Context, private val ph: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 3f
        p.style = Paint.Style.FILL
        p.color = 0xFF2A3350.toInt()
        c.drawCircle(cx, cy, r, p)
        val waning = ph > 0.5f
        val q = if (waning) 1f - ph else ph
        val cs = cos(2.0 * PI * q.toDouble()).toFloat()
        val rx = r * abs(cs)
        val path = Path()
        path.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f)
        if (cs >= 0f) {
            path.arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), 90f, -180f)
        } else {
            path.arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), 90f, 180f)
        }
        path.close()
        c.save()
        if (waning) c.scale(-1f, 1f, cx, cy)
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFF7C2.toInt(), 0xFFE9C45A.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0x55FFFFFF
        c.drawCircle(cx, cy, r, p)
    }
}

fun addSunMoon(a: Activity, body: LinearLayout, j: JSONObject) {
    try {
        val d = j.getJSONObject("daily")
        val sr = d.getJSONArray("sunrise").getString(0)
        val ss = d.getJSONArray("sunset").getString(0)
        val nowT = j.getJSONObject("current").getString("time")
        fun hm(s: String): Int = s.substring(11, 13).toInt() * 60 + s.substring(14, 16).toInt()
        val rise = hm(sr)
        val sset = hm(ss)
        val now = hm(nowT)
        val dens = a.resources.displayMetrics.density
        val box = card2(a)
        box.addView(txt2(a, "🌅 Soare și lună", 14f, 0.85f))
        box.addView(SunArc(a, rise, sset, now), LinearLayout.LayoutParams(-1, (130 * dens).toInt()))
        val len = sset - rise
        val t1 = txt2(a, "Ziua durează ${len / 60} h ${len % 60} min", 13f, 0.9f)
        t1.gravity = Gravity.CENTER
        box.addView(t1, LinearLayout.LayoutParams(-1, -2))

        val ph = moonPhaseNow()
        val illum = ((1.0 - cos(2.0 * PI * ph.toDouble())) / 2.0 * 100.0).roundToInt()
        val name = when {
            ph < 0.03f || ph > 0.97f -> "Lună nouă"
            ph < 0.22f -> "Semilună crescătoare"
            ph < 0.28f -> "Primul pătrar"
            ph < 0.47f -> "Lună crescătoare"
            ph < 0.53f -> "Lună plină"
            ph < 0.72f -> "Lună descrescătoare"
            ph < 0.78f -> "Ultimul pătrar"
            else -> "Semilună descrescătoare"
        }
        val row = LinearLayout(a)
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(MoonView(a, ph), LinearLayout.LayoutParams((56 * dens).toInt(), (56 * dens).toInt()))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((14 * dens).toInt(), 0, 0, 0)
        col.addView(txt2(a, name, 17f, 1f, true))
        col.addView(txt2(a, "Iluminată: $illum%", 13f, 0.85f))
        row.addView(col)
        val rp = LinearLayout.LayoutParams(-1, -2)
        rp.topMargin = (10 * dens).toInt()
        box.addView(row, rp)

        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = (12 * dens).toInt()
        body.addView(box, lp)
    } catch (e: Exception) { }
}
