package com.peyya.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One arrival at a place, optionally rated in the post-trip review. */
data class VisitRecord(
    val stopId: String,
    val placeName: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val visitedAtEpochMs: Long,
    val stars: Int? = null,
    val comment: String = "",
    /** Seeded sample history (shown with a 데모 label), not the family's real arrival. */
    val demo: Boolean = false,
)

/** All visits in one region, drawn as a single pin on the national map. */
data class VisitPin(
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val visits: List<VisitRecord>,
) {
    val averageStars: Double? get() = visits.mapNotNull { it.stars }.takeIf { it.isNotEmpty() }?.average()
}

object VisitPins {
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

    /** Groups visits by region; newest region first, newest visit first inside a pin. */
    fun group(visits: List<VisitRecord>): List<VisitPin> = visits
        .groupBy { it.region }
        .map { (region, list) ->
            VisitPin(
                region = region,
                latitude = list.map { it.latitude }.average(),
                longitude = list.map { it.longitude }.average(),
                visits = list.sortedByDescending { it.visitedAtEpochMs },
            )
        }
        .sortedByDescending { pin -> pin.visits.first().visitedAtEpochMs }

    /** Attaches review stars to the latest real (non-demo) visit of each reviewed stop. */
    fun withRatings(visits: List<VisitRecord>, reviews: List<StopReview>): List<VisitRecord> {
        val byStop = reviews.associateBy { it.stopId }
        val latestIndex = visits.withIndex()
            .filter { !it.value.demo && it.value.stopId in byStop }
            .groupBy { it.value.stopId }
            .mapValues { (_, list) -> list.maxBy { it.value.visitedAtEpochMs }.index }
        return visits.mapIndexed { index, visit ->
            val review = byStop[visit.stopId]
            if (review == null || latestIndex[visit.stopId] != index) visit
            else visit.copy(stars = review.stars, comment = review.comment)
        }
    }

    fun dateLabel(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(seoul).format(dateFormat)
}

/** City-level region from a Korean address ("경북 경주시 불국로" → "경주", "서울 용산구 …" → "서울"). */
object RegionOf {
    private val metros = setOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "제주")

    fun address(address: String): String {
        val tokens = address.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = tokens.firstOrNull() ?: return "기타"
        if (first in metros || tokens.size < 2) return first
        val city = tokens[1]
        return if (city.length > 2 && (city.endsWith("시") || city.endsWith("군"))) city.dropLast(1) else city
    }
}
