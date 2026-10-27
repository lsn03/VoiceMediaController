package ru.lsn03.voicemediacontroller.core.time

import android.os.Handler
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Drives [HandlerScheduler] with the real main-looper Handler and the
 * Robolectric virtual clock. No `Thread.sleep` — `ShadowLooper.idle(...)`
 * moves time deterministically.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class HandlerSchedulerTest {

    private val handler = Handler(Looper.getMainLooper())
    private val scheduler = HandlerScheduler(handler)

    private fun idle(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
    }

    @Test
    fun postDelayed_firesAfterLooperIdlesPastDelay() {
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }

        idle(99L)
        assertThat(fired).isEqualTo(0)

        idle(1L)
        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun cancel_byToken_preventsScheduledAction() {
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }
        scheduler.cancel("t")

        idle(1_000L)
        assertThat(fired).isEqualTo(0)
    }

    @Test
    fun cancelAll_removesEveryPendingToken() {
        var fired = 0

        scheduler.postDelayed(delayMs = 50L, token = "a") { fired += 1 }
        scheduler.postDelayed(delayMs = 100L, token = "b") { fired += 1 }
        scheduler.postDelayed(delayMs = 150L, token = "c") { fired += 1 }

        scheduler.cancelAll()
        idle(1_000L)

        assertThat(fired).isEqualTo(0)
    }

    @Test
    fun postDelayed_withExistingToken_replacesPreviousPending() {
        val events = mutableListOf<String>()

        scheduler.postDelayed(delayMs = 100L, token = "t") { events += "first" }
        scheduler.postDelayed(delayMs = 200L, token = "t") { events += "second" }

        idle(100L)
        assertThat(events).isEmpty()

        idle(100L)
        assertThat(events).containsExactly("second")
    }

    @Test
    fun postDelayed_fromInsideCallback_withSameToken_isSupportedByEntryClearingBeforeAction() {
        // Pins the HandlerScheduler contract: the pending entry for a token
        // is removed BEFORE the user action runs, so the action can re-post
        // with the same token without being clobbered by its own leftover
        // entry.
        val fired = mutableListOf<Long>()
        var remaining = 2

        val action = object {
            fun run() {
                fired += System.nanoTime() // we only care about ordering/count
                remaining -= 1
                if (remaining > 0) {
                    scheduler.postDelayed(delayMs = 50L, token = "t", action = ::run)
                }
            }
        }

        scheduler.postDelayed(delayMs = 50L, token = "t", action = action::run)

        idle(50L)
        assertThat(fired).hasSize(1)

        idle(50L)
        assertThat(fired).hasSize(2)
    }

    @Test
    fun cancel_unknownToken_isNoOp_andOtherPendingStillFire() {
        var fired = 0

        scheduler.postDelayed(delayMs = 50L, token = "t") { fired += 1 }
        scheduler.cancel("unknown-token")

        idle(50L)
        assertThat(fired).isEqualTo(1)
    }
}
