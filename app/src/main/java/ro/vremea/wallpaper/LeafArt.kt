package ro.vremea.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

internal object LeafArt {
    private val leaf = Path().apply {
        moveTo(-1f, 0f)
        quadTo(-0.2f, -0.78f, 1f, 0f)
        quadTo(-0.2f, 0.78f, -1f, 0f)
        close()
    }

    fun draw(c: Canvas, p: Paint, sp: Paint, s: Float) {
        c.save()
        c.scale(s, s)
        c.drawPath(leaf, p)
        sp.color = Color.argb(110, 80, 40, 15)
        sp.strokeWidth = 0.07f
        c.drawLine(-1.35f, 0f, 0.85f, 0f, sp)
        for (i in 0 until 3) {
            val x = -0.55f + i * 0.4f
            c.drawLine(x, 0f, x + 0.28f, -0.3f, sp)
            c.drawLine(x, 0f, x + 0.28f, 0.3f, sp)
        }
        c.restore()
    }
}
