package ro.vremea.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/** Efecte sezoniere desenate direct, fără imagini bitmap externe. */
internal class SeasonalFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leafPath = Path()
    private val rect = RectF()
    private val leafColors = intArrayOf(0xFFD96832.toInt(), 0xFFE3A13A.toInt(), 0xFFB94D35.toInt(), 0xFFCF7A36.toInt())

    fun draw(
        c: Canvas, w: Float, h: Float, t: Float, dtf: Float,
        month: Int, minute: Int, tempC: Float, code: Int, day: Boolean, windX: Float,
        reduceMotion: Boolean, quality: Float,
        leaves: Boolean, frost: Boolean, blossoms: Boolean, pollen: Boolean,
        fireflies: Boolean, dew: Boolean
    ) {
        val motion = if (reduceMotion) 0.06f else 1f
        val countScale = quality.coerceIn(0.35f, 1f)

        if (leaves && month in 8..10 && code !in 71..77) {
            drawLeaves(c, w, h, t * motion, windX, (22 * countScale).toInt().coerceAtLeast(7))
        }
        if (frost && (month in 10..11 || month in 0..2) && tempC <= 2f && minute in 240..630) {
            drawFrost(c, w, h)
        }
        if (blossoms && month in 2..4 && day && code <= 2) {
            drawBlossoms(c, w, h, t * motion, (12 * countScale).toInt().coerceAtLeast(5))
        }
        if (pollen && month in 2..4 && day && code <= 2 && tempC in 12f..29f) {
            drawPollen(c, w, h, t * motion, (20 * countScale).toInt().coerceAtLeast(6))
        }
        if (fireflies && month in 5..7 && !day && code <= 2 && tempC >= 14f) {
            drawFireflies(c, w, h, t * motion, (9 * countScale).toInt().coerceAtLeast(4))
        }
        if (dew && day && minute in 300..570 && tempC in 2f..20f && code <= 2) {
            drawDew(c, w, h, t)
        }
        p.shader = null
        p.clearShadowLayer()
        p.alpha = 255
    }

    private fun drawLeaves(c: Canvas, w: Float, h: Float, t: Float, windX: Float, n: Int) {
        for (i in 0 until n) {
            val phase = (i * 0.618034f + t * (0.025f + (i % 5) * 0.004f)) % 1f
            var x = (i * 0.371f * w + sin(t * 0.65f + i * 1.7f) * w * 0.025f + phase * windX * w * 0.11f) % w
            if (x < 0f) x += w
            val y = h * (0.50f + phase * 0.48f)
            val size = max(4f, w * (0.006f + (i % 4) * 0.0015f))
            val rot = (sin(t * 1.8f + i * 2.1f) * 48f + windX * 24f).toFloat()
            p.color = leafColors[i % leafColors.size]
            p.alpha = 205
            p.style = Paint.Style.FILL
            c.save()
            c.rotate(rot, x, y)
            leafPath.reset()
            leafPath.moveTo(x, y - size)
            leafPath.cubicTo(x + size * 1.15f, y - size * 0.4f, x + size * 0.9f, y + size * 0.55f, x, y + size)
            leafPath.cubicTo(x - size * 0.9f, y + size * 0.55f, x - size * 1.15f, y - size * 0.4f, x, y - size)
            leafPath.close()
            c.drawPath(leafPath, p)
            p.color = Color.argb(100, 92, 54, 35)
            p.strokeWidth = max(0.7f, size * 0.09f)
            p.style = Paint.Style.STROKE
            c.drawLine(x, y - size * 0.65f, x, y + size * 0.65f, p)
            p.style = Paint.Style.FILL
            c.restore()
        }
    }

    private fun drawFrost(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, h * 0.79f, 0f, h,
            intArrayOf(Color.TRANSPARENT, Color.argb(18, 218, 237, 250), Color.argb(70, 226, 241, 250)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.79f, w, h, p)
        p.shader = null
        p.color = Color.argb(112, 225, 242, 252)
        p.strokeWidth = max(1f, w / 800f)
        p.style = Paint.Style.STROKE
        for (i in 0 until 9) {
            val x = w * (0.04f + i * 0.115f)
            val y = h * (0.92f + (i % 3) * 0.018f)
            val len = w * (0.014f + (i % 2) * 0.006f)
            c.drawLine(x, y, x + len, y - len * 1.35f, p)
            c.drawLine(x + len * 0.38f, y - len * 0.52f, x + len * 0.18f, y - len * 0.86f, p)
            c.drawLine(x + len * 0.64f, y - len * 0.86f, x + len * 0.9f, y - len * 0.94f, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawBlossoms(c: Canvas, w: Float, h: Float, t: Float, n: Int) {
        for (i in 0 until n) {
            val phase = (i * 0.754f + t * (0.018f + (i % 4) * 0.003f)) % 1f
            val x = (i * 0.293f * w + sin(t * 0.5f + i) * w * 0.018f) % w
            val y = h * (0.48f + phase * 0.46f)
            val size = max(2.5f, w * (0.0038f + (i % 3) * 0.001f))
            p.color = if (i % 4 == 0) 0xFFE7A7BC.toInt() else 0xFFF2C6D4.toInt()
            p.alpha = 205
            c.save()
            c.rotate(sin(t + i) * 35f, x, y)
            for (petal in 0 until 5) {
                val angle = petal * 72f
                c.save()
                c.rotate(angle, x, y)
                rect.set(x - size * 0.38f, y - size * 1.05f, x + size * 0.38f, y - size * 0.12f)
                c.drawOval(rect, p)
                c.restore()
            }
            p.color = 0xFFFFE19A.toInt()
            c.drawCircle(x, y, size * 0.25f, p)
            c.restore()
        }
    }

    private fun drawPollen(c: Canvas, w: Float, h: Float, t: Float, n: Int) {
        p.color = 0xFFFFE8A4.toInt()
        for (i in 0 until n) {
            val x = (i * 0.417f * w + sin(t * 0.7f + i * 1.4f) * w * 0.035f) % w
            val y = h * (0.38f + ((i * 0.319f + t * (0.012f + (i % 3) * 0.003f)) % 0.57f))
            val pulse = 0.55f + 0.45f * sin(t * 1.5f + i).let { if (it < 0f) -it else it }
            p.alpha = (90f + 100f * pulse).toInt()
            c.drawCircle(x, y, max(1f, w * 0.0018f), p)
        }
    }

    private fun drawFireflies(c: Canvas, w: Float, h: Float, t: Float, n: Int) {
        for (i in 0 until n) {
            val x = w * (0.08f + ((i * 0.379f + sin(t * 0.35f + i) * 0.018f) % 0.84f))
            val y = h * (0.48f + ((i * 0.271f + sin(t * 0.42f + i * 2f) * 0.025f) % 0.46f))
            val pulse = (0.5f + 0.5f * sin(t * 2.1f + i * 1.8f)).coerceIn(0f, 1f)
            p.color = Color.argb((25 + 55 * pulse).toInt(), 190, 255, 137)
            c.drawCircle(x, y, max(4f, w * 0.011f), p)
            p.color = Color.argb((100 + 130 * pulse).toInt(), 230, 255, 171)
            c.drawCircle(x, y, max(1.2f, w * 0.0025f), p)
        }
    }

    private fun drawDew(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 24) {
            val x = w * (0.025f + i * 0.041f)
            val y = h * (0.91f + (i % 4) * 0.018f)
            val pulse = (0.5f + 0.5f * sin(t * 0.45f + i)).coerceIn(0f, 1f)
            p.color = Color.argb((35 + 65 * pulse).toInt(), 224, 244, 255)
            c.drawCircle(x, y, max(1f, w * 0.0017f), p)
        }
    }
}

