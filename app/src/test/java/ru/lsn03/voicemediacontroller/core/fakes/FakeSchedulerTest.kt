package ru.lsn03.voicemediacontroller.core.fakes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Self-test for the FakeScheduler fixture. The point is to prove the
 * scheduler-under-test will behave like production HandlerScheduler
 * when a real-time test replaces the clock.
 */
class FakeSchedulerTest {

    @Test
    fun postDelayed_doesNotFireBeforeAdvance() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }

        assertThat(fired).isEqualTo(0)
        assertThat(scheduler.pending()).hasSize(1)
    }

    @Test
    fun advanceBy_shortOfDelay_doesNotFire() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }
        scheduler.advanceBy(99L)

        assertThat(fired).isEqualTo(0)
        assertThat(clock.elapsedRealtimeMs()).isEqualTo(99L)
    }

    @Test
    fun advanceBy_hittingDelayExactly_fires() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }
        scheduler.advanceBy(99L)
        scheduler.advanceBy(1L)

        assertThat(fired).isEqualTo(1)
        assertThat(scheduler.pending()).isEmpty()
    }

    @Test
    fun cancel_byToken_removesPendingEntry() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }
        scheduler.cancel("t")
        scheduler.advanceBy(1_000L)

        assertThat(fired).isEqualTo(0)
        assertThat(scheduler.pending()).isEmpty()
    }

    @Test
    fun cancel_unknownToken_isNoOp() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += 1 }
        scheduler.cancel("other")
        scheduler.advanceBy(100L)

        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun cancelAll_removesEveryPendingEntry() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        var fired = 0

        scheduler.postDelayed(delayMs = 100L, token = "a") { fired += 1 }
        scheduler.postDelayed(delayMs = 200L, token = "b") { fired += 1 }
        scheduler.postDelayed(delayMs = 300L, token = "c") { fired += 1 }
        scheduler.cancelAll()
        scheduler.advanceBy(10_000L)

        assertThat(fired).isEqualTo(0)
        assertThat(scheduler.pending()).isEmpty()
    }

    @Test
    fun multiplePendingWithDifferentTokens_fireInAscendingFireAtOrder() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        val order = mutableListOf<String>()

        // Posted out of order to prove the scheduler sorts by fireAt.
        scheduler.postDelayed(delayMs = 300L, token = "late") { order += "late" }
        scheduler.postDelayed(delayMs = 100L, token = "early") { order += "early" }
        scheduler.postDelayed(delayMs = 200L, token = "mid") { order += "mid" }

        scheduler.advanceBy(500L)

        assertThat(order).containsExactly("early", "mid", "late").inOrder()
    }

    @Test
    fun postDelayed_withExistingToken_replacesPreviousEntry() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        val fired = mutableListOf<String>()

        scheduler.postDelayed(delayMs = 100L, token = "t") { fired += "first" }
        scheduler.postDelayed(delayMs = 200L, token = "t") { fired += "second" }

        scheduler.advanceBy(100L)
        assertThat(fired).isEmpty()
        scheduler.advanceBy(100L)
        assertThat(fired).containsExactly("second")
    }

    @Test
    fun pending_reflectsStateAfterPostAndCancel() {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)

        scheduler.postDelayed(delayMs = 100L, token = "a") {}
        scheduler.postDelayed(delayMs = 200L, token = "b") {}

        val snapshot = scheduler.pending()
        assertThat(snapshot).hasSize(2)
        assertThat(snapshot.map { it.first }).containsExactly("a", "b")
        assertThat(snapshot.map { it.second }).containsExactly(100L, 200L)

        scheduler.cancel("a")
        assertThat(scheduler.pending().map { it.first }).containsExactly("b")
    }

    @Test
    fun advanceBy_insideCallback_reentrantPostDelayedSameToken_works() {
        // Mirrors the HandlerScheduler contract: a running callback may
        // re-post with the same token and the fake must not re-enter the
        // just-cleared entry.
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        val fired = mutableListOf<Long>()
        var remaining = 2

        val action = object {
            fun run() {
                fired += clock.elapsedRealtimeMs()
                remaining -= 1
                if (remaining > 0) {
                    scheduler.postDelayed(delayMs = 50L, token = "t", action = ::run)
                }
            }
        }

        scheduler.postDelayed(delayMs = 50L, token = "t", action = action::run)
        scheduler.advanceBy(50L)
        scheduler.advanceBy(50L)

        assertThat(fired).containsExactly(50L, 100L).inOrder()
        assertThat(scheduler.pending()).isEmpty()
    }
}
