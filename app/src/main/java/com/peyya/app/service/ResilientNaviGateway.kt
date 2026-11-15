package com.peyya.app.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.peyya.app.domain.Stop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes to Pleos NaviHelper when it actually binds, otherwise to [MockNaviGateway].
 *
 * The link starts as [NaviLink.Connecting]; it becomes [NaviLink.Mock] on bind
 * failure or after [timeoutMs], and is upgraded to [NaviLink.Pleos] whenever the
 * bind succeeds (even late), so a slow emulator never gets stuck in mock mode.
 */
class ResilientNaviGateway(
    private val pleos: PleosNaviGateway,
    private val mock: MockNaviGateway,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : NaviGateway {
    private val handler = Handler(Looper.getMainLooper())
    private val _link = MutableStateFlow(NaviLink.Connecting)
    val link: StateFlow<NaviLink> = _link.asStateFlow()

    override val isMock: Boolean get() = _link.value != NaviLink.Pleos

    /** Latest route asked for while still [NaviLink.Connecting]; flushed once the link resolves. */
    @Volatile private var pendingStop: Stop? = null

    private val fallbackOnTimeout = Runnable {
        if (_link.value == NaviLink.Connecting) {
            Log.w(TAG, "NaviHelper bind timed out after ${timeoutMs}ms → Mock")
            _link.value = NaviLink.Mock
            flushPending()
        }
    }

    override fun initialize(): Result<Unit> {
        mock.initialize()
        handler.postDelayed(fallbackOnTimeout, timeoutMs)
        pleos.connect(
            onReady = {
                handler.removeCallbacks(fallbackOnTimeout)
                _link.value = NaviLink.Pleos
                flushPending()
            },
            onFailed = {
                handler.removeCallbacks(fallbackOnTimeout)
                if (_link.value == NaviLink.Connecting) _link.value = NaviLink.Mock
                flushPending()
            },
        )
        return Result.success(Unit)
    }

    override fun requestRoute(stop: Stop): Result<Unit> {
        return when (_link.value) {
            NaviLink.Pleos -> pleos.requestRoute(stop)
            NaviLink.Mock -> mock.requestRoute(stop)
            NaviLink.Connecting -> {
                Log.i(TAG, "Link still connecting; queued route to ${stop.name}")
                pendingStop = stop
                Result.success(Unit)
            }
        }
    }

    private fun flushPending() {
        val stop = pendingStop ?: return
        pendingStop = null
        requestRoute(stop)
    }

    override fun setEventListener(listener: (NaviEvent) -> Unit) {
        pleos.setEventListener(listener)
    }

    override fun release() {
        handler.removeCallbacks(fallbackOnTimeout)
        pendingStop = null
        pleos.release()
        mock.release()
    }

    companion object {
        private const val TAG = "PeyyaResilientNavi"
        const val DEFAULT_TIMEOUT_MS = 4_000L
    }
}
