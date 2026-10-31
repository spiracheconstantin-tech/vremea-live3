package ro.vremea.wallpaper

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.*
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WeatherWidgetWeek : AppWidgetProvider() {

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
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidgetWeek::class.java))
            if (ids.isEmpty()) return
            val d = fetchCur(ctx) ?: return
            for (id in ids) {
                val sz = widgetSize(ctx, mgr, id)
                pushWidget(ctx, mgr, id, draw(d, sz[0], sz[1]))
            }
        }

        private fun dayName(date: String, i: Int): String {
            if (i == 0) return "Azi"
            return try {
                val dt = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
                SimpleDateFormat("EEE", Locale("ro")).format(dt!!).replace(".", "").replaceFirstChar { it.uppercase() }
            } catch (e: Exception) {
                ""
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
            val m = wf * 0.05f
            val n = min(5, d.dCode.size)
            val colW = (wf - 2f * m) / max(1, n)

            val nm = if (d.name.length > 22) d.name.take(21) + "…" else d.name
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.textAlign = Paint.Align.LEFT
            wText(c, p, nm, m, hf * 0.17f, hf * 0.095f, wf * 0.55f)
            p.textAlign = Paint.Align.RIGHT
            wText(c, p, "${d.t.roundToInt()}°", wf - m, hf * 0.18f, hf * 0.12f, wf * 0.3f)

            p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            p.textAlign = Paint.Align.CENTER
            for (i in 0 until n) {
                val cx = m + colW * (i + 0.5f)
                wText(c, p, dayName(d.dates[i], i), cx, hf * 0.38f, hf * 0.075f, colW * 0.95f, 220)
                val isz = min(colW * 0.85f, hf * 0.34f)
                c.drawBitmap(weatherBitmap(d.dCode[i], true), null, RectF(cx - isz / 2f, hf * 0.40f, cx + isz / 2f, hf * 0.40f + isz), p)
                wText(c, p, "${d.dMax[i].roundToInt()}°", cx, hf * 0.86f, hf * 0.09f, colW * 0.9f)
                wText(c, p, "${d.dMin[i].roundToInt()}°", cx, hf * 0.96f, hf * 0.07f, colW * 0.9f, 190)
            }
            return bmp
        }
    }
}
