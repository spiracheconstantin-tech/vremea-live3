package ro.vremea.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Salveaza eroarea cand aplicatia sau fundalul se opresc, ca sa o poti trimite
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                getSharedPreferences("crash", Context.MODE_PRIVATE).edit()
                    .putString("report", stamp + "\nFir: " + t.name + "\n" + sw.toString().take(3500))
                    .commit()
            } catch (x: Exception) { }
            old?.uncaughtException(t, e)
        }
    }
}

fun showCrashIfAny(a: Activity) {
    val pr = a.getSharedPreferences("crash", Context.MODE_PRIVATE)
    val rep = pr.getString("report", null) ?: return
    pr.edit().remove("report").apply()
    AlertDialog.Builder(a).setTitle("Aplicația s-a oprit data trecută")
        .setMessage(rep)
        .setPositiveButton("Copiază") { _, _ ->
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("raport", rep))
            Toast.makeText(a, "Copiat. Lipește-l în chat.", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Închide", null).show()
}
