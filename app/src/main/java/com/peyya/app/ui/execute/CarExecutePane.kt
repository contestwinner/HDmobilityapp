package com.peyya.app.ui.execute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peyya.app.domain.RouteMetricsEstimator
import com.peyya.app.domain.SavedRoute
import com.peyya.app.ui.components.CourseRouteBoard
import com.peyya.app.ui.components.PrimaryCta
import com.peyya.app.ui.theme.PeyyaBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BOARD_HEADER_DP = 72

/**
 * Landscape head-unit layout: the head unit is wide but short, so the primary
 * 「지금 출발」 action and the route map must both be visible without scrolling.
 */
@Composable
fun CarExecutePane(
    contentPadding: PaddingValues,
    route: SavedRoute,
    otherCount: Int,
    onStartRoute: (SavedRoute) -> Unit,
    onPlanCta: () -> Unit,
) {
    val clock = SimpleDateFormat("HH:mm", Locale.KOREA)
    val window = RouteMetricsEstimator.leaveHomeWindow(route.stops)
    val range = window?.let {
        "${clock.format(Date(it.leaveAtEpochMs))} 출발 → ${clock.format(Date(it.arriveHomeEpochMs))} 귀가"
    } ?: "시간 미정"
    Row(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(0.85f).fillMaxHeight()) {
            // Info scrolls on short head units; the primary action stays pinned and full-size.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("오늘의 계획", style = MaterialTheme.typography.labelLarge, color = PeyyaBrand)
                Spacer(Modifier.height(4.dp))
                Text(route.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(range, style = MaterialTheme.typography.titleMedium)
                window?.let {
                    Text(
                        "이동 약 ${it.driveMinutes}분 · 체류 약 ${it.visitMinutes}분 (예상)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    route.stops.mapIndexed { i, stop -> "${i + 1}. ${stop.name}" }.joinToString("  →  "),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryCta("지금 출발", true) { onStartRoute(route) }
            TextButton(onClick = onPlanCta) {
                val more = if (otherCount > 0) " · 다른 계획 ${otherCount}개" else ""
                Text("새 계획 세우기$more", color = PeyyaBrand)
            }
        }
        BoxWithConstraints(Modifier.weight(1.15f).fillMaxHeight()) {
            val boardHeight = (maxHeight.value.toInt() - BOARD_HEADER_DP).coerceAtLeast(120)
            CourseRouteBoard(title = route.title, subtitle = range, stops = route.stops, heightDp = boardHeight)
        }
    }
}
