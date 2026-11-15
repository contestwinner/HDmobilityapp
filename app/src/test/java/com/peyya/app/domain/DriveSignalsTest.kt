package com.peyya.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveSignalsTest {
    @Test
    fun firstReading_isBaselineOnly() {
        val d = DriveCueDetector()
        assertNull(d.onGear(GearState.Drive))
    }

    @Test
    fun parkAfterDriving_emitsParked() {
        val d = DriveCueDetector()
        d.onGear(GearState.Drive)
        assertEquals(DriveCue.Parked, d.onGear(GearState.Park))
    }

    @Test
    fun parkAtStartup_thenDrive_emitsDeparted() {
        val d = DriveCueDetector()
        assertNull(d.onGear(GearState.Park))
        assertEquals(DriveCue.Departed, d.onGear(GearState.Drive))
    }

    @Test
    fun parkReverseDrive_stillCountsAsDeparture() {
        val d = DriveCueDetector()
        d.onGear(GearState.Drive)
        d.onGear(GearState.Park)
        assertNull(d.onGear(GearState.Reverse))
        assertEquals(DriveCue.Departed, d.onGear(GearState.Drive))
    }

    @Test
    fun repeatedOrUnknownGear_isIgnored() {
        val d = DriveCueDetector()
        d.onGear(GearState.Park)
        assertNull(d.onGear(GearState.Park))
        assertNull(d.onGear(GearState.Unknown))
    }

    @Test
    fun driveWithoutPark_doesNotDepartTwice() {
        val d = DriveCueDetector()
        d.onGear(GearState.Park)
        assertEquals(DriveCue.Departed, d.onGear(GearState.Drive))
        d.onGear(GearState.Neutral)
        assertNull(d.onGear(GearState.Drive))
    }

    @Test
    fun policy_arrivalOnlyWhileNavigating() {
        val nav = TripPhase.Navigating("a")
        assertEquals(TripCommand.MarkArrived, DriveCuePolicy.commandFor(nav, DriveCue.NaviArrived))
        assertEquals(TripCommand.MarkArrived, DriveCuePolicy.commandFor(nav, DriveCue.Parked))
        assertNull(DriveCuePolicy.commandFor(TripPhase.Idle, DriveCue.Parked))
        assertNull(DriveCuePolicy.commandFor(nav, DriveCue.Departed))
    }

    @Test
    fun policy_departure_startsNextWithoutTouch() {
        val arrived = TripPhase.Arrived("a")
        assertEquals(TripCommand.StartNext, DriveCuePolicy.commandFor(arrived, DriveCue.Departed))
        assertEquals(TripCommand.StartNext, DriveCuePolicy.commandFor(TripPhase.AwaitingNext("b"), DriveCue.Departed))
        assertNull(DriveCuePolicy.commandFor(TripPhase.Completed, DriveCue.Departed))
    }

    @Test
    fun gearMoving_flags() {
        assertEquals(true, GearState.Drive.moving)
        assertEquals(true, GearState.Reverse.moving)
        assertEquals(false, GearState.Park.moving)
    }

    @Test
    fun policy_midRoutePark_ignoredWhenNaviLive() {
        val nav = TripPhase.Navigating("a")
        assertEquals(TripCommand.MarkArrived, DriveCuePolicy.commandFor(nav, DriveCue.Parked, naviLive = false))
        assertNull(DriveCuePolicy.commandFor(nav, DriveCue.Parked, naviLive = true))
        assertEquals(TripCommand.MarkArrived, DriveCuePolicy.commandFor(nav, DriveCue.NaviArrived, naviLive = true))
    }
}
