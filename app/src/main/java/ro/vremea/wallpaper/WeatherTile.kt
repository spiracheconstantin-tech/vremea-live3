package ro.vremea.wallpaper

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlin.math.roundToInt

// Buton in panoul rapid: arata temperatura si deschide aplicatia
class WeatherTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val app = applicationContext
        Thread {
            val d = fetchCur(app)
            if (d != null) {
                val num = "${d.t.roundToInt()}"
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = Color.WHITE
                p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                p.textAlign = Paint.Align.CENTER
                p.textSize = if (num.length >= 3) 56f else 78f
                c.drawText(num, 48f, 48f + p.textSize * 0.35f, p)
                Handler(Looper.getMainLooper()).post {
                    val t = qsTile
                    if (t != null) {
                        t.label = num + "° " + d.name
                        t.icon = Icon.createWithBitmap(bmp)
                        t.state = Tile.STATE_ACTIVE
                        t.updateTile()
                    }
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(i)
        }
    }
}
