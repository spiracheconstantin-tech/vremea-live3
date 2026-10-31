package ro.vremea.wallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.URL
import kotlin.math.max
import kotlin.math.min

private val WDP = listOf(
    "#1F8FE0", "#BFE6FB", "#3E9BDB", "#C9E4F5", "#5C7A95", "#CFDBE5", "#8A979F",
    "#D6DCDF", "#2F4256", "#7F97AB", "#7C93AD", "#E8EFF6", "#1E2433", "#5A6683"
).map { Color.parseColor(it) }
private val WNP = listOf(
    "#0A1230", "#3A5088", "#101A3A", "#3F5590", "#1E2535", "#48546A", "#2A3038",
    "#56606A", "#141C28", "#3A4A5C", "#2A3550", "#6A7C98", "#0D111B", "#2E374D"
).map { Color.parseColor(it) }

internal fun wCat(code: Int): Int = when {
    code == 0 -> 0
    code <= 2 -> 1
    code == 3 -> 2
    code in 45..48 -> 3
    code in 51..67 || code in 80..82 -> 4
    code in 71..77 || code in 85..86 -> 5
    else -> 6
}

internal class CurData(
    val name: String, val t: Double, val code: Int, val day: Boolean, val f: Boolean,
    val dates: List<String>, val dCode: IntArray, val dMax: DoubleArray, val dMin: DoubleArray
)

internal fun fetchCur(ctx: Context): CurData? {
    try {
        val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        val f = ap.getString("units", "c") == "f"
        val name = ap.getString("name", null) ?: "București"
        val lat = ap.getString("lat", null) ?: "44.4268"
        val lon = ap.getString("lon", null) ?: "26.1025"
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&timezone=auto" +
            (if (f) "&temperature_unit=fahrenheit" else "")
        val conn = URL(u).openConnection()
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val j = JSONObject(conn.getInputStream().bufferedReader().readText())
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val tm = d.getJSONArray("time")
        val wc = d.getJSONArray("weather_code")
        val mx = d.getJSONArray("temperature_2m_max")
        val mn = d.getJSONArray("temperature_2m_min")
        val n = tm.length()
        val dates = ArrayList<String>()
        for (i in 0 until n) dates.add(tm.getString(i))
        return CurData(
            name, cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.getInt("is_day") == 1, f, dates,
            IntArray(n) { wc.getInt(it) }, DoubleArray(n) { mx.getDouble(it) }, DoubleArray(n) { mn.getDouble(it) }
        )
    } catch (e: Exception) {
        return null
    }
}

internal fun widgetSize(ctx: Context, mgr: AppWidgetManager, id: Int): IntArray {
    val o = mgr.getAppWidgetOptions(id)
    val dens = ctx.resources.displayMetrics.density
    val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val w0 = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val h0 = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    var w = ((if (w0 > 0) w0 else 110) * dens).toInt()
    var h = ((if (h0 > 0) h0 else 110) * dens).toInt()
    val big = max(w, h)
    if (big > 900) {
        val s = 900f / big
        w = (w * s).toInt()
        h = (h * s).toInt()
    }
    return intArrayOf(max(w, 100), max(h, 60))
}

internal fun pushWidget(ctx: Context, mgr: AppWidgetManager, id: Int, bmp: Bitmap) {
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val rv = RemoteViews(ctx.packageName, R.layout.widget)
    rv.setImageViewBitmap(R.id.img, bmp)
    rv.setOnClickPendingIntent(R.id.img, pi)
    mgr.updateAppWidget(id, rv)
}

internal fun widgetBg(c: Canvas, W: Int, H: Int, code: Int, day: Boolean, tC: Float) {
    val wf = W.toFloat()
    val hf = H.toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val rad = min(wf, hf) * 0.17f
    val clip = Path()
    clip.addRoundRect(RectF(0f, 0f, wf, hf), rad, rad, Path.Direction.CW)
    c.clipPath(clip)
    val k = wCat(code)
    val pal = if (day) WDP else WNP
    p.shader = LinearGradient(0f, 0f, 0f, hf, pal[k * 2], pal[k * 2 + 1], Shader.TileMode.CLAMP)
    c.drawPaint(p)
    if (tC < 15f) {
        c.drawColor(Color.argb(((15f - max(tC, -10f)) / 25f * 80).toInt(), 120, 170, 255))
    } else {
        c.drawColor(Color.argb(((min(tC, 38f) - 15f) / 23f * 90).toInt(), 255, 130, 50))
    }
}

internal fun wText(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, maxW: Float, alpha: Int = 255) {
    p.color = Color.argb(alpha, 255, 255, 255)
    p.textSize = size
    val w = p.measureText(s)
    if (w > maxW && w > 0f && maxW > 0f) p.textSize = size * maxW / w
    c.drawText(s, x, y, p)
}
