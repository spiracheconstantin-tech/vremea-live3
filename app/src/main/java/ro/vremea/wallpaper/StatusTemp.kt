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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import kotlin.math.roundToInt

class TempJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val ctx = applicationContext
        Thread {
            try { runTemp(ctx) } catch (e: Exception) { }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

fun toggleTemp(a: Activity) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val on = !pr.getBoolean("statustemp", false)
    pr.edit().putBoolean("statustemp", on).apply()
    val js = a.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    val nm = a.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (on) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        }
        try {
            js.schedule(
                JobInfo.Builder(4444, ComponentName(a, TempJob::class.java))
                    .setPeriodic(30L * 60L * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            )
        } catch (e: Exception) { }
        val app = a.applicationContext
        Thread { try { runTemp(app) } catch (e: Exception) { } }.start()
    } else {
        try { js.cancel(4444) } catch (e: Exception) { }
        nm.cancel(80)
    }
    Toast.makeText(a, "Temperatura în bara de stare: " + (if (on) "pornită" else "oprită"), Toast.LENGTH_SHORT).show()
}

private fun lbl2(code: Int): String = when {
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

private fun runTemp(ctx: Context) {
    val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    if (!pr.getBoolean("statustemp", false)) return
    val d = fetchCur(ctx) ?: return
    val num = "${d.t.roundToInt()}"
    val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.WHITE
    p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    p.textAlign = Paint.Align.CENTER
    p.textSize = if (num.length >= 3) 56f else 78f
    c.drawText(num, 48f, 48f + p.textSize * 0.35f, p)

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        nm.createNotificationChannel(NotificationChannel("temp", "Temperatura în bara de stare", NotificationManager.IMPORTANCE_LOW))
    }
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "temp") else Notification.Builder(ctx)
    b.setSmallIcon(Icon.createWithBitmap(bmp))
    b.setContentTitle("$num° · " + lbl2(d.code))
    b.setContentText(d.name)
    b.setContentIntent(pi)
    b.setOngoing(true)
    b.setOnlyAlertOnce(true)
    b.setShowWhen(false)
    nm.notify(80, b.build())
}
