package ro.vremea.wallpaper

import android.content.Context
import android.graphics.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Zapada care se aduna pe parcursul ninsorii si baltile care raman dupa ploaie
class GroundFx(private val ctx: Context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()
    private var snow = 0f
    private var wet = 0f
    private var at = 0L
    private var loaded = false
    private var lastUpd = 0L
    private var dSnow = 0f
    private var dWet = 0f

    private fun update(code: Int, tC: Float) {
        val nowMs = System.currentTimeMillis()
        val snowing = (code in 71..77) || (code in 85..86)
        val raining = (code in 51..67) || (code in 80..82) || code >= 95
        if (TestMode.read(ctx) != null) {
            dSnow = if (snowing) 0.8f else 0f
            dWet = if (raining) 0.8f else 0f
            return
        }
        val pr = ctx.getSharedPreferences("ground", Context.MODE_PRIVATE)
        if (!loaded) {
            snow = pr.getFloat("snow", 0f)
            wet = pr.getFloat("wet", 0f)
            at = pr.getLong("at", nowMs)
            loaded = true
        }
        val hrs = ((nowMs - at) / 3600000.0).toFloat().coerceIn(0f, 72f)
        val act = min(hrs, 1.5f)
        if (snowing) {
            snow += act * (if (code == 75 || code == 86) 0.35f else 0.18f)
        } else if (tC > 1f) {
            snow -= hrs * (0.04f + 0.03f * min(tC - 1f, 10f))
        } else {
            snow -= hrs * 0.01f
        }
        if (raining && tC > 1f) snow -= act * 0.1f
        if (raining && tC > 0f) {
            wet += act * 0.6f
        } else {
            wet -= hrs * 0.12f
        }
        snow = snow.coerceIn(0f, 1f)
        wet = wet.coerceIn(0f, 1f)
        at = nowMs
        pr.edit().putFloat("snow", snow).putFloat("wet", wet).putLong("at", at).apply()
        dSnow = snow
        dWet = wet
    }

    fun draw(c: Canvas, w: Float, h: Float, t: Float, dtf: Float, code: Int, tC: Float) {
        val nowMs = System.currentTimeMillis()
        if (lastUpd == 0L || nowMs - lastUpd > 20000L) {
            update(code, tC)
            lastUpd = nowMs
        }
        val us = w / 1080f
        val raining = (code in 51..67) || (code in 80..82) || code >= 95

        if (dWet > 0.05f) {
            for (i in 0 until 4) {
                val cx = w * (0.16f + 0.24f * i) + sin(i * 2.1f) * w * 0.03f
                val cy = h * (0.935f + 0.012f * (i % 2))
                val pw = w * (0.07f + 0.04f * (i % 3)) * (0.4f + 0.6f * dWet)
                val ph = pw * 0.16f
                rect.set(cx - pw, cy - ph, cx + pw, cy + ph)
                p.shader = LinearGradient(
                    0f, cy - ph, 0f, cy + ph,
                    Color.argb((150 * dWet).toInt(), 190, 215, 240), Color.argb((150 * dWet).toInt(), 120, 150, 190),
                    Shader.TileMode.CLAMP
                )
                c.drawOval(rect, p)
                p.shader = null
                sp.strokeWidth = max(1f, ph * 0.18f)
                sp.color = Color.argb((110 * dWet).toInt(), 255, 255, 255)
                c.drawLine(cx - pw * 0.5f, cy - ph * 0.3f, cx + pw * 0.1f, cy - ph * 0.3f, sp)
                if (raining) {
                    val a = (t * 0.9f + i * 0.37f) % 1f
                    val rr = pw * (0.2f + 0.7f * a)
                    sp.strokeWidth = max(1f, 1.4f * us)
                    sp.color = Color.argb((160 * (1f - a) * dWet).toInt().coerceIn(0, 255), 235, 245, 255)
                    rect.set(cx - rr, cy - rr * 0.2f, cx + rr, cy + rr * 0.2f)
                    c.drawOval(rect, sp)
                }
            }
        }

        if (dSnow > 0.03f) {
            val topY = h * (0.955f - 0.07f * dSnow)
            path.reset()
            path.moveTo(0f, h)
            path.lineTo(0f, topY)
            var x = 0f
            while (x <= w) {
                x += 24f
                path.lineTo(x, topY + sin(x * 0.011f + 1.3f) * h * 0.008f + sin(x * 0.027f) * h * 0.004f)
            }
            path.lineTo(w, h)
            path.close()
            val a = (235 * dSnow).toInt().coerceIn(0, 255)
            p.shader = LinearGradient(0f, topY - h * 0.01f, 0f, h, Color.argb(a, 250, 252, 255), Color.argb(a, 205, 220, 238), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
            p.shader = null
        }
    }
}
