package com.peyya.app.ui.recommend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.peyya.app.domain.CommunityRoute
import com.peyya.app.ui.components.AssetPhoto
import com.peyya.app.ui.components.SoftCard
import com.peyya.app.ui.theme.PeyyaBrand

internal data class PersonReviewItem(
    val routeId: String,
    val author: String,
    val routeTitle: String,
    val region: String,
    val body: String,
    val stars: Int,
    val isAuthorReview: Boolean,
    val imageModel: String?,
)

internal fun peopleReviewsOf(routes: List<CommunityRoute>): List<PersonReviewItem> = routes.flatMap { route ->
    val authorCard = PersonReviewItem(
        routeId = route.id,
        author = route.authorLabel,
        routeTitle = route.title,
        region = route.region,
        body = route.satisfactionNote,
        stars = ((route.likes % 2) + 4).coerceIn(4, 5),
        isAuthorReview = true,
        imageModel = route.reviewImageModel,
    )
    val commentCards = route.comments.map { c ->
        PersonReviewItem(
            routeId = route.id,
            author = c.authorLabel,
            routeTitle = route.title,
            region = route.region,
            body = c.body,
            stars = 4,
            isAuthorReview = false,
            imageModel = c.imageModel,
        )
    }
    listOf(authorCard) + commentCards
}

/** Free-text region search ("강릉", "부산시", "경상북도" all work). */
@Composable
internal fun RegionSearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        placeholder = { Text("지역으로 찾기 (예: 강릉, 부산, 제주)") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
internal fun NoRegionResult(query: String) {
    SoftCard {
        Text("‘${query.trim()}’ 지역 코스가 아직 없어요.", style = MaterialTheme.typography.titleSmall)
        Text(
            "강릉, 부산, 경주, 전주, 제주, 가평, 대전, 여수, 서울, 분당으로 찾아보세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun PersonReviewCard(
    item: PersonReviewItem,
    onOpen: () -> Unit,
) {
    SoftCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                color = PeyyaBrand.copy(alpha = 0.12f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        item.author.take(1),
                        color = PeyyaBrand,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.author, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${item.region} · ${item.routeTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row {
                repeat(item.stars) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = PeyyaBrand,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        item.imageModel?.let { img ->
            Spacer(Modifier.height(10.dp))
            AssetPhoto(
                img,
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp)),
                item.routeTitle,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(item.body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (item.isAuthorReview) "코스 작성자 후기 · 탭하여 상세" else "이용자 댓글 · 탭하여 상세",
            style = MaterialTheme.typography.labelSmall,
            color = PeyyaBrand,
        )
    }
}
