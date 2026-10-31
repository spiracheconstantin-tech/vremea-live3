package ro.vremea.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.net.URL

private const val REPO = "spiracheconstantin-tech/vremea-live3"

// Verifica pagina Releases a repository-ului si ofera descarcarea versiunii noi
fun checkUpdate(a: Activity) {
    Thread {
        try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/tags/latest").openConnection()
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val j = JSONObject(conn.getInputStream().bufferedReader().readText())
            val body = j.optString("body", "")
            val n = Regex("(\\d+)").find(body)?.value?.toIntOrNull() ?: 0
            @Suppress("DEPRECATION")
            val cur = try { a.packageManager.getPackageInfo(a.packageName, 0).versionCode } catch (e: Exception) { 0 }
            val assets = j.optJSONArray("assets")
            val url = if (assets != null && assets.length() > 0) assets.getJSONObject(0).optString("browser_download_url", "") else ""
            a.runOnUiThread {
                if (n > cur && url.isNotEmpty()) {
                    AlertDialog.Builder(a).setTitle("Versiune nouă")
                        .setMessage("Build $n este disponibil (acum ai build $cur). Îl descarci?")
                        .setPositiveButton("Descarcă") { _, _ -> a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .setNegativeButton("Mai târziu", null).show()
                } else {
                    Toast.makeText(a, "Ai ultima versiune (build $cur)", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            a.runOnUiThread {
                Toast.makeText(a, "Nu am putut verifica actualizările (repository privat sau fără internet).", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}
