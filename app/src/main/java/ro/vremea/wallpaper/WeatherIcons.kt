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
    icCache[key]?.let { return it }
    val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    drawWeather(Canvas(b), code, day)
    icCache[key] = b
    return b
}

fun iconView(ctx: Context, code: Int, day: Boolean, dp: Int): ImageView {
    val px = (dp * ctx.resources.displayMetrics.density).toInt()
    return ImageView(ctx).apply {
        setImageBitmap(weatherBitmap(code, day))
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(px, px)
    }
}

private fun pt() = Paint(Paint.ANTI_ALIAS_FLAG)

private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = pt()
    p.shader = RadialGradient(cx, cy, r * 2f, intArrayOf(0x88FFD54F.toInt(), 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(cx, cy, r * 2f, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    p.strokeWidth = r * 0.24f
    p.color = 0xFFFFB41F.toInt()
    for (i in 0 until 12) {
        val a = i * PI.toFloat() / 6f
        val r2 = if (i % 2 == 0) 1.7f else 1.52f
        c.drawLine(cx + cos(a) * r * 1.28f, cy + sin(a) * r * 1.28f, cx + cos(a) * r * r2, cy + sin(a) * r * r2, p)
    }
    p.style = Paint.Style.FILL
    p.shader = RadialGradient(
        cx - r * .3f, cy - r * .3f, r * 1.4f,
        intArrayOf(0xFFFFF7B0.toInt(), 0xFFFFC832.toInt(), 0xFFFF9A0A.toInt()),
        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
    )
    c.drawCircle(cx, cy, r, p)
    p.shader = null
    p.color = 0x70FFFFFF
    c.drawOval(RectF(cx - r * .6f, cy - r * .8f, cx + r * .1f, cy - r * .3f), p)
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
    p.shader = RadialGradient(cx, cy, r * 1.9f, intArrayOf(0x55CFE0FF, 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(cx, cy, r * 1.9f, p)
    p.shader = null
    val a = Path()
    a.addCircle(cx, cy, r, Path.Direction.CW)
    val b = Path()
    b.addCircle(cx + r * 0.5f, cy - r * 0.2f, r * 0.86f, Path.Direction.CW)
    a.op(b, Path.Op.DIFFERENCE)
    p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFF8D6.toInt(), 0xFFE6CF7E.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(a, p)
    p.shader = null
    p.color = 0xFFFFF3B0.toInt()
    star(c, cx + r * 0.75f, cy - r * 0.7f, r * 0.17f, p)
    star(c, cx + r * 0.95f, cy + r * 0.05f, r * 0.11f, p)
}

private fun cloud(c: Canvas, x: Float, y: Float, s: Float, dark: Boolean) {
    val u = Path()
    val parts = arrayOf(
        floatArrayOf(45f, 68f, 32f), floatArrayOf(88f, 46f, 42f),
        floatArrayOf(140f, 58f, 36f), floatArrayOf(172f, 78f, 26f)
    )
    for (q in parts) {
        val s1 = Path()
        s1.addCircle(q[0], q[1], q[2], Path.Direction.CW)
        u.op(s1, Path.Op.UNION)
    }
    val rb = Path()
    rb.addRoundRect(RectF(14f, 66f, 198f, 106f), 24f, 24f, Path.Direction.CW)
    u.op(rb, Path.Op.UNION)
    c.save()
    c.translate(x, y)
    c.scale(s, s)
    val p = pt()
    p.color = 0x40102850
    p.maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
    c.save()
    c.translate(0f, 7f)
    c.drawPath(u, p)
    c.restore()
    p.maskFilter = null
    p.shader = LinearGradient(
        0f, 20f, 0f, 108f,
        if (dark) 0xFFA9B4C4.toInt() else 0xFFFFFFFF.toInt(),
        if (dark) 0xFF5F6C80.toInt() else 0xFFC4D8EE.toInt(), Shader.TileMode.CLAMP
    )
    c.drawPath(u, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 2f
    p.color = if (dark) 0x66384560 else 0x667FA3CE
    c.drawPath(u, p)
    p.strokeWidth = 5f
    p.strokeCap = Paint.Cap.ROUND
    p.color = 0xAAFFFFFF.toInt()
    c.drawArc(RectF(54f, 12f, 122f, 80f), 200f, 60f, false, p)
    c.restore()
}

private fun drop(c: Canvas, x: Float, y: Float, s: Float) {
    val p = pt()
    val d = Path()
    d.moveTo(x, y - 16f * s)
    d.cubicTo(x + 13f * s, y + 2f * s, x + 10f * s, y + 18f * s, x, y + 18f * s)
    d.cubicTo(x - 10f * s, y + 18f * s, x - 13f * s, y + 2f * s, x, y - 16f * s)
    p.shader = LinearGradient(x, y - 16f * s, x, y + 18f * s, 0xFF8FD8FF.toInt(), 0xFF2F7FEA.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(d, p)
    p.shader = null
    p.color = 0x99FFFFFF.toInt()
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
    p.shader = LinearGradient(x, y, x, y + 78f * s, 0xFFFFF176.toInt(), 0xFFFF9A0A.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(b, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f
    p.strokeJoin = Paint.Join.ROUND
    p.color = 0xFFE8760C.toInt()
    c.drawPath(b, p)
}

private fun fog(c: Canvas) {
    val p = pt()
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    p.strokeWidth = 11f
    p.color = 0xFFB9CCE0.toInt()
    c.drawLine(52f, 170f, 204f, 170f, p)
    c.drawLine(36f, 194f, 176f, 194f, p)
    c.drawLine(70f, 218f, 214f, 218f, p)
}

private fun drawWeather(c: Canvas, code: Int, day: Boolean) {
    when {
        code == 0 -> if (day) sun(c, 128f, 128f, 50f) else moon(c, 128f, 128f, 56f)
        code <= 2 -> {
            if (day) sun(c, 100f, 90f, 36f) else moon(c, 100f, 92f, 40f)
            cloud(c, 38f, 100f, 0.95f, false)
        }
        code == 3 -> { cloud(c, 70f, 40f, 0.75f, true); cloud(c, 24f, 88f, 1f, false) }
        code <= 48 -> { cloud(c, 28f, 40f, 1f, false); fog(c) }
        code <= 57 -> { cloud(c, 28f, 36f, 1f, true); drops(c, 3, 0.8f) }
        code <= 67 -> { cloud(c, 28f, 36f, 1f, true); drops(c, if (code >= 65) 5 else 4, 1f) }
        code <= 77 -> {
            cloud(c, 28f, 36f, 1f, false)
            flake(c, 70f, 190f, 14f); flake(c, 128f, 205f, 16f); flake(c, 186f, 188f, 14f)
        }
        code <= 82 -> { cloud(c, 28f, 36f, 1f, true); drops(c, 5, 1f) }
        code <= 86 -> {
            cloud(c, 28f, 36f, 1f, false)
            flake(c, 70f, 190f, 14f); flake(c, 128f, 205f, 16f); flake(c, 186f, 188f, 14f)
        }
        else -> { cloud(c, 28f, 30f, 1f, true); bolt(c, 118f, 140f, 1f); drops(c, 2, 0.8f) }
    }
}
