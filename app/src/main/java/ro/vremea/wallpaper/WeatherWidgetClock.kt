package ro.vremea.wallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.min
import kotlin.math.roundToInt

class WeatherWidgetClock : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        Thread {
            try { updateAll(context) } catch (e: Exception) { } finally { pr.finish() }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        refreshAll(context)
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val app = ctx.applicationContext
            Thread { try { updateAll(app) } catch (e: Exception) { } }.start()
        }

        private fun lab(code: Int): String = when {
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

        private fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidgetClock::class.java))
            if (ids.isEmpty()) return
            val d = fetchCur(ctx) ?: return
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val sz = widgetSize(ctx, mgr, id)
                val rv = RemoteViews(ctx.packageName, R.layout.widget_clock)
                rv.setImageViewBitmap(R.id.img, draw(d, sz[0], sz[1]))
                rv.setOnClickPendingIntent(R.id.img, pi)
                mgr.updateAppWidget(id, rv)
            }
        }

        private fun draw(d: CurData, W: Int, H: Int): Bitmap {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val tc = (if (d.f) (d.t - 32.0) * 5.0 / 9.0 else d.t).toFloat()
            widgetBg(c, W, H, d.code, d.day, tc)
            val wf = W.toFloat()
            val hf = H.toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val m = hf * 0.08f
            val isz = min(hf * 0.52f, wf * 0.30f)
            val cx = wf - m - isz / 2f
            c.drawBitmap(weatherBitmap(d.code, d.day), null, RectF(cx - isz / 2f, hf * 0.05f, cx + isz / 2f, hf * 0.05f + isz), p)
            p.textAlign = Paint.Align.CENTER
            p.setShadowLayer(4f, 0f, 2f, 0x66000000)
            p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            wText(c, p, "${d.t.roundToInt()}°", cx, hf * 0.80f, hf * 0.22f, isz * 1.3f)
            p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            wText(c, p, lab(d.code), cx, hf * 0.94f, hf * 0.08f, isz * 1.6f, 230)
            return bmp
        }
    }
}
