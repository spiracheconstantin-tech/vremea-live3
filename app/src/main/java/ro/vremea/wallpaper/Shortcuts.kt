package ro.vremea.wallpaper

import android.app.Activity

// Executa comanda primita de la scurtaturile de pe pictograma aplicatiei
fun handleShortcut(a: Activity) {
    val cmd = a.intent?.getStringExtra("cmd") ?: return
    a.intent.removeExtra("cmd")
    when (cmd) {
        "share" -> shareWeather(a)
        "test" -> testMode(a)
        "update" -> checkUpdate(a)
    }
}
