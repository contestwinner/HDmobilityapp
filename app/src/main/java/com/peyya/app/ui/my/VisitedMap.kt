package com.peyya.app.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.peyya.app.domain.VisitPin
import com.peyya.app.domain.VisitPins
import com.peyya.app.domain.VisitRecord
import com.peyya.app.ui.components.MapFrame
import com.peyya.app.ui.components.MapFit
import com.peyya.app.ui.components.OsmWebMap
import com.peyya.app.ui.components.SoftCard
import com.peyya.app.ui.theme.PeyyaBrand
import kotlin.math.roundToInt

/** Mainland + Jeju, so the whole country is always in frame regardless of where pins are. */
private val KOREA_BOUNDS = listOf(33.15 to 125.95, 38.45 to 129.55)
private val PinRed = Color(0xFFE11D48)

/** National map of visited regions; tapping a pin shows when the family went and how they rated it. */
@Composable
fun VisitedKoreaMap(visits: List<VisitRecord>, modifier: Modifier = Modifier) {
    val pins = remember(visits) { VisitPins.group(visits) }
    var selected by rememberSaveable { mutableStateOf(pins.firstOrNull()?.region) }
    val current = pins.firstOrNull { it.region == selected } ?: pins.firstOrNull()
    Column(modifier.fillMaxWidth()) {
        KoreaPinBoard(pins, current?.region) { selected = it }
        Spacer(Modifier.height(10.dp))
        if (current == null) {
            SoftCard { Text("아직 도착한 곳이 없어요. 루트를 달리면 여기에 핀이 쌓입니다.") }
        } else {
            VisitPinCard(current)
        }
    }
}

@Composable
private fun KoreaPinBoard(pins: List<VisitPin>, selected: String?, onSelect: (String) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFB8D4EA)),
    ) {
        val dens = LocalDensity.current
        val wPx = with(dens) { maxWidth.toPx() }
        val hPx = with(dens) { maxHeight.toPx() }
        val frame = remember(wPx, hPx, dens.density) {
            MapFit.fit(KOREA_BOUNDS, wPx, hPx, dens.density, padding = 0.04)
        } ?: return@BoxWithConstraints
        OsmWebMap(frame.centerLat, frame.centerLon, Modifier.fillMaxSize(), zoom = frame.zoom, markers = emptyList())
        pins.forEach { pin -> PinMarker(pin, frame, pin.region == selected) { onSelect(pin.region) } }
    }
}

@Composable
private fun PinMarker(pin: VisitPin, frame: MapFrame, selected: Boolean, onClick: () -> Unit) {
    val size: Dp = if (selected) 34.dp else 26.dp
    val (x, y) = frame.project(pin.latitude, pin.longitude)
    Box(
        Modifier
            .offset { IntOffset((x - size.toPx() / 2).roundToInt(), (y - size.toPx() / 2).roundToInt()) }
            .size(size)
            .clip(CircleShape)
            .background(if (selected) PeyyaBrand else PinRed)
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${pin.region} 방문 ${pin.visits.size}곳" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${pin.visits.size}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun VisitPinCard(pin: VisitPin) {
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pin.region, style = MaterialTheme.typography.titleMedium)
            pin.averageStars?.let {
                Text("평균 ★ ${"%.1f".format(it)}", color = PeyyaBrand, style = MaterialTheme.typography.labelLarge)
            }
        }
        pin.visits.forEach { visit ->
            Spacer(Modifier.height(10.dp))
            VisitRow(visit)
        }
    }
}

@Composable
private fun VisitRow(visit: VisitRecord) {
    Column {
        Text(visit.placeName, style = MaterialTheme.typography.titleSmall)
        Text(
            VisitPins.dateLabel(visit.visitedAtEpochMs) + if (visit.demo) " · 데모 기록" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val stars = visit.stars
        Text(
            if (stars == null) "아직 평점이 없어요" else "★".repeat(stars) + "☆".repeat(5 - stars),
            color = if (stars == null) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF59E0B),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (visit.comment.isNotBlank()) Text(visit.comment, style = MaterialTheme.typography.bodyMedium)
    }
}
