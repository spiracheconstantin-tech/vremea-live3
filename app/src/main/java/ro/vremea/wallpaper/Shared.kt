package ro.vremea.wallpaper

import android.content.Context
import android.os.SystemClock

private val lock = Any()
private var memo: CurData? = null
private var memoKey = ""
private var memoAt = 0L

// Toate widgeturile si notificarea din bara de stare folosesc aceleasi date, reimprospatate la 5 minute
internal fun fetchCur(ctx: Context): CurData? {
    val ap = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    val key = ap.getString("lat", "") + "|" + ap.getString("lon", "") + "|" + ap.getString("units", "c")
    synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val m = memo
        if (m != null && key == memoKey && now - memoAt < 300000L) return m
        val d = fetchCurRaw(ctx)
        if (d != null) {
            memo = d
            memoKey = key
            memoAt = now
        }
        return d
    }
}
