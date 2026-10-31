package ro.vremea.wallpaper

import android.graphics.*
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Efecte de sezon si de viata pentru fundalul animat. Foloseste propriile pensule.
class LifeFx {
    private val rnd = Random(System.nanoTime())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val path = Path()
    private var month = 0
    private var monthAt = 0L
    private var gw = 0f
    private var gh = 0f

    // frunze (toamna) si petale (primavara)
    private val np = 18
    private val px = FloatArray(np)
    private val py = FloatArray(np)
    private val pvy = FloatArray(np)
    private val prot = FloatArray(np)
    private val pvr = FloatArray(np)
    private val psz = FloatArray(np)
    private val pfl = FloatArray(np)
    private val pcol = IntArray(np)

    // licurici
    private val nf = 16
    private val fbx = FloatArray(nf)
    private val fby = FloatArray(nf)
    private val fph = FloatArray(nf)

    // cristale de chiciura
    private val ncr = 14
    private val crx = FloatArray(ncr)
    private val cry = FloatArray(ncr)
    private val crl = FloatArray(ncr)
    private var frostShader: Shader? = null
    private var frostKey = 0L

    // unde la atingere
    private val nr = 6
    private val rx = FloatArray(nr)
    private val ry = FloatArray(nr)
    private val ra = FloatArray(nr) { 1f }
    private var rpi = 0

    private var glowBmp: Bitmap? = null
    private var blobBmp: Bitmap? = null

    private fun curMonth(): Int {
        val now = System.currentTimeMillis()
        if (monthAt == 0L || now - monthAt > 600000L) {
            month = Calendar.getInstance().get(Calendar.MONTH)
            monthAt = now
        }
        return month
    }

    fun tap(x: Float, y: Float) {
        val i = rpi
        rpi = (rpi + 1) % nr
        rx[i] = x
        ry[i] = y
        ra[i] = 0.0001f
    }

