package ro.vremea.wallpaper

import android.graphics.*
import java.util.Calendar
import kotlin.math.max
import kotlin.math.sin

// Dealuri pe 3 straturi, cu case (stratul din mijloc) si copaci pe anotimpuri (stratul din fata)
class Hills3D {
    private val bmps = arrayOfNulls<Bitmap>(3)
    private var key = -1L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val maxT = 90
    private val trX = FloatArray(maxT)
    private val trH = FloatArray(maxT)
    private val trV = IntArray(maxT)
    private var trN = 0
    private var month = 0
    private var monthAt = 0L
    private val hsX = floatArrayOf(0.27f, 0.55f, 0.83f)
    private val cSpring = intArrayOf(0xFFF6C6D8.toInt(), 0xFFFFFFFF.toInt(), 0xFFB6E3A0.toInt())
    private val cSummer = intArrayOf(0xFF3E8E41.toInt(), 0xFF4C9A4A.toInt(), 0xFF357A3A.toInt())
    private val cAutumn = intArrayOf(0xFFD9541E.toInt(), 0xFFE8A317.toInt(), 0xFFB8321E.toInt(), 0xFFC9822B.toInt())

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
    )

    private fun curMonth(): Int {
        val now = System.currentTimeMillis()
        if (monthAt == 0L || now - monthAt > 600000L) {
            month = Calendar.getInstance().get(Calendar.MONTH)
            monthAt = now
        }
        return month
    }

    private fun ry(l: Int, x: Float, bhf: Float): Float {
        val baseY = bhf * (0.22f + 0.24f * l)
        val amp = bhf * (0.13f - 0.02f * l)
        val f1 = 0.004f + 0.0015f * l
        val ph = 1.7f * l + 0.6f
        return baseY + amp * (sin(x * f1 + ph) + 0.5f * sin(x * f1 * 2.3f + ph * 1.9f))
    }

    fun draw(
        c: Canvas, paint: Paint, w: Float, h: Float, k: Int, bot: Int, day: Boolean,
        tx: Float, ty: Float, t: Float, wspd: Float, hx: Float, tC: Float
    ) {
        val bw = max(1, (w * 1.2f).toInt())
        val bh = max(1, (h * 0.24f).toInt())
        val kk = (k * 2 + (if (day) 1 else 0)).toLong() * 100000000L + w.toLong() * 10000L + h.toLong()
        if (key != kk) {
            build(bw, bh, k, bot, day)
            genTrees(bw, bh)
            key = kk
        }
        paint.shader = null
        paint.colorFilter = null
        paint.alpha = 255
        val bhf = bh.toFloat()
        val mo = curMonth()
        for (l in 0 until 3) {
            val b = bmps[l] ?: continue
            val par = 0.015f + 0.03f * l
            val ox = tx * w * par - w * 0.1f
            val oy = ty * h * (0.004f + 0.008f * l) + h * 0.02f
            val top = h - bh + oy
            c.drawBitmap(b, ox, top, paint)
            if (l == 1) drawHouses(c, ox, top, bhf, bw, day, tC, t, wspd, hx, bot)
            if (l == 2) drawTrees(c, ox, top, bhf, k, day, mo, t, wspd, hx)
        }
    }

    private fun genTrees(bw: Int, bh: Int) {
        val bwf = bw.toFloat()
        val bhf = bh.toFloat()
        trN = 0
        var x = 30f
        var seed = 7
        while (x < bwf && trN < maxT) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            trX[trN] = x
            trH[trN] = bhf * (0.12f + 0.11f * ((seed % 100) / 100f))
            trV[trN] = (seed / 100) % 12
            trN++
            x += 42f + (seed % 40)
        }
    }

    private fun drawTrees(c: Canvas, ox: Float, oy: Float, bhf: Float, k: Int, day: Boolean, mo: Int, t: Float, wspd: Float, hx: Float) {
        val winter = mo == 11 || mo <= 1
        val spring = mo in 2..4
        val summer = mo in 5..7
        val snow = k == 5
        val night = if (day) 0f else 0.55f
        for (i in 0 until trN) {
            val x = trX[i]
            val th = trH[i]
            val sway = sin(t * 1.5f + x * 0.013f) * (0.8f + 5f * wspd) + hx * wspd * 4f
            c.save()
            c.translate(ox + x, oy + ry(2, x, bhf) + 2f)
            c.rotate(sway)
            p.style = Paint.Style.FILL
            if (trV[i] % 3 == 0) {
                var col = mix(0xFF2D6A3E.toInt(), Color.BLACK, night)
                if (snow) col = mix(col, Color.WHITE, 0.55f)
                p.color = col
                path.reset()
                path.moveTo(0f, -th * 0.7f)
                path.lineTo(th * 0.36f, 0f)
                path.lineTo(-th * 0.36f, 0f)
                path.close()
                c.drawPath(path, p)
                path.reset()
                path.moveTo(0f, -th * 1.1f)
                path.lineTo(th * 0.27f, -th * 0.4f)
                path.lineTo(-th * 0.27f, -th * 0.4f)
                path.close()
                c.drawPath(path, p)
            } else {
                p.color = mix(0xFF5B4030.toInt(), Color.BLACK, night)
                c.drawRect(-th * 0.04f, -th * 0.5f, th * 0.04f, 0f, p)
                if (winter) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = max(1.5f, th * 0.045f)
                    p.strokeCap = Paint.Cap.ROUND
                    c.drawLine(0f, -th * 0.5f, -th * 0.22f, -th * 0.85f, p)
                    c.drawLine(0f, -th * 0.5f, th * 0.02f, -th * 0.98f, p)
                    c.drawLine(0f, -th * 0.5f, th * 0.22f, -th * 0.8f, p)
                    p.style = Paint.Style.FILL
                } else {
                    val v = trV[i]
                    var cc = if (spring) cSpring[v % 3] else if (summer) cSummer[v % 3] else cAutumn[v % 4]
                    cc = mix(cc, Color.BLACK, night)
                    if (snow) cc = mix(cc, Color.WHITE, 0.4f)
                    p.color = cc
                    c.drawCircle(0f, -th * 0.7f, th * 0.26f, p)
                    c.drawCircle(-th * 0.17f, -th * 0.55f, th * 0.2f, p)
                    c.drawCircle(th * 0.18f, -th * 0.58f, th * 0.2f, p)
                }
            }
            c.restore()
        }
    }

    private fun drawHouses(c: Canvas, ox: Float, oy: Float, bhf: Float, bw: Int, day: Boolean, tC: Float, t: Float, wspd: Float, hx: Float, bot: Int) {
        p.style = Paint.Style.FILL
        for (i in 0 until 3) {
            val lx = bw * hsX[i]
            val x = ox + lx
            val by = oy + ry(1, lx, bhf)
            val hw = bhf * 0.085f
            val body = if (day) mix(0xFF9A7B5E.toInt(), bot, 0.30f) else 0xFF22222E.toInt()
            p.color = body
            rect.set(x - hw * 0.8f, by - hw, x + hw * 0.8f, by + 2f)
            c.drawRect(rect, p)
            p.color = if (day) mix(0xFF7A3B30.toInt(), bot, 0.30f) else 0xFF1A1A24.toInt()
            path.reset()
            path.moveTo(x - hw, by - hw)
            path.lineTo(x, by - hw * 1.65f)
            path.lineTo(x + hw, by - hw)
            path.close()
            c.drawPath(path, p)
            p.color = body
            rect.set(x + hw * 0.35f, by - hw * 1.75f, x + hw * 0.6f, by - hw * 1.2f)
            c.drawRect(rect, p)
            if (!day) {
                p.color = 0xFFFFD66B.toInt()
                rect.set(x - hw * 0.5f, by - hw * 0.7f, x - hw * 0.15f, by - hw * 0.35f)
                c.drawRect(rect, p)
                rect.set(x + hw * 0.1f, by - hw * 0.7f, x + hw * 0.45f, by - hw * 0.35f)
                c.drawRect(rect, p)
            }
            if (tC < 10f) {
                for (j in 0 until 4) {
                    val age = (t * 0.22f + j * 0.25f + i * 0.31f) % 1f
                    val sx = x + hw * 0.47f + sin(age * 6f + i) * hw * 0.15f + hx * wspd * age * hw * 2.5f
                    val sy = by - hw * 1.8f - age * hw * 3.2f
                    p.color = Color.argb((110 * (1f - age)).toInt().coerceIn(0, 255), 220, 220, 225)
                    c.drawCircle(sx, sy, hw * (0.1f + 0.28f * age), p)
                }
            }
        }
    }

    private fun build(bw: Int, bh: Int, k: Int, bot: Int, day: Boolean) {
        val ground = when {
            k == 5 -> Color.rgb(236, 241, 249)
            day -> Color.rgb(52, 98, 72)
            else -> Color.rgb(14, 24, 32)
        }
        val bwf = bw.toFloat()
        val bhf = bh.toFloat()
        for (l in 0 until 3) {
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            val q = Paint(Paint.ANTI_ALIAS_FLAG)
            val col = mix(bot, ground, 0.28f + 0.30f * l)
            val baseY = bhf * (0.22f + 0.24f * l)
            val amp = bhf * (0.13f - 0.02f * l)
            val pth = Path()
            val ridge = Path()
            pth.moveTo(0f, bhf)
            var x = 0f
            var first = true
            while (x <= bwf + 12f) {
                val y = ry(l, x, bhf)
                pth.lineTo(x, y)
                if (first) {
                    ridge.moveTo(x, y)
                    first = false
                } else {
                    ridge.lineTo(x, y)
                }
                x += 12f
            }
            pth.lineTo(bwf, bhf)
            pth.close()
            q.shader = LinearGradient(
                0f, baseY - amp, 0f, bhf,
                mix(col, Color.WHITE, 0.16f), mix(col, Color.BLACK, 0.38f), Shader.TileMode.CLAMP
            )
            cv.drawPath(pth, q)
            q.shader = null
            q.style = Paint.Style.STROKE
            q.strokeWidth = 2.5f
            q.color = Color.argb(60, 255, 255, 255)
            cv.drawPath(ridge, q)
            q.style = Paint.Style.FILL
            if (l < 2) {
                q.shader = LinearGradient(
                    0f, baseY, 0f, bhf,
                    Color.argb(80, Color.red(bot), Color.green(bot), Color.blue(bot)),
                    Color.argb(0, Color.red(bot), Color.green(bot), Color.blue(bot)),
                    Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, baseY, bwf, bhf, q)
                q.shader = null
            }
            bmps[l] = b
        }
    }
}
