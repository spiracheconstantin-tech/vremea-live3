package ro.vremea.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.json.JSONObject
import java.net.URL

// Ceata de poluare: cerul capata o nuanta cenusie-bruna cand calitatea aerului e slaba
class AirFx(private val ctx: Context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    @Volatile private var aqi = 0f
    @Volatile private var busy = false
    private var at = 0L
    private var shader: Shader? = null
    private var shKey = -1

    private fun fetch() {
        Thread {
            try {
                val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                val lp = ctx.getSharedPreferences("loc", Context.MODE_PRIVATE)
                val name = ap.getString("name", null)
                val useApp = name != null && name != "Locația mea"
                val lat = (if (useApp) ap.getString("lat", null) else lp.getString("lat", null)) ?: ap.getString("lat", null) ?: "44.4268"
                val lon = (if (useApp) ap.getString("lon", null) else lp.getString("lon", null)) ?: ap.getString("lon", null) ?: "26.1025"
                val u = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=european_aqi&timezone=auto"
                val cur = JSONObject(URL(u).readText()).getJSONObject("current")
                aqi = cur.optDouble("european_aqi", 0.0).toFloat()
            } catch (e: Exception) { }
            busy = false
        }.start()
    }

    fun draw(c: Canvas, w: Float, h: Float) {
        val now = System.currentTimeMillis()
        if (!busy && now - at > 30L * 60L * 1000L) {
            busy = true
            at = now
            fetch()
        }
        if (aqi < 50f) return
        val s = ((aqi - 50f) / 70f).coerceIn(0f, 1f)
        val key = (s * 100f).toInt() * 100000 + h.toInt()
        if (shader == null || shKey != key) {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.argb((40 * s).toInt(), 150, 130, 100), Color.argb((120 * s).toInt(), 170, 150, 115),
                Shader.TileMode.CLAMP
            )
            shKey = key
        }
        p.shader = shader
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }
}