    private fun glow(): Bitmap {
        val old = glowBmp
        if (old != null) return old
        val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = RadialGradient(
            24f, 24f, 24f,
            intArrayOf(Color.argb(255, 255, 250, 170), Color.argb(130, 200, 255, 120), 0),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(b).drawCircle(24f, 24f, 24f, q)
        glowBmp = b
        return b
    }

    private fun blob(): Bitmap {
        val old = blobBmp
        if (old != null) return old
        val b = Bitmap.createBitmap(256, 96, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = RadialGradient(
            128f, 128f, 128f,
            intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(120, 255, 255, 255), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        cv.scale(1f, 96f / 256f)
        cv.drawCircle(128f, 128f, 128f, q)
        blobBmp = b
        return b
    }

    private fun respawn(i: Int, w: Float, h: Float, mo: Int, scatter: Boolean) {
        px[i] = rnd.nextFloat() * w
        py[i] = if (scatter) rnd.nextFloat() * h else -20f - rnd.nextFloat() * h * 0.2f
        pvy[i] = 1.6f + rnd.nextFloat() * 1.8f
        prot[i] = rnd.nextFloat() * 360f
        pvr[i] = (rnd.nextFloat() - 0.5f) * 5f
        psz[i] = 14f + rnd.nextFloat() * 12f
        pfl[i] = rnd.nextFloat() * 6.28f
        val autumn = mo in 8..10
        val a = intArrayOf(0xFFD9541E.toInt(), 0xFFE8A317.toInt(), 0xFFB8321E.toInt(), 0xFFC9822B.toInt())
        val s = intArrayOf(0xFFFFC9DC.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFB3CE.toInt())
        pcol[i] = if (autumn) a[rnd.nextInt(a.size)] else s[rnd.nextInt(s.size)]
    }

    private fun initFx(w: Float, h: Float) {
        val mo = curMonth()
        for (i in 0 until np) respawn(i, w, h, mo, true)
        for (i in 0 until nf) {
            fbx[i] = rnd.nextFloat() * w
            fby[i] = h * (0.55f + 0.35f * rnd.nextFloat())
            fph[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until ncr) {
            crx[i] = if (rnd.nextBoolean()) rnd.nextFloat() * 0.15f else 1f - rnd.nextFloat() * 0.15f
            cry[i] = rnd.nextFloat()
            crl[i] = 10f + rnd.nextFloat() * 14f
        }
    }

    fun draw(
        c: Canvas, w: Float, h: Float, t: Float, dtf: Float, k: Int, day: Boolean, tC: Float,
        hx: Float, wspd: Float, nowMin: Int, sunrise: Int, sunset: Int, tx: Float, ty: Float
    ) {
        if (w != gw || h != gh) {
            gw = w
            gh = h
            frostShader = null
            initFx(w, h)
        }
        val mo = curMonth()
        val us = w / 1080f
        val calm = k <= 2
        val ox = tx * w * 0.02f

        // ceata de dimineata, care se ridica din vale
        val pos = (nowMin - (sunrise - 40)) / 150f
        if (calm && pos >= 0f && pos <= 1f) {
            val s = sin(PI * pos).toFloat()
            val bm = blob()
            for (i in 0 until 3) {
                val bw = w * (0.9f + 0.2f * i)
                val bh = bw * 0.28f
                val cx = w * (0.25f + 0.25f * i) + sin(t * 0.06f + i * 2f) * w * 0.12f + ox * (1 + i)
                val cy = h * (0.70f + 0.04f * i)
                p.alpha = (110 * s).toInt().coerceIn(0, 255)
                rect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
                c.drawBitmap(bm, null, rect, p)
            }
            p.alpha = 255
        }

        // frunze toamna, petale primavara
        val autumn = mo in 8..10
        val spring = mo in 2..4
        if (calm && (autumn || spring)) {
            for (i in 0 until np) {
                py[i] += pvy[i] * us * dtf * 1.5f
                px[i] += (hx * (0.8f + wspd * 4f) + sin(t * 1.3f + i) * 0.9f) * us * dtf * 1.5f
                prot[i] += pvr[i] * dtf
                if (py[i] > h + 30f || px[i] > w + 40f || px[i] < -40f) respawn(i, w, h, mo, false)
                val s = psz[i] * us * (if (spring) 0.55f else 1f)
                c.save()
                c.translate(px[i] + ox * 2f, py[i])
                c.rotate(prot[i])
                c.scale(1f, 0.35f + 0.65f * abs(cos(t * 1.8f + pfl[i])))
                p.color = pcol[i]
                if (autumn) {
                    rect.set(-s, -s * 0.55f, s, s * 0.55f)
                    c.drawOval(rect, p)
                    sp.color = Color.argb(120, 90, 50, 20)
                    sp.strokeWidth = max(1f, s * 0.08f)
                    c.drawLine(-s, 0f, s, 0f, sp)
                } else {
                    rect.set(-s * 0.7f, -s * 0.4f, s * 0.7f, s * 0.4f)
                    c.drawOval(rect, p)
                }
                c.restore()
            }
        }

        // licurici si un liliac, vara, noaptea
        if (!day && calm && mo in 4..8) {
            val g = glow()
            for (i in 0 until nf) {
                val x = fbx[i] + sin(t * (0.3f + 0.1f * (i % 3)) + fph[i]) * w * 0.06f + ox * 3f
                val y = fby[i] + sin(t * (0.4f + 0.1f * (i % 4)) + fph[i] * 1.7f) * h * 0.02f
                val a = (0.5f + 0.5f * sin(t * (1.2f + 0.2f * (i % 3)) + fph[i] * 3f)).pow(3f)
                p.alpha = (255 * a).toInt().coerceIn(0, 255)
                val r = (14f + 10f * a) * us
                rect.set(x - r, y - r, x + r, y + r)
                c.drawBitmap(g, null, rect, p)
            }
            p.alpha = 255
            val cyc = t % 28f
            if (cyc < 9f) {
                val f = cyc / 9f
                val dir = if (((t / 28f).toInt() % 2) == 0) 1f else -1f
                val x = if (dir > 0f) -0.1f * w + f * 1.2f * w else 1.1f * w - f * 1.2f * w
                val y = h * (0.16f + 0.1f * sin(f * 6f))
                val s = w * 0.035f
                val fl = sin(t * 16f) * 0.6f
                c.save()
                c.translate(x, y)
                if (dir < 0f) c.scale(-1f, 1f)
                path.reset()
                path.moveTo(0f, 0f)
                path.quadTo(s * 0.6f, -s * (0.5f + fl), s * 1.6f, -s * (0.1f + fl * 0.5f))
                path.quadTo(s * 1.1f, s * 0.1f, s * 0.7f, s * 0.3f)
                path.quadTo(s * 0.4f, s * 0.1f, 0f, s * 0.35f)
                path.quadTo(-s * 0.4f, s * 0.1f, -s * 0.7f, s * 0.3f)
                path.quadTo(-s * 1.1f, s * 0.1f, -s * 1.6f, -s * (0.1f + fl * 0.5f))
                path.quadTo(-s * 0.6f, -s * (0.5f + fl), 0f, 0f)
                path.close()
                p.color = 0xE61B1B26.toInt()
                c.drawPath(path, p)
                c.restore()
            }
        }

        // aer incins deasupra dealurilor
        if (day && k <= 1 && tC >= 32f) {
            val hs = ((tC - 32f) / 8f).coerceIn(0.3f, 1f)
            sp.strokeWidth = max(1.5f, h * 0.006f)
            for (i in 0 until 5) {
                path.reset()
                val by = h * (0.64f + 0.045f * i)
                path.moveTo(0f, by)
                var x = 0f
                while (x <= w) {
                    x += 30f
                    path.lineTo(x, by + sin(x * 0.018f + t * 2.2f + i * 1.3f) * h * 0.0045f)
                }
                sp.color = Color.argb((26 * hs).toInt(), 255, 255, 255)
                c.drawPath(path, sp)
            }
        }

        // chiciura pe marginile ecranului, la ger
        if (tC <= -2f) {
            val st = ((-2f - tC) / 10f).coerceIn(0.2f, 1f)
            val key = w.toLong() * 10000L + h.toLong()
            if (frostShader == null || frostKey != key) {
                frostShader = RadialGradient(
                    w / 2f, h / 2f, hypot(w, h) / 2f,
                    intArrayOf(0, 0, Color.argb(190, 225, 240, 255)),
                    floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
                )
                frostKey = key
            }
            p.shader = frostShader
            p.alpha = (255 * st).toInt().coerceIn(0, 255)
            c.drawRect(0f, 0f, w, h, p)
            p.shader = null
            p.alpha = 255
            sp.strokeWidth = max(1f, 1.6f * us)
            for (i in 0 until ncr) {
                val len = crl[i] * us
                val a = (0.55f + 0.45f * sin(t * 1.5f + i)) * st
                sp.color = Color.argb((200 * a).toInt().coerceIn(0, 255), 240, 250, 255)
                c.save()
                c.translate(crx[i] * w, cry[i] * h)
                for (j in 0 until 6) {
                    c.drawLine(0f, 0f, 0f, -len, sp)
                    c.drawLine(0f, -len * 0.55f, -len * 0.25f, -len * 0.8f, sp)
                    c.drawLine(0f, -len * 0.55f, len * 0.25f, -len * 0.8f, sp)
                    c.rotate(60f)
                }
                c.restore()
            }
        }

        // unde la atingere
        for (i in 0 until nr) {
            val a = ra[i]
            if (a >= 1f) continue
            for (j in 0 until 3) {
                val aa = a - j * 0.12f
                if (aa <= 0f) continue
                val rad = (20f + 190f * aa) * us
                sp.strokeWidth = max(1.5f, 3f * us * (1f - aa))
                sp.color = Color.argb((200 * (1f - aa)).toInt().coerceIn(0, 255), 235, 245, 255)
                rect.set(rx[i] - rad, ry[i] - rad * 0.4f, rx[i] + rad, ry[i] + rad * 0.4f)
                c.drawOval(rect, sp)
            }
            ra[i] = a + 0.018f * dtf
        }
    }
}
