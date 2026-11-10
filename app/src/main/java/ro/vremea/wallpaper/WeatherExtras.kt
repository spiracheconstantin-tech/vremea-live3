package ro.vremea.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import java.util.Locale
import java.text.Normalizer
import kotlin.concurrent.thread

internal fun showRadarMap(activity: Activity, latitude: Double, longitude: Double) {
    val web = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClient()
        isVerticalScrollBarEnabled = false
    }
    val lat = String.format(Locale.US, "%.5f", latitude)
    val lon = String.format(Locale.US, "%.5f", longitude)
    val html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>html,body,#map{height:100%;margin:0;background:#17202b}#bar{position:absolute;z-index:999;bottom:8px;left:8px;right:8px;background:#fffffff0;padding:9px;border-radius:10px;font:14px sans-serif}#slider{width:100%}</style>
        </head><body><div id="map"></div><div id="bar"><div id="status">Se încarcă radarul…</div><input id="slider" type="range" min="0" max="0" value="0"><button id="play">▶ Redă ultimele cadre</button><div>Date radar: <a href="https://www.rainviewer.com/">Weather data by RainViewer</a> · Hartă: © OpenStreetMap</div></div>
        <script>
        const map=L.map('map').setView([$lat,$lon],6);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
        let frames=[],layer=null,timer=null;
        const slider=document.getElementById('slider'),status=document.getElementById('status'),play=document.getElementById('play');
        function show(i){if(!frames.length)return;i=Math.max(0,Math.min(i,frames.length-1));if(layer)map.removeLayer(layer);const f=frames[i];const url=f.host+f.path+'/256/{z}/{x}/{y}/2/1_1.png';layer=L.tileLayer(url,{maxZoom:7,opacity:.72}).addTo(map);slider.value=i;status.textContent='Cadru '+(i+1)+'/'+frames.length+' · '+new Date(f.time*1000).toLocaleTimeString('ro-RO',{hour:'2-digit',minute:'2-digit'});}
        fetch('https://api.rainviewer.com/public/weather-maps.json').then(r=>r.json()).then(d=>{frames=(d.radar&&d.radar.past||[]).map(x=>({time:x.time,path:x.path,host:d.host}));if(!frames.length)throw Error('Fără cadre radar disponibile');slider.max=frames.length-1;show(frames.length-1);}).catch(()=>status.textContent='Radar indisponibil momentan. Verifică internetul.');
        slider.addEventListener('input',()=>show(+slider.value));play.addEventListener('click',()=>{if(timer){clearInterval(timer);timer=null;play.textContent='▶ Redă ultimele cadre';return}play.textContent='⏸ Oprește';timer=setInterval(()=>show((+slider.value+1)%frames.length),800);});
        </script></body></html>
    """.trimIndent()
    val dialog = Dialog(activity)
    dialog.setTitle("Radar de precipitații · ultimele 2 ore")
    dialog.setContentView(web)
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * .84f).toInt())
    web.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
}

private data class OfficialWarning(val title: String, val summary: String, val link: String)

internal fun showOfficialWeatherAlerts(context: Context, selectedPlace: String = "") {
    Toast.makeText(context, "Se încarcă avertizările oficiale…", Toast.LENGTH_SHORT).show()
    thread(name = "meteoalarm-feed") {
        try {
            val conn = URL("https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-romania").openConnection().apply {
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", "VremeaLive/1.0")
                setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml")
            }
            val parser = android.util.Xml.newPullParser()
            conn.getInputStream().use { parser.setInput(it, "UTF-8"); val warnings = ArrayList<OfficialWarning>(); var inEntry = false; var title = ""; var summary = ""; var link = "https://meteoalarm.org/en/live/"; var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                            "entry" -> { inEntry = true; title = ""; summary = ""; link = "https://meteoalarm.org/en/live/" }
                            "title" -> if (inEntry) title = parser.nextText().trim()
                            "summary" -> if (inEntry) summary = parser.nextText().trim()
                            "link" -> if (inEntry) link = parser.getAttributeValue(null, "href") ?: link
                        }
                        XmlPullParser.END_TAG -> if (parser.name.equals("entry", true)) {
                            if (title.isNotBlank()) warnings.add(OfficialWarning(title, summary, link))
                            inEntry = false
                        }
                    }
                    event = parser.next()
                }
                val act = context as? Activity ?: return@thread
                val areaHint = selectedPlace.split(",").getOrNull(1)?.trim().orEmpty()
                fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
                val matches = if (areaHint.isBlank()) emptyList() else warnings.filter { norm(it.title + " " + it.summary).contains(norm(areaHint)) }
                val shown = if (matches.isNotEmpty()) matches else warnings
                val areaNote = when {
                    areaHint.isBlank() -> "Feed pentru mai multe zone"
                    matches.isNotEmpty() -> "Potrivire după aria: $areaHint"
                    else -> "Nu am putut potrivi automat aria «$areaHint»; sunt afișate toate zonele"
                }
                act.runOnUiThread {
                    if (act.isFinishing) return@runOnUiThread
                    if (warnings.isEmpty()) {
                        AlertDialog.Builder(context).setTitle("Avertizări oficiale").setMessage("Feedul oficial nu conține avertizări active sau nu a returnat rezultate.").setPositiveButton("Închide", null).show()
                    } else {
                        val rows = shown.take(20).map { w ->
                            val plain = Html.fromHtml(w.summary, Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim().take(190)
                            if (plain.isBlank()) w.title else "${w.title}\n$plain"
                        }.toTypedArray()
                        AlertDialog.Builder(context).setTitle("Avertizări oficiale · $areaNote")
                            .setItems(rows) { _, which -> try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(shown[which].link))) } catch (_: Exception) { } }
                            .setPositiveButton("Închide", null)
                            .setNeutralButton("MeteoAlarm · licență CC BY 4.0") { _, _ -> context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://creativecommons.org/licenses/by/4.0/"))) }
                            .show()
                    }
                }
            }
        } catch (_: Exception) {
            (context as? Activity)?.runOnUiThread { AlertDialog.Builder(context).setTitle("Avertizări oficiale").setMessage("Nu am putut încărca feedul MeteoAlarm. Verifică internetul sau consultă meteoalarm.org.").setPositiveButton("Deschide MeteoAlarm") { _, _ -> try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://meteoalarm.org/en/live/"))) } catch (_: Exception) { } }.setNegativeButton("Închide", null).show() }
        }
    }
}


internal fun chooseWallpaperScene(context: Context) {
    val labels = arrayOf("Dealuri · case și copaci", "Pădure · mai mulți copaci", "Lac · mal deschis și reflexii")
    val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    val selected = prefs.getInt("wp_scene", 0).coerceIn(0, labels.lastIndex)
    AlertDialog.Builder(context)
        .setTitle("Peisajul fundalului animat")
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            prefs.edit().putInt("wp_scene", which).commit()
            dialog.dismiss()
            Toast.makeText(context, "Peisaj aplicat: ${labels[which]}", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Închide", null)
        .show()
}




