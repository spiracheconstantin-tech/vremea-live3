package ro.vremea.wallpaper

import android.graphics.*
import kotlin.math.sin

// Soare si luna in stilul desenat (acelasi cu iconitele)
class FlatSky {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var moon: Path? = null
    private var moonR = 0f

    private fun spark(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s)
        path.quadTo(x, y, x + s, y)
        path.quadTo(x, y, x, y + s)
        path.quadTo(x, y, x - s, y)
        path.quadTo(x, y, x, y - s)
        c.drawPath(path, p)
    }

    fun draw(c: Canvas, day: Boolean, sx: Float, sy: Float, w: Float, h: Float, t: Float) {
        val r = w * 0.075f
        if (day) {
            for (i in 0 until 14) {
                c.save()
                c.rotate(t * 4f + i * 360f / 14f, sx, sy)
                path.reset()
                path.moveTo(sx - r * 0.2f, sy - r * 0.95f)
                path.lineTo(sx, sy - r * 1.62f)
                path.lineTo(sx + r * 0.2f, sy - r * 0.95f)
                path.close()
                p.shader = LinearGradient(sx, sy - r * 0.95f, sx, sy - r * 1.62f, 0xFFF59E0B.toInt(), 0xFFFFC72C.toInt(), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
                c.restore()
            }
            p.shader = RadialGradient(
                sx - r * 0.3f, sy - r * 0.3f, r * 1.4f,
                intArrayOf(0xFFFFF9B0.toInt(), 0xFFFFD21F.toInt(), 0xFFF5A300.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(sx, sy, r, p)
            p.shader = null
        } else {
            for (i in 0 until 70) {
                val x = ((i * 7919) % 1000) / 1000f * w
                val y = ((i * 104729) % 1000) / 1000f * h * 0.6f
                val a = 0.5f + 0.5f * sin(t * (1.2f + (i % 5) * 0.4f) + i)
                p.color = Color.argb((90 + 150 * a).toInt(), 255, 255, 235)
                c.drawCircle(x, y, 1.3f + (i % 3) * 0.7f, p)
            }
            var mp = moon
            if (mp == null || moonR != r) {
                val a = Path()
                a.addCircle(0f, 0f, r, Path.Direction.CW)
                val b = Path()
                b.addCircle(r * 0.5f, -r * 0.2f, r * 0.86f, Path.Direction.CW)
                a.op(b, Path.Op.DIFFERENCE)
                moon = a
                moonR = r
                mp = a
            }
            c.save()
            c.translate(sx, sy)
            p.shader = LinearGradient(-r, -r, r * 0.3f, r, 0xFFFFF7C2.toInt(), 0xFFF2BE3B.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(mp, p)
            p.shader = null
            p.color = 0xFFFFE680.toInt()
            spark(c, r * 0.75f, -r * 0.7f, r * 0.17f)
            spark(c, r * 0.95f, r * 0.05f, r * 0.11f)
            c.restore()
        }
    }
}
