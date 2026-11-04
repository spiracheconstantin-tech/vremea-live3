package ro.vremea.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/** Reflected sky highlights and small ripples in the foreground lake. */
internal class LakeFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(c: Canvas, w: Float, h: Float, t: Float, day: Boolean, cloudy: Int, wet: Boolean, wind: Float, scene: Int = 0, lightX: Float = .5f) {
        val y0 = if (scene == 2) h * 0.89f else h * 0.925f
        p.shader = android.graphics.LinearGradient(
            0f, y0, 0f, h,
            intArrayOf(Color.argb(0, 75, 135, 175), Color.argb(if (day) 68 else 105, 65, 110, 155), Color.argb(if (day) 115 else 145, 18, 40, 75)),
            floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, y0, w, h, p)
        p.shader = null
        val alpha = (if (day) 52 else 68) * (100 - cloudy.coerceIn(0, 100)) / 100
        for (i in 0 until 16) {
            val yy = y0 + (i + 1) * (h - y0) / 18f
            val phase = t * (0.7f + wind * 1.8f) + i * 1.7f
            val cx = w * lightX + w * 0.08f * sin(phase * 0.22f)
            val span = w * (0.025f + (i % 5) * 0.012f)
            p.color = Color.argb((alpha * (1f - i / 20f)).toInt(), 210, 225, 245)
            p.strokeWidth = max(1f, h / 1200f)
            val ripple = if (wet) 1.8f else 1f
            c.drawLine(cx - span * ripple, yy, cx + span * ripple, yy, p)
        }
        if (wet) {
            p.color = Color.argb(90, 210, 225, 245)
            for (i in 0 until 5) {
                val x = (i * 0.23f + (t * 0.015f) % 1f) * w
                val y = y0 + (0.2f + (i % 3) * 0.22f) * (h - y0)
                rect.set(x - 5f, y - 1f, x + 5f, y + 1f)
                c.drawOval(rect, p)
            }
        }
    }
}

/** A few subtle, fixed planets on clear nights. */
internal class PlanetFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, w: Float, h: Float, t: Float, visible: Boolean) {
        if (!visible) return
        val xy = arrayOf(floatArrayOf(.18f, .16f), floatArrayOf(.78f, .24f), floatArrayOf(.62f, .09f))
        val colors = intArrayOf(0xFFFFD89A.toInt(), 0xFFB9D8FF.toInt(), 0xFFFFB9A0.toInt())
        for (i in xy.indices) {
            val a = (105 + 35 * sin(t * 0.25f + i)).toInt().coerceIn(65, 145)
            val x = xy[i][0] * w
            val y = xy[i][1] * h
            val col = colors[i]
            glow.shader = RadialGradient(x, y, w * .022f, intArrayOf(Color.argb(a / 2, Color.red(col), Color.green(col), Color.blue(col)), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, w * .022f, glow)
            glow.shader = null
            p.color = Color.argb(a, Color.red(col), Color.green(col), Color.blue(col))
            c.drawCircle(x, y, max(1.5f, w / 520f), p)
        }
    }
}

/** Softly lights the cloud layer during the existing lightning flash. */
internal class StormGlowFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, w: Float, h: Float, strength: Float, cloud: Int) {
        if (strength < 0.02f) return
        val a = (strength * (45 + cloud.coerceIn(0, 100)) * 0.72f).toInt().coerceIn(0, 110)
        p.shader = RadialGradient(w * .54f, h * .27f, w * .68f,
            intArrayOf(Color.argb(a, 205, 225, 255), Color.argb(a / 3, 160, 190, 235), 0),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * .72f, p)
        p.shader = null
    }
}

/** Gentle overlay that softens abrupt changes while the weather layers update. */
internal class WeatherTransitionFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var previous = -1
    private var alpha = 0f
    private var tint = Color.TRANSPARENT

    fun draw(c: Canvas, w: Float, h: Float, category: Int, dtf: Float, day: Boolean) {
        if (category != previous) {
            if (previous >= 0) {
                alpha = 0.16f
                tint = if (day) Color.WHITE else 0xFF9BB9EA.toInt()
            }
            previous = category
        }
        if (alpha <= 0.002f) return
        p.color = tint
        p.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        c.drawRect(0f, 0f, w, h, p)
        alpha *= 0.94f.coerceAtLeast(1f - 0.06f * dtf)
        p.alpha = 255
    }
}




