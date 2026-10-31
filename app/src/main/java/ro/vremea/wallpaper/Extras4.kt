package ro.vremea.wallpaper

import android.app.Activity
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import org.json.JSONArray
import kotlin.math.abs

// Trece la orasul urmator sau anterior din lista de favorite
fun cycleCity(a: Activity, dir: Int) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val fa = try { JSONArray(pr.getString("favs", "[]")) } catch (e: Exception) { JSONArray() }
    if (fa.length() == 0) {
        Toast.makeText(a, "Nu ai orașe salvate. Caută unul din meniu.", Toast.LENGTH_SHORT).show()
        return
    }
    val cur = pr.getString("name", "")
    var idx = -1
    for (i in 0 until fa.length()) {
        if (fa.getJSONObject(i).getString("name") == cur) idx = i
    }
    val n = fa.length()
    val ni = if (idx < 0) (if (dir > 0) 0 else n - 1) else ((idx + dir) % n + n) % n
    val o = fa.getJSONObject(ni)
    pr.edit()
        .putString("name", o.getString("name"))
        .putString("lat", o.getDouble("lat").toString())
        .putString("lon", o.getDouble("lon").toString())
        .apply()
    a.recreate()
}

// Glisare stanga-dreapta pe bara de sus schimba orasul
fun attachCitySwipe(a: Activity, view: View) {
    val gd = GestureDetector(a, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > 900f && abs(vx) > abs(vy) * 1.5f) {
                cycleCity(a, if (vx < 0f) 1 else -1)
                return true
            }
            return false
        }
    })
    view.isClickable = true
    view.setOnTouchListener { _, ev ->
        gd.onTouchEvent(ev)
        false
    }
}

fun toggleFlat(a: Activity) {
    val pr = a.getSharedPreferences("app", Context.MODE_PRIVATE)
    val on = !pr.getBoolean("wp_flat", false)
    pr.edit().putBoolean("wp_flat", on).apply()
    Toast.makeText(a, "Stil fundal: " + (if (on) "desenat" else "realist") + ". Se aplică la următoarea afișare.", Toast.LENGTH_LONG).show()
}
