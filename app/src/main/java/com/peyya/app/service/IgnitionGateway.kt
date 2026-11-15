package com.peyya.app.service

import com.peyya.app.domain.GearState

interface IgnitionGateway {
    val isMock: Boolean

    fun initialize(): Result<Unit>

    /** UI / demo signal that ignition (시동) was pressed. */
    fun signalIgnition(): Result<Unit>

    /**
     * Streams gear changes (P/D/R/N). Real vehicles emit the current gear first.
     * Mock implementations never call [listener].
     */
    fun observeGear(listener: (GearState) -> Unit): Result<Unit> = Result.success(Unit)

    fun release()
}
