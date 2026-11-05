package ro.vremea.wallpaper

import android.content.Context
import android.os.SystemClock

private val lock = Any()
private var memo: CurData? = null
private var memoKey = ""
private var memoAt = 0L

// Toate widgeturile folosesc mai întâi prognoza curentă a aplicației.
// Dacă e veche sau lipsește, cer o singură prognoză online și păstrez cache-ul offline ca rezervă.
internal fun fetchCur(ctx: Context): CurData? {
    val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    val cacheAt = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE).getLong("at", 0L)
    val key = ap.getString("lat", "") + "|" + ap.getString("lon", "") + "|" + ap.getString("units", "c") + "|" + cacheAt
    synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val m = memo
        if (m != null && key == memoKey && now - memoAt < 300000L) return m

        val cached = fetchAppWeatherCache(ctx)
        val result = if (cached != null && cached.ageMinutes < 20L) {
            cached
        } else {
            fetchCurRaw(ctx) ?: cached
        }
        if (result != null) {
            memo = result
            memoKey = key
            memoAt = now
        }
        return result
    }
}
