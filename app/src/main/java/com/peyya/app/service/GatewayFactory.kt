package com.peyya.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GatewayBundle(
    val navi: NaviGateway,
    val ignition: IgnitionGateway,
    /** Live navigation backend; the UI shows the mock banner only for [NaviLink.Mock]. */
    val link: StateFlow<NaviLink>,
)

object GatewayFactory {
    fun create(context: Context, forceMock: Boolean = false): GatewayBundle {
        if (forceMock) {
            Log.i(TAG, "Force mock mode")
            return mockBundle()
        }
        val navi = ResilientNaviGateway(
            pleos = PleosNaviGateway(context),
            mock = MockNaviGateway(),
        )
        navi.initialize()
        return GatewayBundle(navi = navi, ignition = createIgnition(context), link = navi.link)
    }

    private fun createIgnition(context: Context): IgnitionGateway {
        val vehicle = runCatching { VehicleIgnitionGateway(context) }
            .onFailure { Log.w(TAG, "Vehicle SDK unavailable → MockIgnition", it) }
            .getOrNull()
        if (vehicle != null && vehicle.initialize().isSuccess) return vehicle
        Log.w(TAG, "Vehicle init failed → MockIgnition")
        return MockIgnitionGateway().also { it.initialize() }
    }

    private fun mockBundle(): GatewayBundle {
        val navi = MockNaviGateway().also { it.initialize() }
        val ignition = MockIgnitionGateway().also { it.initialize() }
        return GatewayBundle(
            navi = navi,
            ignition = ignition,
            link = MutableStateFlow(NaviLink.Mock).asStateFlow(),
        )
    }

    private const val TAG = "PeyyaGatewayFactory"
}
