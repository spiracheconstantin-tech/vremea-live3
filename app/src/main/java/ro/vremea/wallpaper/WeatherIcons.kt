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

// Soare: raze ascutite portocalii + disc galben in degrade
private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = pt()
    val n = 14
    for (i in 0 until n) {
        c.save()
        c.rotate(i * 360f / n, cx, cy)
        val ray = Path()
        ray.moveTo(cx - r * 0.2f, cy - r * 0.95f)
        ray.lineTo(cx, cy - r * 1.62f)
        ray.lineTo(cx + r * 0.2f, cy - r * 0.95f)
        ray.close()
        p.shader = LinearGradient(cx, cy - r * 0.95f, cx, cy - r * 1.62f, 0xFFF59E0B.toInt(), 0xFFFFC72C.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(ray, p)
        c.restore()
    }
    p.shader = RadialGradient(
        cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
        intArrayOf(0xFFFFF9B0.toInt(), 0xFFFFD21F.toInt(), 0xFFF5A300.toInt()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    c.drawCircle(cx, cy, r, p)
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

// Luna: semiluna cu degrade cald + stelute
private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = pt()
    p.shader = RadialGradient(cx, cy, r * 1.9f, intArrayOf(0x44FFF3B0, 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(cx, cy, r * 1.9f, p)
    p.shader = null
    val a = Path()
    a.addCircle(cx, cy, r, Path.Direction.CW)
    val b = Path()
    b.addCircle(cx + r * 0.5f, cy - r * 0.2f, r * 0.86f, Path.Direction.CW)
    a.op(b, Path.Op.DIFFERENCE)
    p.shader = LinearGradient(cx - r, cy - r, cx + r * 0.3f, cy + r, 0xFFFFF7C2.toInt(), 0xFFF2BE3B.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(a, p)
    p.shader = null
    p.color = 0xFFFFE680.toInt()
    star(c, cx + r * 0.75f, cy - r * 0.7f, r * 0.17f, p)
    star(c, cx + r * 0.95f, cy + r * 0.05f, r * 0.11f, p)
}

// Nor: 0 alb, 1 gri, 2 inchis (furtuna)
private fun cloud(c: Canvas, x: Float, y: Float, s: Float, tone: Int) {
    val top = when (tone) { 0 -> 0xFFFFFFFF.toInt(); 1 -> 0xFFEAF0F6.toInt(); else -> 0xFF8EA0BC.toInt() }
    val bot = when (tone) { 0 -> 0xFFD3E5F4.toInt(); 1 -> 0xFFBBC9DA.toInt(); else -> 0xFF5E7191.toInt() }
    val rim = when (tone) { 0 -> 0xFF8FB0CF.toInt(); 1 -> 0xFF7F94AE.toInt(); else -> 0xFF3F4E69.toInt() }
    val u = cloudPath()
    c.save()
    c.translate(x, y)
    c.scale(s, s)
    val p = pt()
    p.color = rim
    c.save()
    c.translate(5f, 6f)
    c.drawPath(u, p)
    c.restore()
    p.shader = LinearGradient(0f, 16f, 0f, 108f, top, bot, Shader.TileMode.CLAMP)
    c.drawPath(u, p)
    c.restore()
}

private fun drop(c: Canvas, x: Float, y: Float, s: Float) {
    c.save()
    c.translate(x, y)
    c.rotate(18f)
    val p = pt()
    val d = Path()
    d.moveTo(0f, -17f * s)
    d.cubicTo(13f * s, 0f, 11f * s, 17f * s, 0f, 17f * s)
    d.cubicTo(-11f * s, 17f * s, -13f * s, 0f, 0f, -17f * s)
    p.shader = LinearGradient(0f, -17f * s, 0f, 17f * s, 0xFFF1FAFF.toInt(), 0xFF9CCDE8.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(d, p)
    p.shader = null
    p.color = 0x99FFFFFF.toInt()
    c.drawOval(RectF(-6f * s, 0f, -2f * s, 9f * s), p)
    c.restore()
}

private fun rain(c: Canvas, n: Int, s: Float) {
    val xs: FloatArray
    val ys: FloatArray
    when (n) {
        3 -> { xs = floatArrayOf(82f, 126f, 170f); ys = floatArrayOf(176f, 198f, 176f) }
        4 -> { xs = floatArrayOf(66f, 106f, 146f, 186f); ys = floatArrayOf(172f, 196f, 172f, 196f) }
        else -> { xs = floatArrayOf(58f, 94f, 130f, 166f, 202f); ys = floatArrayOf(170f, 194f, 170f, 194f, 170f) }
    }
    for (i in xs.indices) drop(c, xs[i], ys[i], s)
}

private fun hailBalls(c: Canvas) {
    val p = pt()
    val xs = floatArrayOf(66f, 100f, 134f)
    val ys = floatArrayOf(178f, 204f, 180f)
    for (i in xs.indices) {
        p.shader = RadialGradient(xs[i] - 3f, ys[i] - 3f, 12f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFBFDDF2.toInt(), 0xFF8DB9E6.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(xs[i], ys[i], 9f, p)
    }
}

private fun flake(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = pt()
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    for (pass in 0..1) {
        p.strokeWidth = if (pass == 0) 7f else 3.5f
        p.color = if (pass == 0) 0xFF8DB9E6.toInt() else 0xFFFFFFFF.toInt()
        c.save()
        c.translate(cx, cy)
        for (i in 0 until 6) {
            c.drawLine(0f, 0f, 0f, -r, p)
            c.drawLine(0f, -r * 0.55f, -r * 0.28f, -r * 0.82f, p)
            c.drawLine(0f, -r * 0.55f, r * 0.28f, -r * 0.82f, p)
            c.rotate(60f)
        }
        c.restore()
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
    p.shader = LinearGradient(x, y, x, y + 78f * s, 0xFFFFD54F.toInt(), 0xFFF59E0B.toInt(), Shader.TileMode.CLAMP)
    c.drawPath(b, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 2.5f
    p.strokeJoin = Paint.Join.ROUND
    p.color = 0xFFD97706.toInt()
    c.drawPath(b, p)
}

private fun fog(c: Canvas) {
    val p = pt()
    val ys = floatArrayOf(172f, 196f, 220f)
    val x0 = floatArrayOf(52f, 36f, 70f)
    val x1 = floatArrayOf(204f, 176f, 214f)
    for (i in 0..2) {
        p.color = 0xFF8FB0CF.toInt()
        c.drawRoundRect(RectF(x0[i] + 3f, ys[i] - 6f + 4f, x1[i] + 3f, ys[i] + 6f + 4f), 7f, 7f, p)
        p.shader = LinearGradient(0f, ys[i] - 6f, 0f, ys[i] + 6f, 0xFFFFFFFF.toInt(), 0xFFD3E5F4.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x0[i], ys[i] - 6f, x1[i], ys[i] + 6f), 7f, 7f, p)
        p.shader = null
    }
}

private fun drawWeather(c: Canvas, code: Int, day: Boolean) {
    when {
        code == 0 -> if (day) sun(c, 128f, 128f, 46f) else moon(c, 128f, 128f, 56f)
        code <= 2 -> {
            if (day) sun(c, 92f, 84f, 34f) else moon(c, 92f, 84f, 40f)
            cloud(c, 26f, 100f, 1.05f, 0)
        }
        code == 3 -> {
            cloud(c, 66f, 34f, 0.8f, 1)
            cloud(c, 18f, 88f, 1.05f, 0)
        }
        code <= 48 -> {
            cloud(c, 22f, 34f, 1f, 0)
            fog(c)
        }
        code <= 57 -> {
            cloud(c, 22f, 34f, 1f, 0)
            rain(c, 3, 0.8f)
        }
        code <= 67 -> {
            cloud(c, 22f, 34f, 1f, 1)
            rain(c, if (code >= 65) 5 else 4, 1f)
        }
        code <= 77 -> {
            cloud(c, 22f, 34f, 1f, 0)
            flake(c, 66f, 190f, 15f)
            flake(c, 126f, 208f, 17f)
            flake(c, 188f, 188f, 15f)
        }
        code <= 82 -> {
            cloud(c, 22f, 34f, 1f, 1)
            rain(c, 5, 1f)
        }
        code <= 86 -> {
            cloud(c, 22f, 34f, 1f, 0)
            flake(c, 66f, 190f, 15f)
            flake(c, 126f, 208f, 17f)
            flake(c, 188f, 188f, 15f)
        }
        else -> {
            cloud(c, 22f, 24f, 1f, 2)
            if (code >= 96) hailBalls(c) else { drop(c, 62f, 178f, 0.8f); drop(c, 96f, 204f, 0.8f) }
            bolt(c, 148f, 118f, 1f)
        }
    }
}
