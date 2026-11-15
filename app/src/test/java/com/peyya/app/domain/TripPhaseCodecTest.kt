package com.peyya.app.domain

import com.peyya.app.data.PresetTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPhaseCodecTest {
    private val phases = listOf(
        TripPhase.Idle,
        TripPhase.Navigating("museum"),
        TripPhase.Arrived("museum"),
        TripPhase.AwaitingNext("restaurant"),
        TripPhase.Completed,
    )

    @Test
    fun roundTrip_allPhases() {
        phases.forEach { assertEquals(it, TripPhaseCodec.decode(TripPhaseCodec.encode(it))) }
    }

    @Test
    fun decode_rejectsGarbage() {
        assertNull(TripPhaseCodec.decode(null))
        assertNull(TripPhaseCodec.decode(""))
        assertNull(TripPhaseCodec.decode("nav:"))
        assertNull(TripPhaseCodec.decode("warp:museum"))
    }

    @Test
    fun idsWithColon_surviveRoundTrip() {
        val phase = TripPhase.Arrived("poi:1")
        assertEquals(phase, TripPhaseCodec.decode(TripPhaseCodec.encode(phase)))
    }

    @Test
    fun inProgress_onlyForActiveLegs() {
        assertFalse(TripPhase.Idle.isInProgress)
        assertFalse(TripPhase.Completed.isInProgress)
        assertTrue(TripPhase.Arrived("x").isInProgress)
    }

    @Test
    fun consistency_requiresKnownStop() {
        val plan = PresetTrips.weekendMuseumTrip
        val known = plan.stops.first().id
        assertTrue(TripPhaseCodec.isConsistent(TripPhase.Arrived(known), plan))
        assertFalse(TripPhaseCodec.isConsistent(TripPhase.Arrived("ghost"), plan))
    }
}
