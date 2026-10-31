package ro.vremea.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

internal class Scn(
    val name: String, val code: Int, val temp: Double, val wind: Double, val dir: Double,
    val cloud: Int, val precip: Double, val minute: Int
)

internal object TestMode {
    val list = listOf(
        Scn("Senin, zi (24°)", 0, 24.0, 8.0, 270.0, 0, 0.0, 13 * 60),
        Scn("Senin, apus", 0, 16.0, 6.0, 90.0, 5, 0.0, 18 * 60 + 50),
        Scn("Senin, noapte cu lună", 0, 10.0, 4.0, 270.0, 0, 0.0, 23 * 60),
        Scn("Parțial noros", 2, 20.0, 14.0, 270.0, 45, 0.0, 14 * 60),
        Scn("Noros", 3, 15.0, 20.0, 90.0, 100, 0.0, 12 * 60),
        Scn("Ceață de dimineață", 45, 8.0, 3.0, 270.0, 100, 0.0, 7 * 60 + 15),
        Scn("Burniță", 53, 12.0, 10.0, 270.0, 100, 0.3, 11 * 60),
        Scn("Ploaie moderată", 63, 11.0, 18.0, 270.0, 100, 3.0, 15 * 60),
        Scn("Ploaie torențială", 65, 13.0, 30.0, 90.0, 100, 10.0, 16 * 60),
        Scn("Furtună cu grindină", 99, 21.0, 45.0, 270.0, 100, 12.0, 17 * 60),
        Scn("Ninsoare slabă", 71, -1.0, 6.0, 270.0, 100, 0.3, 11 * 60),
        Scn("Ninsoare puternică", 75, -6.0, 28.0, 90.0, 100, 3.0, 10 * 60),
        Scn("Ger (−12°), senin", 0, -12.0, 5.0, 270.0, 0, 0.0, 9 * 60),
        Scn("Caniculă (37°)", 0, 37.0, 6.0, 270.0, 0, 0.0, 14 * 60)
    )

    fun read(ctx: Context): Scn? {
        val i = ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getInt("test", -1)
        return if (i >= 0 && i < list.size) list[i] else null
    }
}

fun testMode(a: Activity) {
    val names = arrayOf("⏹️ Oprește testul (vreme reală)") + TestMode.list.map { it.name }.toTypedArray()
    AlertDialog.Builder(a).setTitle("Mod test fundal").setItems(names) { _, i ->
        a.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("test", i - 1).apply()
        Toast.makeText(a, if (i == 0) "Test oprit" else "Test pornit: ieși pe ecranul de start", Toast.LENGTH_LONG).show()
    }.show()
}
