package ro.vremea.wallpaper

import android.graphics.*

// Nor curat: umplere alb -> albastru deschis, contur fin, reflexie sus, umbra discreta
internal fun cloudIcon(c: Canvas, x: Float, y: Float, s: Float, dark: Boolean) {
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
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

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
        if (dark) 0xFF5F6C80.toInt() else 0xFFC4D8EE.toInt(),
        Shader.TileMode.CLAMP
    )
    c.drawPath(u, p)
    p.shader = null

    c.save()
    c.clipPath(u)
    val q = Paint(Paint.ANTI_ALIAS_FLAG)
    q.shader = LinearGradient(
        0f, 70f, 0f, 108f,
        0x00FFFFFF, if (dark) 0x40202A40 else 0x338AA8D0,
        Shader.TileMode.CLAMP
    )
    c.drawRect(0f, 60f, 220f, 112f, q)
    c.restore()

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
