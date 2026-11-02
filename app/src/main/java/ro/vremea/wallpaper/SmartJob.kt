package ro.vremea.wallpaper

import android.app.Activity
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
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class SmartJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val ctx = applicationContext
        Thread {
            try { runSmart(ctx) } catch (e: Exception) { }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

fun scheduleSmart(ctx: Context) {
    try {
        val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (pr.getBoolean("morning", false) || pr.getBoolean("rainsoon", false) || pr.getBoolean("tomorrow", false) || pr.getBoolean("golden", false)) {
            val ji = JobInfo.Builder(4343, ComponentName(ctx, SmartJob::class.java))
                .setPeriodic(30L * 60L * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            js.schedule(ji)
        } else {
            js.cancel(4343)
        }
    } catch (e: Exception) { }
}

fun toggleSmart(a: Activity, key: String, label: String) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val on = !pr.getBoolean(key, false)
    pr.edit().putBoolean(key, on).apply()
    if (on && Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
        a.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
    }
    scheduleSmart(a)
    Toast.makeText(a, label + (if (on) ": pornit" else ": oprit"), Toast.LENGTH_SHORT).show()
}

private fun lbl(code: Int): String = when {
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

private fun post(ctx: Context, id: Int, title: String, text: String) {
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
    b.setSmallIcon(android.R.drawable.ic_dialog_info)
    b.setContentTitle(title)
    b.setContentText(text)
    b.setContentIntent(pi)
    b.setAutoCancel(true)
    nm.notify(id, b.build())
}

private fun runSmart(ctx: Context) {
        try { runExtra(ctx) } catch (e: Exception) { }
    val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    val lat = pr.getString("lat", null) ?: return
    val lon = pr.getString("lon", null) ?: return
    val name = pr.getString("name", "") ?: ""
    val f = pr.getString("units", "c") == "f"
    val unit = if (f) "&temperature_unit=fahrenheit" else ""
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    if (pr.getBoolean("morning", false) && hour in 6..9 && pr.getString("morning_last", "") != today) {
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1&timezone=auto$unit"
        val d = JSONObject(URL(u).readText()).getJSONObject("daily")
        val code = d.getJSONArray("weather_code").getInt(0)
        val mx = d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
        val mn = d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
        val pp = d.getJSONArray("precipitation_probability_max").optInt(0)
        val text = "${lbl(code)}, $mx°/$mn°" + (if (pp >= 30) " · șanse de ploaie $pp%" else "")
        post(ctx, 78, "☀️ Vremea azi" + (if (name.isNotEmpty()) " · $name" else ""), text)
        pr.edit().putString("morning_last", today).apply()
    }

    if (pr.getBoolean("rainsoon", false)) {
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&minutely_15=precipitation&forecast_minutely_15=4&timezone=auto"
        val m = JSONObject(URL(u).readText()).optJSONObject("minutely_15") ?: return
        val pc = m.optJSONArray("precipitation") ?: return
        val dryNow = pc.optDouble(0, 0.0) < 0.1
        var first = -1
        for (i in 1 until pc.length()) {
            if (pc.optDouble(i, 0.0) >= 0.1) {
                first = i
                break
            }
        }
        if (dryNow && first in 1..2) {
            val last = pr.getLong("rain_last", 0L)
            if (System.currentTimeMillis() - last > 2L * 3600L * 1000L) {
                post(ctx, 79, "☔ Începe să plouă" + (if (name.isNotEmpty()) " · $name" else ""), "În aproximativ ${first * 15} de minute")
                pr.edit().putLong("rain_last", System.currentTimeMillis()).apply()
            }
        }
    }
}
