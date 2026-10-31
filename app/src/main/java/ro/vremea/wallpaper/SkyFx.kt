package ro.vremea.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Trecere lina intre culorile cerului cand se schimba vremea sau ora
class SkyFade {
    private var ct = 0
    private var cb = 0
    private var started = false
    var changing = false

    private fun ch(a: Int, b: Int, k: Float): Int {
        val diff = b - a
        if (diff == 0) return a
        var s = diff * k
        if (abs(s) < 1f) s = if (diff > 0) 1f else -1f
        val n = a + s.toInt()
        return if (diff > 0) min(n, b) else max(n, b)
    }

    private fun mix(a: Int, b: Int, k: Float): Int =
        Color.rgb(
            ch(Color.red(a), Color.red(b), k),
            ch(Color.green(a), Color.green(b), k),
            ch(Color.blue(a), Color.blue(b), k)
        )

    fun colors(top: Int, bot: Int, dtf: Float): IntArray {
        if (!started) {
            ct = top
            cb = bot
            started = true
        }
        changing = ct != top || cb != bot
        val k = min(1f, 0.05f * dtf)
        ct = mix(ct, top, k)
        cb = mix(cb, bot, k)
        return intArrayOf(ct, cb)
    }
}

// Stele cazatoare in perioadele reale ale roiurilor de meteori
class MeteorFx {
    private val rnd = Random(System.nanoTime())
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val n = 6
    private val mx = FloatArray(n)
    private val my = FloatArray(n)
    private val mvx = FloatArray(n)
    private val mvy = FloatArray(n)
    private val ml = FloatArray(n) { 1f }
    private var next = 0f
    private var mo = 0
    private var dd = 1
    private var at = 0L

    private fun rate(): Float {
        val now = System.currentTimeMillis()
        if (at == 0L || now - at > 600000L) {
            val cal = Calendar.getInstance()
            mo = cal.get(Calendar.MONTH)
            dd = cal.get(Calendar.DAY_OF_MONTH)
            at = now
        }
        return when {
            mo == 7 && dd in 9..15 -> 2.5f
            mo == 11 && dd in 11..16 -> 2.5f
            mo == 0 && dd in 2..5 -> 4f
            mo == 10 && dd in 16..19 -> 5f
            mo == 3 && dd in 21..24 -> 6f
            else -> 0f
        }
    }

    fun draw(c: Canvas, w: Float, h: Float, t: Float, dtf: Float, night: Boolean, clear: Boolean) {
        if (!night || !clear) return
        val r = rate()
        if (r <= 0f) return
        val us = w / 1080f
        if (t >= next) {
            var i = 0
            while (i < n && ml[i] < 1f) i++
            if (i < n) {
                mx[i] = w * (0.25f + 0.75f * rnd.nextFloat())
                my[i] = h * (0.03f + 0.27f * rnd.nextFloat())
                mvx[i] = -w * (0.45f + 0.5f * rnd.nextFloat())
                mvy[i] = h * (0.12f + 0.15f * rnd.nextFloat())
                ml[i] = 0f
            }
            next = t + r * (0.5f + rnd.nextFloat())
        }
        val dt = dtf / 30f
        for (i in 0 until n) {
            if (ml[i] >= 1f) continue
            mx[i] += mvx[i] * dt
            my[i] += mvy[i] * dt
            ml[i] += dt * 1.1f
            val a = sin(PI * ml[i].coerceIn(0f, 1f)).toFloat()
            sp.strokeWidth = max(1.5f, 2.6f * us)
            sp.color = Color.argb((230 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawLine(mx[i], my[i], mx[i] - mvx[i] * 0.14f, my[i] - mvy[i] * 0.14f, sp)
            hp.color = Color.argb((255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle(mx[i], my[i], max(1.5f, 2.6f * us), hp)
        }
    }
}
