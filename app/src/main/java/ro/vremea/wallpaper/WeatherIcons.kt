package ro.vremea.wallpaper

import android.content.Context
import android.graphics.*
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val icCache = HashMap<Int, Bitmap>()

fun weatherBitmap(code: Int, day: Boolean): Bitmap {
    val key = code * 2 + (if (day) 1 else 0)
    val old = icCache[key]
    if (old != null) return old
    val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    drawWeather(Canvas(b), code, day)
    icCache[key] = b
    return b
}

fun iconView(ctx: Context, code: Int, day: Boolean, dp: Int): ImageView {
    val px = (dp * ctx.resources.displayMetrics.density).toInt()
    val v = ImageView(ctx)
    v.setImageBitmap(weatherBitmap(code, day))
    v.scaleType = ImageView.ScaleType.FIT_CENTER
    v.layoutParams = LinearLayout.LayoutParams(px, px)
    return v
}

private fun pt(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = pt()
    p.shader = RadialGradient(cx, cy, r * 2.2f, intArrayOf(0x99FFD54F.toInt(), 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(cx, cy, r * 2.2f, p)
    p.shader = null
    for (i in 0 until 12) {
        c.save()
        c.rotate(i * 30f, cx, cy)
        val len = if (i % 2 == 0) 1.85f else 1.6f
        val ray = Path()
        ray.moveTo(cx - r * 0.14f, cy - r * 1.3f)
        ray.lineTo(cx + r * 0.14f, cy - r * 1.3f)
        ray.lineTo(cx, cy - r * len)
        ray.close()
        p.shader = LinearGradient(cx, cy - r * 1.3f, cx, cy - r * len, 0xFFFFA81F.toInt(), 0xFFFFE680.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(ray, p)
        c.restore()
    }
    p.shader = RadialGradient(
        cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
        intArrayOf(0xFFFFFFE6.toInt(), 0xFFFFD43B.toInt(), 0xFFFF9A0A.toInt(), 0xFFE0680A.toInt()),
        floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP
    )
    c.drawCircle(cx, cy, r, p)
    p.shader = null
    p.color = 0x66FFFFFF
    c.drawOval(RectF(cx - r * 0.62f, cy - r * 0.85f, cx + r * 0.05f, cy - r * 0.35f), p)
}

private fun star(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
    val q = Path()
    q.moveTo(x, y - s)
    q.quadTo(x, y, x + s, y)
    q.quadTo(x, y, x, y + s)
    q.quadTo(x, y, x - s, y)
    q.quadTo(x, y, x, y - s)
    c.drawPath(q, p)
}

private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = pt()
    p.shader = RadialGradient(cx, cy, r * 2f, intArrayOf(0x55CFE0FF, 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(cx, cy, r * 2f, p)
    p.shader = null
    val a = Path()
    a.addCircle(cx, cy, r, Path.Direction.CW)
    val b = Path()
    b.addCircle(cx + r * 0.5f, cy - r * 0.2f, r * 0.86f, Path.Direction.CW)
    a.op(b, Path.Op.DIFFERENCE)
    p.shader = RadialGradient(
        cx - r * 0.5f, cy - r * 0.2f, r * 1.7f,
        intArrayOf(0xFFFFFBE0.toInt(), 0xFFEBD98A.toInt(), 0xFFB9A45A.toInt()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    c.drawPath(a, p)
    p.shader = null
    c.save()
    c.clipPath(a)
    p.color = 0x33806A30
    c.drawCircle(cx - r * 0.5f, cy + r * 0.2f, r * 0.14f, p)
    c.drawCircle(cx - r * 0.2f, cy - r * 0.45f, r * 0.1f, p)
    c.drawCircle(cx - r * 0.7f, cy - r * 0.2f, r * 0.08f, p)
    c.restore()
    p.color = 0xFFFFF3B0.toInt()
    star(c, cx + r * 0.75f, cy - r * 0.7f, r * 0.17f, p)
    star(c, cx + r * 0.95f, cy + r * 0.05f, r * 0.11f, p)
}

private fun puff(c: Canvas, cx: Float, cy: Float, r: Float, dark: Boolean) {
    val p = pt()
    p.shader = RadialGradient(
        cx - r * 0.35f, cy - r * 0.45f, r * 1.6f,
        intArrayOf(
            if (dark) 0xFFD2DAE6.toInt() else 0xFFFFFFFF.toInt(),
            if (dark) 0xFF9AA7BC.toInt() else 0xFFE7F0FA.toInt(),
            if (dark) 0xFF5C6A82.toInt() else 0xFFAFC6E0.toInt()
        ),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    c.drawCircle(cx, cy, r, p)
}

private fun cloud(c: Canvas, x: Float, y: Float, s: Float, dark: Boolean) {
    c.save()
    c.translate(x, y)
    c.scale(s, s)
    val p = pt()
    p.color = 0x4D102850
    p.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
    c.drawRoundRect(RectF(22f, 88f, 196f, 120f), 18f, 18f, p)
    p.maskFilter = null
    p.shader = LinearGradient(
        0f, 64f, 0f, 108f,
        if (dark) 0xFF8794AA.toInt() else 0xFFEAF2FB.toInt(),
        if (dark) 0xFF56637A.toInt() else 0xFFA7BEDA.toInt(), Shader.TileMode.CLAMP
    )
    c.drawRoundRect(RectF(14f, 64f, 198f, 108f), 22f, 22f, p)
    p.shader = null
    puff(c, 45f, 70f, 30f, dark)
    puff(c, 172f, 80f, 26f, dark)
    puff(c, 140f, 58f, 36f, dark)
    puff(c, 88f, 46f, 42f, dark)
    puff(c, 120f, 92f, 18f, dark)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 5f
    p.strokeCap = Paint.Cap.ROUND
    p.color = 0xAAFFFFFF.toInt()
    c.drawArc(RectF(60f, 8f, 116f, 78f), 200f, 60f, false, p)
    c.restore()
}

private fun drop(c: Canvas, x: Float, y: Float, s: Float) {
    val p = pt()
    val d = Path()
    d.moveTo(x, y - 16f * s)
    d.cubicTo(x + 13f * s, y + 2f * s, x + 10f * s, y + 18f * s, x, y + 18f * s)
    d.cubicTo(x - 10f * s, y + 18f * s, x - 13f * s, y + 2f * s, x, y - 16f * s)
    p.shader = LinearGradient(x - 10f * s, y, x + 12f * s, y + 10f * s, 0xFFB5E4FF.toInt(), 0xFF2A74E0.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(d, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1.5f * s
    p.color = 0x99246CC8.toInt()
    c.drawPath(d, p)
    p.style = Paint.Style.FILL
    p.color = 0xCCFFFFFF.toInt()
    c.drawOval(RectF(x - 6f * s, y - 2f * s, x - 1f * s, y + 9f * s), p)
}

private fun drops(c: Canvas, n: Int, s: Float) {
    for (i in 0 until n) {
        val x = 60f + i * (136f / (n - 1))
        drop(c, x, 186f + (if (i % 2 == 0) 0f else 16f), s)
    }
}

private fun flake(c: Canvas, x: Float, y: Float, r: Float) {
    val p = pt()
    p.shader = RadialGradient(x, y, r * 1.8f, intArrayOf(0x66FFFFFF, 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(x, y, r * 1.8f, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    for (pass in 0..1) {
        p.strokeWidth = if (pass == 0) 8f else 4.5f
        p.color = if (pass == 0) 0xFF8DB9E6.toInt() else 0xFFFFFFFF.toInt()
        for (i in 0 until 3) {
            val a = i * PI.toFloat() / 3f
            c.drawLine(x - cos(a) * r, y - sin(a) * r, x + cos(a) * r, y + sin(a) * r, p)
        }
    }
}

private fun bolt(c: Canvas, x: Float, y: Float, s: Float) {
    val p = pt()
    val b = Path()
    b.moveTo(x + 8f * s, y)
    b.lineTo(x - 22f * s, y + 40f * s)
    b.lineTo(x - 4f * s, y + 40f * s)
    b.lineTo(x - 18f * s, y + 78f * s)
    b.lineTo(x + 26f * s, y + 28f * s)
    b.lineTo(x + 6f * s, y + 28f * s)
    b.lineTo(x + 22f * s, y)
    b.close()
    p.color = 0x66FFC832
    p.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    c.drawPath(b, p)
    p.maskFilter = null
    p.shader = LinearGradient(x, y, x, y + 78f * s, 0xFFFFF59D.toInt(), 0xFFFF8F00.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(b, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f
    p.strokeJoin = Paint.Join.ROUND
    p.color = 0xFFD86A00.toInt()
    c.drawPath(b, p)
}

private fun fog(c: Canvas) {
    val p = pt()
    val ys = floatArrayOf(170f, 194f, 218f)
    val x0 = floatArrayOf(52f, 36f, 70f)
    val x1 = floatArrayOf(204f, 176f, 214f)
    for (i in 0..2) {
        p.shader = LinearGradient(0f, ys[i] - 6f, 0f, ys[i] + 6f, 0xFFEAF2FA.toInt(), 0xFF9DB4CC.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x0[i], ys[i] - 6f, x1[i], ys[i] + 6f), 6f, 6f, p)
    }
}

private fun drawWeather(c: Canvas, code: Int, day: Boolean) {
    when {
        code == 0 -> if (day) sun(c, 128f, 128f, 50f) else moon(c, 128f, 128f, 56f)
        code <= 2 -> {
            if (day) sun(c, 100f, 90f, 36f) else moon(c, 100f, 92f, 40f)
            cloud(c, 38f, 100f, 0.95f, false)
        }
        code == 3 -> {
            cloud(c, 70f, 40f, 0.75f, true)
            cloud(c, 24f, 88f, 1f, false)
        }
        code <= 48 -> {
            cloud(c, 28f, 40f, 1f, false)
            fog(c)
        }
        code <= 57 -> {
            cloud(c, 28f, 36f, 1f, true)
            drops(c, 3, 0.8f)
        }
        code <= 67 -> {
            cloud(c, 28f, 36f, 1f, true)
            drops(c, if (code >= 65) 5 else 4, 1f)
        }
        code <= 77 -> {
            cloud(c, 28f, 36f, 1f, false)
            flake(c, 70f, 190f, 14f)
            flake(c, 128f, 205f, 16f)
            flake(c, 186f, 188f, 14f)
        }
        code <= 82 -> {
            cloud(c, 28f, 36f, 1f, true)
            drops(c, 5, 1f)
        }
        code <= 86 -> {
            cloud(c, 28f, 36f, 1f, false)
            flake(c, 70f, 190f, 14f)
            flake(c, 128f, 205f, 16f)
            flake(c, 186f, 188f, 14f)
        }
        else -> {
            cloud(c, 28f, 30f, 1f, true)
            bolt(c, 118f, 140f, 1f)
            drops(c, 2, 0.8f)
        }
    }
}
