package ro.vremea.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Geam aburit la ceata sau ploaie rece; atingerea sterge un cerc care se aburește la loc
class FogGlass {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val n = 10
    private val hx = FloatArray(n)
    private val hy = FloatArray(n)
    private val ha = FloatArray(n) { 1f }
    private var hi = 0
    private var level = 0f

    fun tap(x: Float, y: Float) {
        val i = hi
        hi = (hi + 1) % n
        hx[i] = x
        hy[i] = y
        ha[i] = 0f
    }

    fun draw(c: Canvas, w: Float, h: Float, dtf: Float, active: Boolean) {
        level = if (active) min(1f, level + 0.004f * dtf) else max(0f, level - 0.01f * dtf)
        if (level < 0.02f) return
        val sc = c.saveLayer(0f, 0f, w, h, null)
        p.color = Color.argb((115 * level).toInt(), 225, 235, 245)
        c.drawRect(0f, 0f, w, h, p)
        for (i in 0 until n) {
            val a = ha[i]
            if (a >= 1f) continue
            val r = w * (0.11f + 0.04f * a)
            val s = ((1f - a) * 255f * level).toInt().coerceIn(0, 255)
            hp.shader = RadialGradient(
                hx[i], hy[i], r,
                intArrayOf(Color.argb(s, 255, 255, 255), Color.argb(s, 255, 255, 255), 0),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(hx[i], hy[i], r, hp)
            ha[i] = a + 0.0018f * dtf
        }
        hp.shader = null
        c.restoreToCount(sc)
    }
}

// Un stol de pasari traverseaza cerul ziua, pe vreme fara precipitatii
class BirdFx {
    private val rnd = Random(System.nanoTime())
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var next = 8f
    private var t0 = -100f
    private var dir = 1f
    private var yb = 0.15f
    private val dur = 16f
    private val ox = floatArrayOf(0f, -1f, -1f, -2f, -2f)
    private val oy = floatArrayOf(0f, -0.6f, 0.6f, -1.2f, 1.2f)

    fun draw(c: Canvas, w: Float, h: Float, t: Float, ok: Boolean, hx: Float) {
        if (!ok) return
        if (t >= next && t - t0 > dur) {
            t0 = t
            dir = if (hx >= 0f) 1f else -1f
            if (rnd.nextFloat() < 0.3f) dir = -dir
            yb = 0.1f + 0.12f * rnd.nextFloat()
            next = t + dur + 15f + 25f * rnd.nextFloat()
        }
        val f = (t - t0) / dur
        if (f < 0f || f > 1f) return
        val s = w * 0.028f
        val x = if (dir > 0f) -0.12f * w + f * 1.3f * w else 1.12f * w - f * 1.3f * w
        val y = h * (yb + 0.05f * sin(f * 5f))
        sp.strokeWidth = max(1.5f, w * 0.0024f)
        sp.color = Color.argb(190, 35, 40, 55)
        for (i in 0 until 5) {
            val bx = x + ox[i] * s * dir * 1.6f
            val by = y + oy[i] * s * 1.2f
            val fl = sin(t * 9f + i * 0.9f)
            val b = s * 0.5f
            c.drawLine(bx - b, by - b * 0.55f * fl, bx, by, sp)
            c.drawLine(bx, by, bx + b, by - b * 0.55f * fl, sp)
        }
    }
}
