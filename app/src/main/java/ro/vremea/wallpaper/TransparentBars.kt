package ro.vremea.wallpaper

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowManager

object TransparentBars {
    fun apply(activity: Activity) {
        val window = activity.window
        
        // Dezactivează limitările de decorare ale ferestrei pentru a permite layout Edge-to-Edge complet
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        
        // Forțează barele de sistem să devină complet transparente, eliminând fundalul alb implicit
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Configurează flag-urile de vizibilitate pentru a extinde layout-ul sub bare
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or 
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    
        // Schimbă culoarea pictogramelor de sus (ceas, baterie) în alb, optim pentru gradientele închise ale aplicației
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Nu adăugăm SYSTEM_UI_FLAG_LIGHT_STATUS_BAR pentru a păstra pictogramele albe
            window.decorView.systemUiVisibility = flags
        } else {
            window.decorView.systemUiVisibility = flags
        }

        // Permite afișarea în zona decupajului camerei foto (Notch) pe telefoanele Xiaomi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
