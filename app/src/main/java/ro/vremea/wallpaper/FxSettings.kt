package ro.vremea.wallpaper

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

internal fun wallpaperFxSettings(context: Context) {
    val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    val keys = arrayOf(
        "wp_fx_aurora", "wp_fx_shadows", "wp_fx_butterflies", "wp_fx_grass",
        "wp_fx_lake", "wp_fx_planets", "wp_fx_storm_glow", "wp_fx_transition",
        "wp_fx_birds", "wp_stars", "wp_fx_lightning", "wp_fx_leaves", "wp_fx_frost",
        "wp_fx_blossoms", "wp_fx_pollen", "wp_fx_fireflies", "wp_fx_dew"
    )
    val labels = arrayOf(
        "Auroră boreală", "Umbre de nori", "Fluturi", "Iarbă în prim-plan",
        "Lac cu reflexii", "Planete noaptea", "Fulgerul luminează norii", "Tranziții line ale vremii",
        "Păsări", "Stele", "Fulgerul propriu-zis", "Frunze de toamnă", "Brumă de dimineață",
        "Flori de primăvară", "Polen în aer", "Licurici vara", "Rouă dimineața"
    )
    val checked = BooleanArray(keys.size) { prefs.getBoolean(keys[it], true) }
    AlertDialog.Builder(context)
        .setTitle("Efecte wallpaper și anotimpuri")
        .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
            prefs.edit().putBoolean(keys[which], isChecked).apply()
        }
        .setNeutralButton("Preset aspect") { _, _ -> showWallpaperPresets(context, keys) }
        .setPositiveButton("Gata", null)
        .show()
}

private fun showWallpaperPresets(context: Context, effectKeys: Array<String>) {
    val names = arrayOf("Natural", "Mai animat", "Economie de baterie")
    AlertDialog.Builder(context)
        .setTitle("Alege stilul wallpaper")
        .setItems(names) { _, which ->
            val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
            val edit = prefs.edit()
            when (which) {
                0 -> {
                    edit.putString("wp_quality", "normal").putBoolean("wp_reduce_motion", false)
                    effectKeys.forEach { edit.putBoolean(it, true) }
                }
                1 -> {
                    edit.putString("wp_quality", "max").putBoolean("wp_reduce_motion", false)
                    effectKeys.forEach { edit.putBoolean(it, true) }
                }
                else -> {
                    edit.putString("wp_quality", "eco").putBoolean("wp_reduce_motion", true)
                    effectKeys.forEach { edit.putBoolean(it, it == "wp_fx_grass" || it == "wp_stars" || it == "wp_fx_lightning") }
                }
            }
            edit.apply()
            Toast.makeText(context, "Stil aplicat: ${names[which]}", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Anulează", null)
        .show()
}


