package ro.vremea.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun txt(a: Activity, s: String, size: Float, alpha: Float = 1f, bold: Boolean = false): TextView {
    val v = TextView(a)
    v.text = s
    v.textSize = size
    v.setTextColor(Color.WHITE)
    v.alpha = alpha
    if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
    return v
}

private fun card(a: Activity): LinearLayout {
    val d = a.resources.displayMetrics.density
    val l = LinearLayout(a)
    l.orientation = LinearLayout.VERTICAL
    val bg = GradientDrawable()
        bg.orientation = GradientDrawable.Orientation.TL_BR
    bg.setColors(intArrayOf(0x55FFFFFF, 0x14FFFFFF))
    bg.setStroke(max(1, (1 * d).toInt()), 0x44FFFFFF)
    bg.cornerRadius = 18f * d
    l.background = bg
    l.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
    return l
}

// ---------- trage in jos ca sa reimprospatezi ----------
fun attachPull(a: Activity, scroll: ScrollView, onRefresh: () -> Unit) {
    var startY = -1f
    var done = false
    val th = 110f * a.resources.displayMetrics.density
    scroll.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = if (scroll.scrollY == 0) ev.rawY else -1f
                done = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (startY < 0f && scroll.scrollY == 0) startY = ev.rawY
                if (startY >= 0f && !done && scroll.scrollY == 0 && ev.rawY - startY > th) {
                    done = true
                    Toast.makeText(a, "⟳ Se reîmprospătează…", Toast.LENGTH_SHORT).show()
                    onRefresh()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                startY = -1f
                done = false
            }
        }
        false
    }
}

// ---------- grafic de temperatura ----------
class TempChart(ctx: Context, private val t: DoubleArray, private val hrs: IntArray, private val pop: IntArray) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val n = t.size
        if (n < 2) return
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val l = 16f * d
        val r = 16f * d
        val top = 28f * d
        val bot = 40f * d
        var lo = t[0]
        var hi = t[0]
        for (v in t) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
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

        val path = Path()
        path.moveTo(px(0), py(t[0]))
        for (i in 1 until n) {
            val x0 = px(i - 1)
            val y0 = py(t[i - 1])
            val x1 = px(i)
            val y1 = py(t[i])
            val mx = (x0 + x1) / 2f
            path.cubicTo(mx, y0, mx, y1, x1, y1)
        }
        val fill = Path(path)
        fill.lineTo(px(n - 1), top + ch)
        fill.lineTo(px(0), top + ch)
        fill.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, top, 0f, top + ch, Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawPath(fill, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * d
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.WHITE
        c.drawPath(path, p)

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        var i = 0
        while (i < n) {
            val x = px(i)
            val y = py(t[i])
            p.color = Color.WHITE
            c.drawCircle(x, y, 3.5f * d, p)
            p.textSize = 12f * d
            c.drawText("${t[i].roundToInt()}°", x, y - 9f * d, p)
            p.textSize = 11f * d
            p.color = Color.argb(200, 255, 255, 255)
            c.drawText("%02d:00".format(hrs[i]), x, h - 5f * d, p)
            val ph = pop[i] / 100f * 14f * d
            p.color = Color.argb(160, 120, 190, 255)
            c.drawRect(x - 5f * d, h - 20f * d - ph, x + 5f * d, h - 20f * d, p)
            i += 3
        }
    }
}

fun addChart(a: Activity, body: LinearLayout, j: JSONObject) {
    try {
        WeatherWidget.refreshAll(a)
        val hr = j.getJSONObject("hourly")
        val times = hr.getJSONArray("time")
        val tt = hr.getJSONArray("temperature_2m")
        val pp = hr.optJSONArray("precipitation_probability")
        val nowH = j.getJSONObject("current").getString("time").substring(0, 13)
        var s = 0
        while (s < times.length() - 1 && times.getString(s).substring(0, 13) < nowH) s++
        val n = min(24, times.length() - s)
        if (n < 2) return
        val t = DoubleArray(n) { tt.getDouble(s + it) }
        val hrs = IntArray(n) { times.getString(s + it).substring(11, 13).toInt() }
        val pop = IntArray(n) { if (pp != null) pp.optInt(s + it) else 0 }
        val d = a.resources.displayMetrics.density
        val box = card(a)
        box.addView(txt(a, "📈 Temperatura în următoarele 24 de ore", 14f, 0.85f))
        box.addView(TempChart(a, t, hrs, pop), LinearLayout.LayoutParams(-1, (190 * d).toInt()))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = (12 * d).toInt()
        body.addView(box, lp)
    } catch (e: Exception) { }
}

