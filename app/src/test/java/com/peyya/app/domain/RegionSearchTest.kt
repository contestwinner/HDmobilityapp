package com.peyya.app.domain

import com.peyya.app.data.SeedCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionSearchTest {
    private val routes = SeedCatalog.communitySeed(now = 0L)

    @Test
    fun cityName_findsByRegionAndAddress() {
        assertEquals(listOf("comm-gangneung"), RegionSearch.filter(routes, "강릉").map { it.id })
        assertEquals(listOf("comm-gyeongju"), RegionSearch.filter(routes, "경주시").map { it.id })
    }

    @Test
    fun provinceLongName_mapsToAddressShortForm() {
        assertEquals("경북", RegionSearch.normalizeQuery("경상북도"))
        assertEquals("제주", RegionSearch.normalizeQuery("제주특별자치도"))
        assertTrue(RegionSearch.filter(routes, "제주도").any { it.id == "comm-jeju" })
    }

    @Test
    fun placeName_andBlankQuery() {
        assertEquals(listOf("comm-daejeon"), RegionSearch.filter(routes, "성심당").map { it.id })
        assertEquals(routes.size, RegionSearch.filter(routes, "  ").size)
        assertTrue(RegionSearch.filter(routes, "평양").isEmpty())
    }

    @Test
    fun themes_listMostLikedFirst() {
        val healing = ThemeCourses.of(routes, CourseTheme.Healing)
        assertTrue(healing.isNotEmpty())
        assertEquals(healing.sortedByDescending { it.likes }, healing)
        assertTrue(healing.all { CourseTheme.Healing in it.themes })
    }
}
