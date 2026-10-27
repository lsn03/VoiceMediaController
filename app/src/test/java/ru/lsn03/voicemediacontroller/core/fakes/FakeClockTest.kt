package ru.lsn03.voicemediacontroller.core.fakes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Self-test for the FakeClock fixture.
 *
 * Pins the policy for [FakeClock.advanceBy] with a negative delta:
 * it THROWS `IllegalArgumentException`. The alternative — silently
 * clamp to zero — would hide bugs in schedulers under test, so the
 * fake is loud about it.
 */
class FakeClockTest {

    @Test
    fun elapsedRealtimeMs_returnsInitialValueBeforeAnyAdvance() {
        val clock = FakeClock(initialMs = 1_000L)

        assertThat(clock.elapsedRealtimeMs()).isEqualTo(1_000L)
    }

    @Test
    fun elapsedRealtimeMs_defaultsToZero() {
        val clock = FakeClock()

        assertThat(clock.elapsedRealtimeMs()).isEqualTo(0L)
    }

    @Test
    fun advanceBy_addsToCurrentValue() {
        val clock = FakeClock(initialMs = 100L)

        clock.advanceBy(50L)
        assertThat(clock.elapsedRealtimeMs()).isEqualTo(150L)

        clock.advanceBy(0L)
        assertThat(clock.elapsedRealtimeMs()).isEqualTo(150L)

        clock.advanceBy(1_000L)
        assertThat(clock.elapsedRealtimeMs()).isEqualTo(1_150L)
    }

    @Test
    fun set_overridesCurrentValue_evenBackwards() {
        val clock = FakeClock(initialMs = 500L)

        clock.set(10L)
        assertThat(clock.elapsedRealtimeMs()).isEqualTo(10L)

        clock.set(10_000L)
        assertThat(clock.elapsedRealtimeMs()).isEqualTo(10_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun advanceBy_negativeDelta_throwsIllegalArgumentException() {
        val clock = FakeClock()

        clock.advanceBy(-1L)
    }
}