// ---------- calitatea aerului si polen ----------
fun addAir(a: Activity, body: LinearLayout, lat: String, lon: String) {
    if (lat.isEmpty() || lon.isEmpty()) return
    Thread {
        try {
            val u = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                "&current=european_aqi,pm10,pm2_5,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen&timezone=auto"
            val cur = JSONObject(URL(u).readText()).getJSONObject("current")
            val aqi = cur.optDouble("european_aqi", Double.NaN)
            val pm25 = cur.optDouble("pm2_5", Double.NaN)
            val pm10 = cur.optDouble("pm10", Double.NaN)
            val names = arrayOf("Arin", "Mesteacăn", "Iarbă", "Pelin", "Măslin", "Ambrozie")
            val keys = arrayOf("alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "olive_pollen", "ragweed_pollen")
            val pol = ArrayList<String>()
            var anyPollen = false
            for (i in keys.indices) {
                val v = cur.optDouble(keys[i], Double.NaN)
                if (v.isNaN()) continue
                anyPollen = true
                if (v >= 1.0) {
                    val lvl = if (v < 10) "scăzut" else if (v < 50) "moderat" else if (v < 200) "ridicat" else "foarte ridicat"
                    pol.add("${names[i]} ${v.roundToInt()} ($lvl)")
                }
            }
            a.runOnUiThread {
                val now = a.getSharedPreferences("app", Context.MODE_PRIVATE).getString("lat", "")
                if (now == lat && !aqi.isNaN()) {
                    val d = a.resources.displayMetrics.density
                    val dot = if (aqi <= 20) "🟢" else if (aqi <= 40) "🟡" else if (aqi <= 60) "🟠" else if (aqi <= 80) "🔴" else "🟣"
                    val cat = if (aqi <= 20) "Foarte bună" else if (aqi <= 40) "Bună" else if (aqi <= 60) "Moderată" else if (aqi <= 80) "Slabă" else if (aqi <= 100) "Foarte slabă" else "Extrem de slabă"
                    val box = card(a)
                    box.tag = "air"
                    box.addView(txt(a, "🌬️ Calitatea aerului", 14f, 0.85f))
                    box.addView(txt(a, "$dot AQI ${aqi.roundToInt()} · $cat", 22f, 1f, true))
                    if (!pm25.isNaN() && !pm10.isNaN()) {
                        box.addView(txt(a, "PM2.5 ${pm25.roundToInt()} µg/m³  ·  PM10 ${pm10.roundToInt()} µg/m³", 13f, 0.85f))
                    }
                    if (anyPollen) {
                        val line = if (pol.isEmpty()) "🌿 Polen: scăzut" else "🌿 Polen: " + pol.joinToString(", ")
                        box.addView(txt(a, line, 13f, 0.9f))
                    }
                    val old = body.findViewWithTag<View>("air")
                    if (old != null) body.removeView(old)
                    val lp = LinearLayout.LayoutParams(-1, -2)
                    lp.topMargin = (12 * d).toInt()
                    body.addView(box, max(0, body.childCount - 1), lp)
                }
            }
        } catch (e: Exception) { }
    }.start()
}

// ---------- setari fundal animat ----------
fun wallpaperSettings(a: Activity) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val d = a.resources.displayMetrics.density
    val box = LinearLayout(a)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding((20 * d).toInt(), (12 * d).toInt(), (20 * d).toInt(), 0)
    val rg = RadioGroup(a)
    val names = arrayOf("Economic (consum redus, 30 fps)", "Normal", "Maxim (mai multe picături)")
    val keys = arrayOf("eco", "normal", "max")
    val cur = pr.getString("wp_quality", "normal")
    for (i in 0..2) {
        val rb = RadioButton(a)
        rb.text = names[i]
        rb.id = 100 + i
        rg.addView(rb)
        if (keys[i] == cur) rg.check(100 + i)
    }
    box.addView(rg)
    val cbH = CheckBox(a)
    cbH.text = "Dealuri"
    cbH.isChecked = pr.getBoolean("wp_hills", true)
    val cbS = CheckBox(a)
    cbS.text = "Stele (noaptea)"
    cbS.isChecked = pr.getBoolean("wp_stars", true)
    box.addView(cbH)
    box.addView(cbS)
    AlertDialog.Builder(a).setTitle("Setări fundal animat").setView(box)
        .setPositiveButton("Salvează") { _, _ ->
            val sel = (rg.checkedRadioButtonId - 100).coerceIn(0, 2)
            pr.edit().putString("wp_quality", keys[sel]).putBoolean("wp_hills", cbH.isChecked).putBoolean("wp_stars", cbS.isChecked).apply()
            Toast.makeText(a, "Salvat. Se aplică la următoarea afișare a fundalului.", Toast.LENGTH_LONG).show()
        }
        .setNegativeButton("Anulează", null).show()
}

