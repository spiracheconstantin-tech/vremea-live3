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
    val dates: List<String>, val dCode: IntArray, val dMax: DoubleArray, val dMin: DoubleArray,
    val feels: Double = t, val rainProbability: Int = 0, val nextChange: String = "",
    val updatedAt: Long = 0L, val ageMinutes: Long = 0L, val stale: Boolean = false
)

/** Citește ultima prognoză aleasă de aplicație, inclusiv atunci când telefonul e offline. */
internal fun fetchAppWeatherCache(ctx: Context): CurData? {
    return try {
        val cache = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)
        val raw = cache.getString("last", null) ?: return null
        val at = cache.getLong("at", 0L)
        val age = if (at > 0L) ((System.currentTimeMillis() - at) / 60000L).coerceAtLeast(0L) else Long.MAX_VALUE
        parseWidgetWeather(ctx, JSONObject(raw), at, age)
    } catch (_: Exception) {
        null
    }
}

private fun parseWidgetWeather(ctx: Context, json: JSONObject, updatedAt: Long, ageMinutes: Long): CurData? {
    return try {
        val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        val current = json.optJSONObject("current") ?: return null
        val daily = json.optJSONObject("daily") ?: return null
        val t = current.optDouble("temperature_2m", Double.NaN)
        if (t.isNaN()) return null
        val unit = json.optJSONObject("current_units")?.optString("temperature_2m", "") ?: ""
        val f = if (unit.isNotBlank()) unit.contains("F", ignoreCase = true) else ap.getString("units", "c") == "f"
        val datesJson = daily.optJSONArray("time")
        val codesJson = daily.optJSONArray("weather_code")
        val maxJson = daily.optJSONArray("temperature_2m_max")
        val minJson = daily.optJSONArray("temperature_2m_min")
        val count = minOf(5, datesJson?.length() ?: 0)
        val dates = ArrayList<String>(count)
        val codes = IntArray(count)
        val highs = DoubleArray(count)
        val lows = DoubleArray(count)
        for (i in 0 until count) {
            dates.add(datesJson!!.optString(i, ""))
            codes[i] = codesJson?.optInt(i, current.optInt("weather_code", 0)) ?: current.optInt("weather_code", 0)
            highs[i] = maxJson?.optDouble(i, t) ?: t
            lows[i] = minJson?.optDouble(i, t) ?: t
        }
        val code = current.optInt("weather_code", 0)
        val change = nextChange(current, json.optJSONObject("hourly"))
        val dailyRain = daily.optJSONArray("precipitation_probability_max")?.optInt(0, 0) ?: 0
        val probability = if (change.second > 0) change.second else dailyRain
        CurData(
            ap.getString("name", null) ?: "București", t, code, current.optInt("is_day", 1) == 1, f,
            dates, codes, highs, lows, current.optDouble("apparent_temperature", t), probability,
            change.first, updatedAt, ageMinutes, ageMinutes >= 60L
        )
    } catch (_: Exception) {
        null
    }
}

private fun nextChange(current: JSONObject, hourly: JSONObject?): Pair<String, Int> {
    val times = hourly?.optJSONArray("time") ?: return "" to 0
    val codes = hourly.optJSONArray("weather_code") ?: return "" to 0
    val probabilities = hourly.optJSONArray("precipitation_probability")
    val now = current.optString("time", "").take(13)
    val currentCode = current.optInt("weather_code", 0)
    var examined = 0
    for (i in 0 until minOf(times.length(), codes.length())) {
        val time = times.optString(i, "")
        if (time <= now) continue
        val code = codes.optInt(i, currentCode)
        val probability = probabilities?.optInt(i, 0)?.coerceIn(0, 100) ?: 0
        val hour = time.substringAfter('T', "").take(5)
        if (probability >= 50 && currentCode !in 51..67 && currentCode !in 80..82 && currentCode < 95) {
            return "Ploaie posibilă ~$hour" to probability
        }
        if (wCat(code) != wCat(currentCode)) {
            val label = when (code) {
                0 -> "Senin"
                1, 2 -> "Parțial noros"
                3 -> "Noros"
                in 45..48 -> "Ceață"
                in 51..67, in 80..82 -> "Ploaie"
                in 71..77, in 85..86 -> "Ninsoare"
                else -> "Furtună"
            }
            return "$label ~$hour" to probability
        }
        examined++
        if (examined >= 10) break
    }
    return "" to 0
}

internal fun fetchCurRaw(ctx: Context): CurData? {
    return try {
        val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        val f = ap.getString("units", "c") == "f"
        val name = ap.getString("name", null) ?: "București"
        val lat = ap.getString("lat", null) ?: "44.4268"
        val lon = ap.getString("lon", null) ?: "26.1025"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&hourly=weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=5&timezone=auto" +
            (if (f) "&temperature_unit=fahrenheit" else "")
        val conn = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
        val json = JSONObject(conn.getInputStream().bufferedReader().readText())
        parseWidgetWeather(ctx, json, System.currentTimeMillis(), 0L)?.let { data ->
            CurData(name, data.t, data.code, data.day, f, data.dates, data.dCode, data.dMax, data.dMin,
                data.feels, data.rainProbability, data.nextChange, data.updatedAt, 0L, false)
        }
    } catch (_: Exception) {
        null
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
