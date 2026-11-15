package com.peyya.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFitTest {
    private val museum = 37.52386 to 126.98047
    private val food = 37.53120 to 126.96580

    @Test
    fun emptyOrZeroSize_returnsNull() {
        assertNull(MapFit.fit(emptyList(), 100f, 100f, 1f))
        assertNull(MapFit.fit(listOf(museum), 0f, 100f, 1f))
    }

    @Test
    fun allPoints_landInsidePaddedBox() {
        val frame = MapFit.fit(listOf(museum, food), 1000f, 600f, 2.6f, padding = 0.16)
        requireNotNull(frame)
        listOf(museum, food).forEach { (lat, lon) ->
            val (x, y) = frame.project(lat, lon)
            assertTrue("x=$x", x >= 1000f * 0.16f - 1f && x <= 1000f * 0.84f + 1f)
            assertTrue("y=$y", y >= 600f * 0.16f - 1f && y <= 600f * 0.84f + 1f)
        }
    }

    @Test
    fun center_projectsToMiddle() {
        val frame = requireNotNull(MapFit.fit(listOf(museum, food), 800f, 400f, 1f))
        val (x, y) = frame.project(frame.centerLat, frame.centerLon)
        assertEquals(400f, x, 0.5f)
        assertEquals(200f, y, 0.5f)
    }

    @Test
    fun zoom_isLeafletPlausibleForCityScale() {
        val frame = requireNotNull(MapFit.fit(listOf(museum, food), 1000f, 600f, 2.6f))
        assertTrue("zoom=${frame.zoom}", frame.zoom in 12.0..16.0)
    }

    @Test
    fun mercator_roundTripsLatitude() {
        val frame = requireNotNull(MapFit.fit(listOf(museum), 500f, 500f, 1f))
        assertEquals(museum.first, frame.centerLat, 1e-9)
        assertEquals(museum.second, frame.centerLon, 1e-9)
    }
}
