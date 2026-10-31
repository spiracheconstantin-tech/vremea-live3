package ro.vremea.wallpaper

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.*
import android.os.Bundle
import kotlin.math.min
import kotlin.math.roundToInt

class WeatherWidgetSmall : AppWidgetProvider() {

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

        private fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidgetSmall::class.java))
            if (ids.isEmpty()) return
            val d = fetchCur(ctx) ?: return
            for (id in ids) {
                val sz = widgetSize(ctx, mgr, id)
                pushWidget(ctx, mgr, id, draw(d, sz[0], sz[1]))
            }
        }

        private fun draw(d: CurData, W: Int, H: Int): Bitmap {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val tc = (if (d.f) (d.t - 32.0) * 5.0 / 9.0 else d.t).toFloat()
            widgetBg(c, W, H, d.code, d.day, tc)
            val wf = W.toFloat()
            val hf = H.toFloat()
            val base = min(wf, hf)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val isz = base * 0.50f
            c.drawBitmap(
                weatherBitmap(d.code, d.day), null,
                RectF((wf - isz) / 2f, hf * 0.06f, (wf + isz) / 2f, hf * 0.06f + isz), p
            )
            p.textAlign = Paint.Align.CENTER
            p.setShadowLayer(4f, 0f, 2f, 0x66000000)
            p.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            wText(c, p, "${d.t.roundToInt()}°", wf / 2f, hf * 0.80f, base * 0.26f, wf * 0.9f)
            p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val nm = if (d.name.length > 18) d.name.take(17) + "…" else d.name
            wText(c, p, nm, wf / 2f, hf * 0.94f, base * 0.095f, wf * 0.9f, 230)
            return bmp
        }
    }
}
