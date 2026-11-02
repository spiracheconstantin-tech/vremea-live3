package ro.vremea.wallpaper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun notifyX(ctx: Context, id: Int, title: String, text: String) {
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

// Alerta de seara pentru schimbari mari si notificarea "ora de aur"
internal fun runExtra(ctx: Context) {
    val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    val tomorrow = pr.getBoolean("tomorrow", false)
    val golden = pr.getBoolean("golden", false)
    if (!tomorrow && !golden) return
    val lat = pr.getString("lat", null) ?: return
    val lon = pr.getString("lon", null) ?: return
    val name = pr.getString("name", "") ?: ""
    val f = pr.getString("units", "c") == "f"
    val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&daily=weather_code,temperature_2m_max,sunset&forecast_days=3&timezone=auto" +
        (if (f) "&temperature_unit=fahrenheit" else "")
    val j = JSONObject(URL(u).readText())
    val d = j.getJSONObject("daily")
    val off = j.optInt("utc_offset_seconds", 0)
    val locMin = (((System.currentTimeMillis() / 1000L + off) / 60L) % 1440L).toInt()
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val codes = d.getJSONArray("weather_code")
    val mx = d.getJSONArray("temperature_2m_max")
    val suffix = if (name.isNotEmpty()) " · $name" else ""

    if (tomorrow && locMin >= 18 * 60 && locMin <= 21 * 60 && pr.getString("tomorrow_last", "") != today) {
        val msgs = ArrayList<String>()
        val diff = mx.getDouble(1) - mx.getDouble(0)
        val big = if (f) 12.6 else 7.0
        if (diff >= big) {
            msgs.add("🔥 Mâine va fi cu ${diff.roundToInt()}° mai cald")
        } else if (diff <= -big) {
            msgs.add("🥶 Mâine va fi cu ${(-diff).roundToInt()}° mai frig")
        }
        for (i in 1..2) {
            val c = codes.getInt(i)
            val what = if (c >= 95) "furtună" else if ((c in 71..77) || (c in 85..86)) "ninsoare" else if (c == 65 || c == 82) "ploaie puternică" else ""
            if (what.isNotEmpty()) {
                msgs.add((if (i == 1) "Mâine" else "Poimâine") + ": " + what)
                break
            }
        }
        if (msgs.isNotEmpty()) notifyX(ctx, 81, "📅 Ce urmează$suffix", msgs.joinToString(" · "))
        pr.edit().putString("tomorrow_last", today).apply()
    }

    if (golden && pr.getString("golden_last", "") != today && codes.getInt(0) <= 2) {
        val ss = d.getJSONArray("sunset").getString(0)
        val sm = ss.substring(11, 13).toInt() * 60 + ss.substring(14, 16).toInt()
        if (locMin >= sm - 90 && locMin < sm - 60) {
            notifyX(ctx, 82, "📸 Ora de aur$suffix", "Începe în ~" + (sm - 60 - locMin) + " minute. Apusul e la " + ss.substring(11) + ".")
            pr.edit().putString("golden_last", today).apply()
        }
    }
}
