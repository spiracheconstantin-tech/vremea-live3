package ro.vremea.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import org.json.JSONObject

// Salveaza orasele si setarile ca text copiat si le restaureaza dintr-un text lipit
fun backupSettings(a: Activity) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val o = JSONObject()
    for ((k, v) in pr.all) {
        val e = JSONObject()
        when (v) {
            is String -> { e.put("t", "s"); e.put("v", v) }
            is Boolean -> { e.put("t", "b"); e.put("v", v) }
            is Int -> { e.put("t", "i"); e.put("v", v) }
            is Long -> { e.put("t", "l"); e.put("v", v) }
            is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
            else -> continue
        }
        o.put(k, e)
    }
    val txt = o.toString()
    val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("setari", txt))
    AlertDialog.Builder(a).setTitle("Setări copiate")
        .setMessage("Textul cu orașele și setările (" + txt.length + " caractere) e în clipboard. Lipește-l într-un loc sigur, de exemplu în Notițe sau în chatul cu tine însuți.")
        .setPositiveButton("OK", null).show()
}

fun restoreSettings(a: Activity) {
    val et = EditText(a)
    et.hint = "Lipește aici textul salvat"
    et.minLines = 4
    AlertDialog.Builder(a).setTitle("Restaurează setările").setView(et)
        .setPositiveButton("Restaurează") { _, _ ->
            try {
                val o = JSONObject(et.text.toString().trim())
                val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
                val ed = pr.edit()
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val e = o.getJSONObject(k)
                    when (e.getString("t")) {
                        "s" -> ed.putString(k, e.getString("v"))
                        "b" -> ed.putBoolean(k, e.getBoolean("v"))
                        "i" -> ed.putInt(k, e.getInt("v"))
                        "l" -> ed.putLong(k, e.getLong("v"))
                        "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    }
                }
                ed.apply()
                scheduleSmart(a)
                if (pr.getBoolean("alerts", false)) scheduleAlerts(a)
                Toast.makeText(a, "Setări restaurate", Toast.LENGTH_SHORT).show()
                a.recreate()
            } catch (e: Exception) {
                Toast.makeText(a, "Text invalid. Verifică să fi lipit tot.", Toast.LENGTH_LONG).show()
            }
        }
        .setNegativeButton("Anulează", null).show()
}
