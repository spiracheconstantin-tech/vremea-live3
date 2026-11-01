package ro.vremea.wallpaper

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private fun cardBitmap(d: CurData): Bitmap {
    val W = 1080
    val H = 1350
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val tc = (if (d.f) (d.t - 32.0) * 5.0 / 9.0 else d.t).toFloat()
    widgetBg(c, W, H, d.code, d.day, tc)
    val wf = W.toFloat()
    val hf = H.toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.textAlign = Paint.Align.CENTER
    p.setShadowLayer(8f, 0f, 3f, 0x66000000)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale("ro")).format(Date()).replaceFirstChar { it.uppercase() }
    p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    wText(c, p, d.name, wf / 2f, 130f, 78f, wf * 0.85f)
    p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    wText(c, p, date, wf / 2f, 195f, 40f, wf * 0.85f, 220)
    c.drawBitmap(weatherBitmap(d.code, d.day), null, RectF(wf / 2f - 260f, 230f, wf / 2f + 260f, 750f), p)
    p.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    wText(c, p, "${d.t.roundToInt()}°", wf / 2f, 960f, 260f, wf * 0.7f)
    val labels = arrayOf("Senin", "Parțial noros", "Noros", "Ceață", "Burniță", "Ploaie", "Ninsoare", "Averse", "Averse de zăpadă", "Furtună")
    val code = d.code
    val lab = when {
        code == 0 -> labels[0]
        code <= 2 -> labels[1]
        code == 3 -> labels[2]
        code <= 48 -> labels[3]
        code <= 57 -> labels[4]
        code <= 67 -> labels[5]
        code <= 77 -> labels[6]
        code <= 82 -> labels[7]
        code <= 86 -> labels[8]
        else -> labels[9]
    }
    p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    wText(c, p, lab, wf / 2f, 1040f, 60f, wf * 0.8f)
    val n = min(5, d.dCode.size)
    val colW = (wf - 80f) / n
    for (i in 0 until n) {
        val cx = 40f + colW * (i + 0.5f)
        val nm = if (i == 0) "Azi" else try {
            val dt = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.dates[i])
            SimpleDateFormat("EEE", Locale("ro")).format(dt!!).replace(".", "").replaceFirstChar { it.uppercase() }
        } catch (e: Exception) { "" }
        wText(c, p, nm, cx, 1120f, 34f, colW * 0.9f, 220)
        c.drawBitmap(weatherBitmap(d.dCode[i], true), null, RectF(cx - 55f, 1135f, cx + 55f, 1245f), p)
        wText(c, p, "${d.dMax[i].roundToInt()}° / ${d.dMin[i].roundToInt()}°", cx, 1295f, 34f, colW * 0.92f)
    }
    return bmp
}

fun shareWeather(a: Activity) {
    if (Build.VERSION.SDK_INT < 29) {
        Toast.makeText(a, "Partajarea ca imagine cere Android 10 sau mai nou.", Toast.LENGTH_LONG).show()
        return
    }
    val app = a.applicationContext
    Thread {
        val d = fetchCur(app)
        if (d == null) {
            a.runOnUiThread { Toast.makeText(a, "Nu am date de partajat. Verifică internetul.", Toast.LENGTH_LONG).show() }
            return@Thread
        }
        try {
            val bmp = cardBitmap(d)
            val cv = ContentValues()
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "vremea_" + System.currentTimeMillis() + ".png")
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VremeaLive")
            val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri == null) throw Exception("fara uri")
            val os = app.contentResolver.openOutputStream(uri) ?: throw Exception("fara stream")
            os.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            a.runOnUiThread {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "image/png"
                i.putExtra(Intent.EXTRA_STREAM, uri)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                a.startActivity(Intent.createChooser(i, "Partajează vremea"))
            }
        } catch (e: Exception) {
            a.runOnUiThread { Toast.makeText(a, "Nu am putut crea imaginea.", Toast.LENGTH_LONG).show() }
        }
    }.start()
}
