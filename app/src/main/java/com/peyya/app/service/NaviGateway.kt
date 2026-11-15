package com.peyya.app.service

import com.peyya.app.domain.Stop

/** Navigation-side events the trip loop reacts to. */
sealed interface NaviEvent {
    data object Arrived : NaviEvent

    data class Failed(val reason: String) : NaviEvent
}

/** Which navigation backend is actually serving route requests. */
enum class NaviLink {
    Connecting,
    Pleos,
    Mock,
}

interface NaviGateway {
    val isMock: Boolean

    fun initialize(): Result<Unit>

    fun requestRoute(stop: Stop): Result<Unit>

    /** Registers the single consumer of [NaviEvent]s. Mock implementations stay silent. */
    fun setEventListener(listener: (NaviEvent) -> Unit) = Unit

    fun release()
}
