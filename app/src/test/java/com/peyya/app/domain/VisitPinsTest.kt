package com.peyya.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisitPinsTest {
    private fun visit(id: String, region: String, at: Long, stars: Int? = null, demo: Boolean = false) =
        VisitRecord(id, id, region, 37.0, 127.0, at, stars = stars, demo = demo)

    @Test
    fun regionOf_cityLevel() {
        assertEquals("서울", RegionOf.address("서울 용산구 서빙고로 137"))
        assertEquals("경주", RegionOf.address("경북 경주시 불국로 385"))
        assertEquals("가평", RegionOf.address("경기 가평군 상면 수목원로 432"))
        assertEquals("제주", RegionOf.address("제주 서귀포시 성산읍 일출로"))
    }

    @Test
    fun group_newestRegionFirst_withAverage() {
        val pins = VisitPins.group(
            listOf(visit("a", "서울", 1, 5), visit("b", "강릉", 3, 4), visit("c", "서울", 2, 3)),
        )
        assertEquals(listOf("강릉", "서울"), pins.map { it.region })
        assertEquals(listOf("c", "a"), pins[1].visits.map { it.stopId })
        assertEquals(4.0, pins[1].averageStars ?: 0.0, 0.001)
    }

    @Test
    fun ratings_goToLatestRealVisitOnly() {
        val visits = listOf(visit("museum", "서울", 1, demo = true), visit("museum", "서울", 5), visit("museum", "서울", 3))
        val review = StopReview("museum", "박물관", 4, "좋아요", Sentiment.Positive)
        val rated = VisitPins.withRatings(visits, listOf(review))
        assertNull(rated[0].stars)
        assertEquals(4, rated[1].stars)
        assertNull(rated[2].stars)
    }

    @Test
    fun dateLabel_isKoreanInSeoulTime() {
        // 2026-10-18 05:00 UTC == 14:00 KST
        assertEquals("2026년 10월 18일 (일)", VisitPins.dateLabel(1_792_299_600_000L))
    }
}
