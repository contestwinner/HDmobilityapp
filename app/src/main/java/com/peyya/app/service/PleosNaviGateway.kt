package com.peyya.app.service

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.peyya.app.domain.Stop

class PleosNaviGateway(
    context: Context,
) : NaviGateway {
    override val isMock: Boolean = false

    private val appContext = context.applicationContext
    @Volatile private var helper: NaviHelper? = null
    @Volatile private var ready = false
    @Volatile private var released = false

    /** Read on the SDK binder thread, written on main. */
    @Volatile private var eventListener: (NaviEvent) -> Unit = {}

    private val sdkListener = object : NaviHelperEventListener {
        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            Log.i(TAG, "onDestinationArrived")
            eventListener(NaviEvent.Arrived)
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            Log.i(TAG, "onWayPointArrived")
            eventListener(NaviEvent.Arrived)
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "NaviHelper error $code")
            eventListener(NaviEvent.Failed(code.toString()))
        }
    }

    /** Fire-and-forget init; readiness is only known via [connect]'s callbacks. */
    override fun initialize(): Result<Unit> {
        connect(onReady = {}, onFailed = {})
        return Result.success(Unit)
    }

    /** Async init: reports the real binding result instead of assuming success. */
    fun connect(onReady: () -> Unit, onFailed: (Throwable) -> Unit) {
        val created = runCatching { NaviHelper(appContext) }.getOrElse {
            Log.e(TAG, "NaviHelper construction failed", it)
            onFailed(it)
            return
        }
        runCatching {
            created.initialize(
                {
                    if (released) {
                        // Bound after the screen was torn down: drop it instead of leaking the binding.
                        runCatching { created.release() }.onFailure { Log.w(TAG, "late release failed", it) }
                    } else {
                        onConnected(created)
                        onReady()
                    }
                },
                { error -> Log.e(TAG, "NaviHelper bind failed", error); onFailed(error) },
            )
        }.onFailure { error ->
            Log.e(TAG, "NaviHelper initialize threw", error)
            onFailed(error)
        }
    }

    override fun setEventListener(listener: (NaviEvent) -> Unit) {
        eventListener = listener
    }

    override fun requestRoute(stop: Stop): Result<Unit> {
        val navi = helper
        if (!ready || navi == null) {
            return Result.failure(IllegalStateException("NaviHelper not initialized"))
        }
        return runCatching {
            navi.requestRoute(stop.toRouteInfo())
            Log.i(TAG, "requestRoute ${stop.name}")
            Unit
        }.onFailure { error ->
            Log.e(TAG, "requestRoute failed for ${stop.name}", error)
        }
    }

    override fun release() {
        released = true
        eventListener = {}
        runCatching {
            helper?.removeListener(sdkListener)
            helper?.release()
        }.onFailure { Log.e(TAG, "release failed", it) }
        helper = null
        ready = false
    }

    private fun onConnected(created: NaviHelper) {
        runCatching { created.addListener(sdkListener) }
            .onFailure { Log.w(TAG, "addListener failed; arrival events unavailable", it) }
        helper = created
        ready = true
        Log.i(TAG, "Pleos NaviHelper connected")
    }

    private fun Stop.toRouteInfo(): RouteInfo = RouteInfo(
        latitude = latitude,
        longitude = longitude,
        poiId = poiId,
        poiName = name,
        poiSubId = poiSubId,
        address = address,
    )

    companion object {
        private const val TAG = "PeyyaPleosNavi"
    }
}