// ---------- alerte pentru vreme periculoasa ----------
fun scheduleAlerts(ctx: Context) {
    try {
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val ji = JobInfo.Builder(4242, ComponentName(ctx, AlertJob::class.java))
            .setPeriodic(3L * 60L * 60L * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        js.schedule(ji)
    } catch (e: Exception) { }
}

fun toggleAlerts(a: Activity) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val on = !pr.getBoolean("alerts", false)
    pr.edit().putBoolean("alerts", on).apply()
    if (on) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        }
        scheduleAlerts(a)
        Toast.makeText(a, "🔔 Alerte pornite (verificare la ~3 ore)", Toast.LENGTH_LONG).show()
    } else {
        try {
            (a.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(4242)
        } catch (e: Exception) { }
        Toast.makeText(a, "Alerte oprite", Toast.LENGTH_SHORT).show()
    }
}

class AlertJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val ctx = applicationContext
        Thread {
            try { runAlert(ctx) } catch (e: Exception) { }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

private fun runAlert(ctx: Context) {
    val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    if (!pr.getBoolean("alerts", false)) return
    val lat = pr.getString("lat", null) ?: return
    val lon = pr.getString("lon", null) ?: return
    val name = pr.getString("name", "") ?: ""
    val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&hourly=temperature_2m,weather_code,precipitation,wind_gusts_10m&forecast_hours=12&timezone=auto"
    val hr = JSONObject(URL(u).readText()).getJSONObject("hourly")
    val tt = hr.getJSONArray("temperature_2m")
    val wc = hr.getJSONArray("weather_code")
    val pc = hr.getJSONArray("precipitation")
    val gu = hr.getJSONArray("wind_gusts_10m")
    var maxT = -99.0
    var minT = 99.0
    var maxG = 0.0
    var maxP = 0.0
    var storm = false
    var snow = false
    for (i in 0 until tt.length()) {
        maxT = max(maxT, tt.optDouble(i, -99.0))
        minT = min(minT, tt.optDouble(i, 99.0))
        maxG = max(maxG, gu.optDouble(i, 0.0))
        maxP = max(maxP, pc.optDouble(i, 0.0))
        val c = wc.optInt(i, 0)
        if (c >= 95) storm = true
        if (c == 75 || c == 86) snow = true
    }
    val msgs = ArrayList<String>()
    if (storm) msgs.add("⛈️ Furtună în următoarele ore")
    if (maxG >= 70) msgs.add("💨 Rafale de până la ${maxG.roundToInt()} km/h")
    if (maxP >= 10) msgs.add("🌧️ Ploaie torențială (${maxP.roundToInt()} mm/oră)")
    if (snow) msgs.add("❄️ Ninsoare puternică")
    if (maxT >= 35) msgs.add("🥵 Caniculă: ${maxT.roundToInt()}°C")
    if (minT <= -10) msgs.add("🥶 Ger: ${minT.roundToInt()}°C")
    if (msgs.isEmpty()) return
    val key = msgs.joinToString("|") + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    if (pr.getString("alert_last", "") == key) return
    pr.edit().putString("alert_last", key).apply()

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        nm.createNotificationChannel(NotificationChannel("alerte", "Alerte vreme", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "alerte") else Notification.Builder(ctx)
    b.setSmallIcon(android.R.drawable.ic_dialog_alert)
    b.setContentTitle("⚠️ Vreme periculoasă" + (if (name.isNotEmpty()) " · $name" else ""))
    b.setContentText(msgs.joinToString("  "))
    b.setStyle(Notification.BigTextStyle().bigText(msgs.joinToString("\n")))
    b.setContentIntent(pi)
    b.setAutoCancel(true)
    nm.notify(77, b.build())
}
