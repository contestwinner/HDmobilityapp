package com.peyya.app.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.peyya.app.data.PeyyaImages
import com.peyya.app.domain.Stop
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OsmWebMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 14.0,
    markers: List<Pair<Double, Double>> = listOf(latitude to longitude),
) {
    val html = remember(latitude, longitude, zoom, markers) {
        buildLeafletHtml(latitude, longitude, zoom, markers)
    }
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    setBackgroundColor(0x00000000)
                }
            },
            // Load only when the HTML actually changed (update runs on every recomposition).
            update = { view ->
                if (view.tag != html) {
                    view.tag = html
                    view.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null)
                }
            },
        )
    }
}

@Composable
fun DestinationPreviewMap(
    query: String,
    modifier: Modifier = Modifier,
    heightDp: Int = 168,
) {
    val place = com.peyya.app.data.PlaceSearchCatalog.resolve(query)
        ?: com.peyya.app.data.PlaceSearchCatalog.defaultCenter
    val pinned = com.peyya.app.data.PlaceSearchCatalog.resolve(query) != null
    Column(modifier.fillMaxWidth()) {
        OsmWebMap(
            latitude = place.latitude,
            longitude = place.longitude,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .border(1.dp, Color(0xFF2F6BFF).copy(0.25f), RoundedCornerShape(16.dp)),
            zoom = if (pinned) 15.0 else 12.0,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (pinned) "${place.detail} · ${place.label}" else "지도에서 위치를 확인하세요",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Course board inspired by trip photo-route maps:
 * real OSM base + numbered circular stop photos + connecting path.
 */
@Composable
fun CourseRouteBoard(
    title: String,
    subtitle: String,
    stops: List<Stop>,
    modifier: Modifier = Modifier,
    heightDp: Int = 260,
) {
    if (stops.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
            Surface(color = Color(0xFFFF8A3D), modifier = Modifier.fillMaxWidth()) {
                Text(
                    title,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1A1A1A),
                )
            }
            Surface(color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth()) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
        RoutePhotoMap(
            stops = stops,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
        )
    }
}

/** Live OSM route map with numbered photo pins; shared by the course board and the car screen. */
@Composable
fun RoutePhotoMap(stops: List<Stop>, modifier: Modifier = Modifier) {
    if (stops.isEmpty()) return
    BoxWithConstraints(modifier.background(Color(0xFFB8D4EA))) {
        // One Web-Mercator frame drives both the Leaflet base map and the photo-pin overlay,
        // so pins and the route line sit exactly on the map (no double line / drift).
        val dens = LocalDensity.current
        val wPx = with(dens) { maxWidth.toPx() }
        val hPx = with(dens) { maxHeight.toPx() }
        val frame = remember(stops, wPx, hPx, dens.density) {
            MapFit.fit(
                stops.map { it.latitude to it.longitude },
                wPx,
                hPx,
                dens.density,
                // Keep 44dp photo pins fully inside even on short (landscape) boards.
                padding = (PIN_HALF_DP * dens.density / minOf(wPx, hPx) + 0.06).coerceIn(0.2, 0.4).toDouble(),
            )
        } ?: return@BoxWithConstraints
        OsmWebMap(
            latitude = frame.centerLat,
            longitude = frame.centerLon,
            modifier = Modifier.fillMaxSize(),
            zoom = frame.zoom,
            markers = emptyList(),
        )
        val points = remember(frame, stops) {
            stops.map { frame.project(it.latitude, it.longitude).let { (x, y) -> Offset(x, y) } }
        }
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until points.lastIndex) {
                drawLine(
                    color = Color(0xFFB91C1C),
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        points.forEachIndexed { index, p ->
            val stop = stops[index]
            val img = PeyyaImages.forStopId(stop.id)
            Box(
                Modifier
                    .offset { IntOffset((p.x - 22.dp.toPx()).roundToInt(), (p.y - 22.dp.toPx()).roundToInt()) }
                    .size(44.dp),
            ) {
                AssetPhoto(
                    img,
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                    stop.name,
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset((-2).dp, (-2).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE11D48)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

private const val PIN_HALF_DP = 26f

private fun buildLeafletHtml(
    lat: Double,
    lon: Double,
    zoom: Double,
    markers: List<Pair<Double, Double>>,
): String {
    val markerJs = markers.joinToString("\n") { (mLat, mLon) ->
        "L.marker([$mLat,$mLon]).addTo(map);"
    }
    val lineJs = if (markers.size >= 2) {
        val pts = markers.joinToString(",") { "[${it.first},${it.second}]" }
        "L.polyline([$pts],{color:'#b91c1c',weight:3,opacity:0.85}).addTo(map); map.fitBounds(L.latLngBounds([$pts]).pad(0.2));"
    } else ""
    // If unpkg/OSM are unreachable (offline emulator, car without data), Leaflet never
    // defines `L`; we then draw a self-contained schematic so the card is never blank.
    return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
        html,body,#map{margin:0;height:100%;background:#b8d4ea}
        .leaflet-control-attribution{font-size:9px}
        #off{position:relative;height:100%;background:linear-gradient(135deg,#cfe0ee,#b8d4ea)}
        #off span{position:absolute;right:8px;bottom:6px;font:11px sans-serif;color:#41566b}
        </style>
        </head><body><div id="map"></div><script>
        if (typeof L === 'undefined') {
          document.getElementById('map').outerHTML = '<div id="off">${offlineSvg(markers)}<span>오프라인 간이 지도</span></div>';
        } else {
          var map=L.map('map',{zoomControl:false,zoomSnap:0,dragging:false,touchZoom:false,doubleClickZoom:false,scrollWheelZoom:false}).setView([$lat,$lon],$zoom);
          map.attributionControl.setPrefix(false);
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
          $markerJs
          $lineJs
        }
        </script></body></html>
    """.trimIndent()
}

/** Schematic SVG (viewBox 0..100) of the stops in route order, used when tiles cannot load. */
private fun offlineSvg(markers: List<Pair<Double, Double>>): String {
    if (markers.isEmpty()) return ""
    val minLat = markers.minOf { it.first }
    val maxLat = markers.maxOf { it.first }
    val minLon = markers.minOf { it.second }
    val maxLon = markers.maxOf { it.second }
    val dLat = (maxLat - minLat).coerceAtLeast(0.008)
    val dLon = (maxLon - minLon).coerceAtLeast(0.008)
    val pts = markers.map { (mLat, mLon) ->
        val x = 15 + (mLon - minLon) / dLon * 70
        val y = 15 + (maxLat - mLat) / dLat * 70
        "%.1f,%.1f".format(java.util.Locale.US, x, y)
    }
    val dots = pts.joinToString("") { p ->
        val (x, y) = p.split(",")
        "<circle cx=\\'$x\\' cy=\\'$y\\' r=\\'3.2\\' fill=\\'#e11d48\\' stroke=\\'#fff\\' stroke-width=\\'1\\'/>"
    }
    val line = "<polyline points=\\'${pts.joinToString(" ")}\\' fill=\\'none\\' stroke=\\'#b91c1c\\' stroke-width=\\'1.4\\' stroke-dasharray=\\'3 2\\'/>"
    return "<svg viewBox=\\'0 0 100 100\\' preserveAspectRatio=\\'xMidYMid meet\\' width=\\'100%\\' height=\\'100%\\'>$line$dots</svg>"
}
