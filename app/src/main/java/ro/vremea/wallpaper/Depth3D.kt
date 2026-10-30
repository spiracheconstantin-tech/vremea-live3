package ro.vremea.wallpaper

import android.graphics.*
import kotlin.math.max
import kotlin.math.sin

// Dealuri pe 3 straturi: fiecare se misca diferit cand inclini telefonul
class Hills3D {
    private val bmps = arrayOfNulls<Bitmap>(3)
    private var key = -1L

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
    )

    fun draw(c: Canvas, paint: Paint, w: Float, h: Float, k: Int, bot: Int, day: Boolean, tx: Float, ty: Float) {
        val bw = max(1, (w * 1.2f).toInt())
        val bh = max(1, (h * 0.24f).toInt())
        val kk = (k * 2 + (if (day) 1 else 0)).toLong() * 100000000L + w.toLong() * 10000L + h.toLong()
        if (key != kk) {
            build(bw, bh, k, bot, day)
            key = kk
        }
        paint.shader = null
        paint.colorFilter = null
        paint.alpha = 255
        for (l in 0 until 3) {
            val b = bmps[l] ?: continue
            val par = 0.015f + 0.03f * l
            val ox = tx * w * par - w * 0.1f
            val oy = ty * h * (0.004f + 0.008f * l) + h * 0.02f
            c.drawBitmap(b, ox, h - bh + oy, paint)
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
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val col = mix(bot, ground, 0.28f + 0.30f * l)
            val baseY = bhf * (0.22f + 0.24f * l)
            val amp = bhf * (0.13f - 0.02f * l)
            val f1 = 0.004f + 0.0015f * l
            val ph = 1.7f * l + 0.6f
            fun ry(xx: Float): Float = baseY + amp * (sin(xx * f1 + ph) + 0.5f * sin(xx * f1 * 2.3f + ph * 1.9f))
            val path = Path()
            val ridge = Path()
            path.moveTo(0f, bhf)
            var x = 0f
            var first = true
            while (x <= bwf + 12f) {
                val y = ry(x)
                path.lineTo(x, y)
                if (first) {
                    ridge.moveTo(x, y)
                    first = false
                } else {
                    ridge.lineTo(x, y)
                }
                x += 12f
            }
            path.lineTo(bwf, bhf)
            path.close()
            p.shader = LinearGradient(
                0f, baseY - amp, 0f, bhf,
                mix(col, Color.WHITE, 0.16f), mix(col, Color.BLACK, 0.38f), Shader.TileMode.CLAMP
            )
            cv.drawPath(path, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f
            p.color = Color.argb(60, 255, 255, 255)
            cv.drawPath(ridge, p)
            p.style = Paint.Style.FILL

            if (l == 2 && k != 5) {
                p.color = mix(col, Color.BLACK, 0.55f)
                var tx2 = 30f
                var seed = 7
                while (tx2 < bwf) {
                    seed = (seed * 1103515245 + 12345) and 0x7fffffff
                    val th = bhf * (0.10f + 0.10f * ((seed % 100) / 100f))
                    val base = ry(tx2)
                    val tw = th * 0.38f
                    val tree = Path()
                    tree.moveTo(tx2, base - th)
                    tree.lineTo(tx2 + tw, base + 2f)
                    tree.lineTo(tx2 - tw, base + 2f)
                    tree.close()
                    cv.drawPath(tree, p)
                    tx2 += 38f + (seed % 40)
                }
            }
            if (l < 2) {
                p.shader = LinearGradient(
                    0f, baseY, 0f, bhf,
                    Color.argb(80, Color.red(bot), Color.green(bot), Color.blue(bot)),
                    Color.argb(0, Color.red(bot), Color.green(bot), Color.blue(bot)),
                    Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, baseY, bwf, bhf, p)
                p.shader = null
            }
            bmps[l] = b
        }
    }
}
