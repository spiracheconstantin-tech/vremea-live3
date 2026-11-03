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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Alerta de polen ridicat, o data pe zi
internal fun runPollen(ctx: Context) {
    val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    if (!pr.getBoolean("pollen", false)) return
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (hour < 7 || hour > 20) return
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    if (pr.getString("pollen_last", "") == today) return
    val lat = pr.getString("lat", null) ?: return
    val lon = pr.getString("lon", null) ?: return
    val name = pr.getString("name", "") ?: ""
    val keys = arrayOf("alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "olive_pollen", "ragweed_pollen")
    val names = arrayOf("Arin", "Mesteacăn", "Iarbă", "Pelin", "Măslin", "Ambrozie")
    val u = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
        "&current=" + keys.joinToString(",") + "&timezone=auto"
    val cur = JSONObject(URL(u).readText()).getJSONObject("current")
    val found = ArrayList<String>()
    for (i in keys.indices) {
        val v = cur.optDouble(keys[i], Double.NaN)
        if (!v.isNaN() && v >= 50.0) {
            found.add(names[i] + " " + v.roundToInt() + (if (v >= 200.0) " (foarte ridicat)" else " (ridicat)"))
        }
    }
    pr.edit().putString("pollen_last", today).apply()
    if (found.isEmpty()) return

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
    b.setContentTitle("🌿 Polen ridicat" + (if (name.isNotEmpty()) " · $name" else ""))
    b.setContentText(found.joinToString(", "))
    b.setContentIntent(pi)
    b.setAutoCancel(true)
    nm.notify(83, b.build())
}
