package com.peyya.app.domain

/** True while a route is in progress and must survive an app restart. */
val TripPhase.isInProgress: Boolean
    get() = this is TripPhase.Navigating || this is TripPhase.Arrived || this is TripPhase.AwaitingNext

/** Compact string form of [TripPhase] for persistence (no Android dependency). */
object TripPhaseCodec {
    private const val SEP = ":"

    fun encode(phase: TripPhase): String = when (phase) {
        TripPhase.Idle -> "idle"
        is TripPhase.Navigating -> "nav$SEP${phase.stopId}"
        is TripPhase.Arrived -> "arr$SEP${phase.stopId}"
        is TripPhase.AwaitingNext -> "next$SEP${phase.nextStopId}"
        TripPhase.Completed -> "done"
    }

    fun decode(raw: String?): TripPhase? {
        if (raw.isNullOrBlank()) return null
        val tag = raw.substringBefore(SEP)
        val id = raw.substringAfter(SEP, missingDelimiterValue = "")
        return when (tag) {
            "idle" -> TripPhase.Idle
            "done" -> TripPhase.Completed
            "nav" -> id.takeIf { it.isNotBlank() }?.let { TripPhase.Navigating(it) }
            "arr" -> id.takeIf { it.isNotBlank() }?.let { TripPhase.Arrived(it) }
            "next" -> id.takeIf { it.isNotBlank() }?.let { TripPhase.AwaitingNext(it) }
            else -> null
        }
    }

    /** A restored phase is only valid if every stop it references still exists in the plan. */
    fun isConsistent(phase: TripPhase, plan: TripPlan): Boolean {
        val ids = plan.stops.map { it.id }.toSet()
        return when (phase) {
            is TripPhase.Navigating -> phase.stopId in ids
            is TripPhase.Arrived -> phase.stopId in ids
            is TripPhase.AwaitingNext -> phase.nextStopId in ids
            TripPhase.Idle, TripPhase.Completed -> true
        }
    }
}
