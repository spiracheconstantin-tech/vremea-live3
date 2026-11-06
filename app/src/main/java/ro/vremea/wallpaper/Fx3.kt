package ro.vremea.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private var cachedMonth = 0
private var cachedAt = 0L

internal fun nowMonth(): Int {
    val now = System.currentTimeMillis()
    if (cachedAt == 0L || now - cachedAt > 600000L) {
        cachedMonth = Calendar.getInstance().get(Calendar.MONTH)
        cachedAt = now
    }
    return cachedMonth
}

private fun mixc(a: Int, b: Int, t: Float): Int = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
)

// Aurora boreala: doar decor, pe cer senin, noaptea, toamna si iarna
class AuroraFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sh = arrayOfNulls<Shader>(3)
    private var sk = -1
    private var level = 0f

    fun draw(c: Canvas, w: Float, h: Float, t: Float, dtf: Float, ok: Boolean) {
        val mo = nowMonth()
        val season = mo >= 8 || mo <= 2
        val tgt = if (ok && season) 1f else 0f
        level += (tgt - level) * min(1f, 0.02f * dtf)
        if (level < 0.02f) return
        val len = h * 0.19f
        if (sk != h.toInt()) {
            val cols = intArrayOf(0xFF56E6A4.toInt(), 0xFF43DCCB.toInt(), 0xFF9B83FF.toInt())
            for (l in 0 until 3) {
                val rgb = cols[l]
                sh[l] = LinearGradient(
                    0f, 0f, 0f, len,
                    intArrayOf(Color.argb(0, Color.red(rgb), Color.green(rgb), Color.blue(rgb)),
                        Color.argb(118, Color.red(rgb), Color.green(rgb), Color.blue(rgb)),
                        Color.argb(0, Color.red(rgb), Color.green(rgb), Color.blue(rgb))),
                    floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
                )
            }
            sk = h.toInt()
        }
        c.save()
        c.clipRect(0f, 0f, w, h * 0.28f)
        val steps = 18
        val weights = floatArrayOf(0.55f, 0.39f, 0.27f)
        for (l in 0 until 3) {
            val base = h * (0.035f + 0.018f * l)
            val path = Path()
            fun upper(x: Float): Float = base +
                sin(x / max(1f, w) * (2f * Math.PI.toFloat()) + t * 0.20f + l * 1.6f) * h * 0.014f +
                sin(x / max(1f, w) * (5f * Math.PI.toFloat()) - t * 0.13f) * h * 0.006f
            fun lower(x: Float): Float = base + len * (0.53f + 0.06f * sin(x / max(1f, w) * (2f * Math.PI.toFloat()) - t * 0.16f + l))
            path.moveTo(0f, upper(0f))
            for (i in 1..steps) {
                val x = w * i / steps
                path.lineTo(x, upper(x))
            }
            path.lineTo(w, lower(w))
            for (i in steps - 1 downTo 0) {
                val x = w * i / steps
                path.lineTo(x, lower(x))
            }
            path.close()
            p.shader = sh[l]
            p.alpha = (255f * level * weights[l]).toInt().coerceIn(0, 255)
            c.drawPath(path, p)
        }
        c.restore()
        p.shader = null
        p.alpha = 255
    }
}

// Fluturi vara, ziua, pe vreme buna
class ButterflyFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val n = 5
    private val ph = FloatArray(n) { it * 1.9f + 0.5f }
    private val cols = intArrayOf(0xFFFFA24B.toInt(), 0xFFFFE066.toInt(), 0xFFB48CFF.toInt())
    private var level = 0f

    fun draw(c: Canvas, w: Float, h: Float, t: Float, dtf: Float, ok: Boolean, tx: Float) {
        val mo = nowMonth()
        val season = mo in 4..8
        level += ((if (ok && season) 1f else 0f) - level) * min(1f, 0.03f * dtf)
        if (level < 0.02f) return
        val us = w / 1080f
        for (i in 0 until n) {
            val bx = w * (0.15f + 0.7f * ((sin(t * 0.07f + ph[i]) + 1f) / 2f)) + sin(t * 0.9f + ph[i] * 3f) * 30f * us + tx * w * 0.03f
            val by = h * (0.70f + 0.15f * ((sin(t * 0.05f + ph[i] * 2f) + 1f) / 2f)) + sin(t * 1.7f + ph[i]) * 22f * us
            val flap = abs(sin(t * 9f + ph[i] * 2f))
            val s = 11f * us
            val ww = s * (0.25f + 0.75f * flap) * 1.6f
            val col = cols[i % 3]
            p.color = Color.argb((235 * level).toInt(), Color.red(col), Color.green(col), Color.blue(col))
            rect.set(bx - ww, by - s, bx, by + s * 0.3f)
            c.drawOval(rect, p)
            rect.set(bx, by - s, bx + ww, by + s * 0.3f)
            c.drawOval(rect, p)
            rect.set(bx - ww * 0.7f, by, bx, by + s * 0.9f)
            c.drawOval(rect, p)
            rect.set(bx, by, bx + ww * 0.7f, by + s * 0.9f)
            c.drawOval(rect, p)
            p.color = Color.argb((220 * level).toInt(), 40, 30, 30)
            c.drawRect(bx - s * 0.07f, by - s * 0.8f, bx + s * 0.07f, by + s * 0.7f, p)
        }
    }
}

