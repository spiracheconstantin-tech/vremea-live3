package ro.vremea.wallpaper

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowManager

object TransparentBars {
    fun apply(activity: Activity) {
        val window = activity.window
        
        // Elimină flag-urile vechi care pot păstra fundaluri solide din temă
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_REFILLS ?: 0x80000000.toInt())
        
        // Forțează ecranul complet (Edge-to-Edge) hardware
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        // Setează culorile de transparență absolută
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Activează modul fullscreen pe tot ecranul pentru layout-ul din Kotlin
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or 
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        // Permite afișarea corectă sub decupajul camerei frontale de pe Xiaomi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
