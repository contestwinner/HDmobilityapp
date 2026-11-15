package com.peyya.app.service

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.peyya.app.domain.GearState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Vehicle SDK wrapper. Pleos exposes no ignition ON/OFF API, so the gear
 * selection stream (P → D) is used as the re-ignition signal.
 * Requires `pleos.car.permission.CAR_DRIVE` in the manifest.
 */
class VehicleIgnitionGateway(
    context: Context,
) : IgnitionGateway {
    override val isMock: Boolean = false

    private val vehicle = Vehicle(context.applicationContext)
    private var ready = false
    private var gearListener: GearSelectionListener? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun initialize(): Result<Unit> {
        return runCatching {
            vehicle.initialize()
            ready = true
            Log.i(TAG, "Vehicle SDK initialized")
            Unit
        }.onFailure { error ->
            ready = false
            Log.e(TAG, "Vehicle SDK init failed", error)
        }
    }

    override fun signalIgnition(): Result<Unit> {
        if (!ready) {
            return Result.failure(IllegalStateException("Vehicle not initialized"))
        }
        Log.i(TAG, "signalIgnition (manual fallback button)")
        return Result.success(Unit)
    }

    override fun observeGear(listener: (GearState) -> Unit): Result<Unit> {
        if (!ready) return Result.failure(IllegalStateException("Vehicle not initialized"))
        return registerGear(listener, attempt = 1)
    }

    /**
     * Vehicle.initialize() binds asynchronously, so the first registration can race the binding.
     * Non-permission failures are retried a few times; a permission failure (non-Pleos device) is final.
     */
    @Suppress("DEPRECATION")
    private fun registerGear(listener: (GearState) -> Unit, attempt: Int): Result<Unit> = runCatching {
        val gear = vehicle.getGear()
        val liveEventSeen = AtomicBoolean(false)
        val sdkListener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                liveEventSeen.set(true)
                listener(gear.toGearState())
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "gear listener failed", e)
            }
        }
        gear.registerGearSelection(sdkListener)
        gearListener = sdkListener
        gear.getCurrentGearSelectionState(
            // A stale snapshot arriving after a live event would fake a gear change; drop it.
            { current -> if (!liveEventSeen.get()) listener(current.toGearState()) },
            { error -> Log.w(TAG, "current gear read failed", error) },
        )
        Log.i(TAG, "Gear selection listener registered (attempt $attempt)")
        Unit
    }.onFailure { error ->
        val retryable = error !is SecurityException && attempt < MAX_GEAR_ATTEMPTS && ready
        Log.e(TAG, "Gear listener registration failed (attempt $attempt, retry=$retryable)", error)
        if (retryable) {
            handler.postDelayed({ if (ready) registerGear(listener, attempt + 1) }, GEAR_RETRY_DELAY_MS)
        }
    }

    @Suppress("DEPRECATION")
    override fun release() {
        handler.removeCallbacksAndMessages(null)
        if (!ready) return
        gearListener?.let { registered ->
            runCatching { vehicle.getGear().unregisterGearSelection(registered) }
                .onFailure { Log.e(TAG, "Gear unregister failed", it) }
        }
        gearListener = null
        runCatching { vehicle.release() }
            .onFailure { Log.e(TAG, "Vehicle release failed", it) }
        ready = false
    }

    private fun VehicleGear?.toGearState(): GearState = when (this) {
        null -> GearState.Unknown
        VehicleGear.GEAR_PARK -> GearState.Park
        VehicleGear.GEAR_REVERSE -> GearState.Reverse
        VehicleGear.GEAR_NEUTRAL -> GearState.Neutral
        VehicleGear.GEAR_UNKNOWN -> GearState.Unknown
        else -> GearState.Drive
    }

    companion object {
        private const val TAG = "PeyyaVehicleIgnition"
        private const val MAX_GEAR_ATTEMPTS = 3
        private const val GEAR_RETRY_DELAY_MS = 1_500L
    }
}
