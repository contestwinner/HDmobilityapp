package com.peyya.app.domain

/** Vehicle gear reduced to what the trip loop cares about. */
enum class GearState {
    Park,
    Drive,
    Reverse,
    Neutral,
    Unknown,
    ;

    val moving: Boolean get() = this == Drive || this == Reverse
}

/** Real-vehicle moments that replace the demo 「시동」 button. */
sealed interface DriveCue {
    /** Shifted into P after having driven → the family got out here. */
    data object Parked : DriveCue

    /** Left P and engaged D → the family is back in the car (re-ignition proxy). */
    data object Departed : DriveCue

    /** System navigation reported arrival at the requested destination. */
    data object NaviArrived : DriveCue
}

/** What the trip state machine should do in response to a cue. */
enum class TripCommand {
    MarkArrived,
    /** Re-ignition while a next stop exists: route straight there (no touch while in D). */
    StartNext,
}

/**
 * Turns a raw gear stream into [DriveCue]s.
 *
 * Pleos has no ignition ON/OFF API, so "P → (R/N) → D" stands in for re-ignition.
 * The first reading only establishes a baseline and never emits a cue.
 */
class DriveCueDetector {
    private var last: GearState = GearState.Unknown
    private var armedFromPark = false
    private var hasDriven = false

    fun onGear(gear: GearState): DriveCue? {
        if (gear == GearState.Unknown || gear == last) return null
        val previous = last
        last = gear
        return when (gear) {
            GearState.Park -> parkCue(previous)
            GearState.Drive -> driveCue()
            GearState.Reverse, GearState.Neutral, GearState.Unknown -> null
        }
    }

    private fun parkCue(previous: GearState): DriveCue? {
        val cue = if (hasDriven && previous != GearState.Unknown) DriveCue.Parked else null
        armedFromPark = true
        return cue
    }

    private fun driveCue(): DriveCue? {
        val cue = if (armedFromPark) DriveCue.Departed else null
        armedFromPark = false
        hasDriven = true
        return cue
    }
}

/** Pure mapping from (phase, cue) to the next trip command, if any. */
object DriveCuePolicy {
    /**
     * @param naviLive true when Pleos NaviHelper is bound and reports arrivals itself. Then a P
     * shift mid-route (fuel stop, drive-through) must NOT count as arriving at the destination.
     */
    fun commandFor(phase: TripPhase, cue: DriveCue, naviLive: Boolean = false): TripCommand? = when (cue) {
        DriveCue.NaviArrived ->
            if (phase is TripPhase.Navigating) TripCommand.MarkArrived else null
        DriveCue.Parked ->
            if (phase is TripPhase.Navigating && !naviLive) TripCommand.MarkArrived else null
        DriveCue.Departed ->
            if (phase is TripPhase.Arrived || phase is TripPhase.AwaitingNext) TripCommand.StartNext else null
    }
}
