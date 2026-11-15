package com.peyya.app.ui.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peyya.app.data.PeyyaImages
import com.peyya.app.domain.CommunityRoute
import com.peyya.app.domain.CourseTheme
import com.peyya.app.domain.ThemeCourses
import com.peyya.app.ui.components.RoutePhotoRow
import com.peyya.app.ui.components.SectionTitle

/** Plan-tab picks by mood (힐링·체험·맛집 …) instead of by region. */
@Composable
fun ThemeCourseSection(routes: List<CommunityRoute>, onAdopt: (String) -> Unit) {
    var theme by rememberSaveable { mutableStateOf(CourseTheme.Healing) }
    val courses = ThemeCourses.of(routes, theme)
    SectionTitle("테마별 추천 코스")
    Text(
        "오늘 하고 싶은 걸 고르면 맞는 코스를 보여 드려요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CourseTheme.entries.forEach { t ->
            FilterChip(selected = theme == t, onClick = { theme = t }, label = { Text(t.labelKo) })
        }
    }
    Spacer(Modifier.height(12.dp))
    courses.forEach { route ->
        RoutePhotoRow(
            title = route.title,
            imageModel = route.reviewImageModel ?: PeyyaImages.forRegion(route.region),
            meta = "${route.region} · ${route.stops.joinToString(" → ") { it.name }}",
            actionLabel = "이 코스로 일정 잡기",
            onAction = { onAdopt(route.id) },
        )
        Spacer(Modifier.height(12.dp))
    }
}
