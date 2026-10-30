package ro.vremea.wallpaper

import android.graphics.*

// Silueta norului: cercuri suprapuse + baza rotunjita
internal fun cloudPath(): Path {
    val u = Path()
    val parts = arrayOf(
        floatArrayOf(42f, 80f, 26f), floatArrayOf(86f, 58f, 40f),
        floatArrayOf(142f, 62f, 38f), floatArrayOf(176f, 84f, 22f)
    )
    for (q in parts) {
        val s1 = Path()
        s1.addCircle(q[0], q[1], q[2], Path.Direction.CW)
        u.op(s1, Path.Op.UNION)
    }
    val rb = Path()
    rb.addRoundRect(RectF(16f, 78f, 198f, 108f), 15f, 15f, Path.Direction.CW)
    u.op(rb, Path.Op.UNION)
    return u
}

// Nor pentru fundalul animat, in acelasi stil: culoare in degrade + margine albastra
internal fun makeCloudBitmap(col: Int): Bitmap {
    val r = Color.red(col)
    val g = Color.green(col)
    val b = Color.blue(col)
    fun mx(a: Int, t: Float, to: Int): Int = (a + (to - a) * t).toInt().coerceIn(0, 255)
    val top = Color.rgb(mx(r, 0.35f, 255), mx(g, 0.35f, 255), mx(b, 0.35f, 255))
    val bot = Color.rgb(mx(r, 0.22f, 127), mx(g, 0.22f, 151), mx(b, 0.22f, 184))
    val rim = Color.rgb((r * 0.55f).toInt(), (g * 0.6f).toInt(), (b * 0.68f).toInt())
    val bmp = Bitmap.createBitmap(440, 240, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.scale(2.2f, 2.2f)
    cv.translate(-12f, -14f)
    val u = cloudPath()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = rim
    cv.save()
    cv.translate(5f, 6f)
    cv.drawPath(u, p)
    cv.restore()
    p.shader = LinearGradient(0f, 18f, 0f, 108f, top, bot, Shader.TileMode.CLAMP)
    cv.drawPath(u, p)
    return bmp
}
