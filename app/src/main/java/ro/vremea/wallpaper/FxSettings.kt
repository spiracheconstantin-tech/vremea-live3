package ro.vremea.wallpaper

import android.app.AlertDialog
import android.content.Context

internal fun wallpaperFxSettings(context: Context) {
    val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    val keys = arrayOf(
        "wp_fx_aurora", "wp_fx_shadows", "wp_fx_butterflies", "wp_fx_grass",
        "wp_fx_lake", "wp_fx_planets", "wp_fx_storm_glow", "wp_fx_transition",
        "wp_fx_birds", "wp_stars", "wp_fx_lightning"
    )
    val labels = arrayOf(
        "Auroră boreală", "Umbre de nori", "Fluturi", "Iarbă în prim-plan",
        "Lac cu reflexii", "Planete noaptea", "Fulgerul luminează norii", "Tranziții line ale vremii",
        "Păsări", "Stele", "Fulgerul propriu-zis"
    )
    val checked = BooleanArray(keys.size) { prefs.getBoolean(keys[it], true) }
    AlertDialog.Builder(context)
        .setTitle("Efecte fundal animat")
        .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
            prefs.edit().putBoolean(keys[which], isChecked).apply()
        }
        .setPositiveButton("Gata", null)
        .show()
}