// Iarba in prim-plan, care se pleaca dupa vant
class GrassFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val rnd = Random(11)
    private val n = 130
    private val gx = FloatArray(n)
    private val gh = FloatArray(n)
    private val gp = FloatArray(n)
    private var gw = 0f
    private var level = 0f

    fun draw(c: Canvas, w: Float, h: Float, t: Float, dtf: Float, ok: Boolean, hx: Float, wspd: Float, day: Boolean, tx: Float) {
        level += ((if (ok) 1f else 0f) - level) * min(1f, 0.03f * dtf)
        if (level < 0.02f) return
        if (gw != w) {
            gw = w
            for (i in 0 until n) {
                gx[i] = (i + rnd.nextFloat() * 0.8f) / n * w * 1.1f - w * 0.05f
                gh[i] = 0.018f + 0.022f * rnd.nextFloat()
                gp[i] = rnd.nextFloat() * 6.28f
            }
        }
        val mo = nowMonth()
        var base = when {
            mo in 2..4 -> 0xFF6DBB4A.toInt()
            mo in 5..7 -> 0xFF3F9A3C.toInt()
            mo in 8..10 -> 0xFF9A8A3A.toInt()
            else -> 0xFF8A7B55.toInt()
        }
        if (!day) base = mixc(base, Color.BLACK, 0.55f)
        val us = w / 1080f
        p.strokeWidth = max(2f, 4.2f * us)
        val by = h + 2f
        val px = tx * w * 0.05f
        val al = (255 * level).toInt().coerceIn(0, 255)
        for (i in 0 until n) {
            val x = gx[i] + px
            val len = gh[i] * h
            val bend = (sin(t * 1.8f + gp[i] + x * 0.01f) * 0.35f + hx * wspd * 0.9f + 0.1f) * len * 0.8f
            path.reset()
            path.moveTo(x, by)
            path.quadTo(x + bend * 0.2f, by - len * 0.6f, x + bend, by - len)
            val col = mixc(base, Color.WHITE, (i % 3) * 0.12f)
            p.color = Color.argb(al, Color.red(col), Color.green(col), Color.blue(col))
            c.drawPath(path, p)
        }
    }
}

// Umbre moi de nori care aluneca peste dealuri
class ShadowFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var bmp: Bitmap? = null
    private var level = 0f

    private fun blob(): Bitmap {
        val old = bmp
        if (old != null) return old
        val b = Bitmap.createBitmap(256, 96, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = RadialGradient(
            128f, 128f, 128f,
            intArrayOf(Color.argb(255, 0, 0, 0), Color.argb(110, 0, 0, 0), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        cv.scale(1f, 96f / 256f)
        cv.drawCircle(128f, 128f, 128f, q)
        bmp = b
        return b
    }

    fun draw(c: Canvas, w: Float, h: Float, t: Float, dtf: Float, ok: Boolean, hx: Float, wspd: Float) {
        level += ((if (ok) 1f else 0f) - level) * min(1f, 0.03f * dtf)
        if (level < 0.02f) return
        val b = blob()
        val sgn = if (hx >= 0f) 1f else -1f
        for (i in 0 until 3) {
            val bw = w * (0.7f + 0.25f * i)
            val bh = bw * 0.22f
            val speed = (0.012f + 0.02f * wspd) * (0.7f + 0.3f * i)
            var pos = (t * speed * sgn + i * 0.37f) % 1.6f
            if (pos < 0f) pos += 1.6f
            val cx = (pos - 0.3f) * w
            val cy = h * (0.80f + 0.05f * i)
            p.alpha = (55 * level).toInt().coerceIn(0, 255)
            rect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
            c.drawBitmap(b, null, rect, p)
        }
        p.alpha = 255
    }
}

