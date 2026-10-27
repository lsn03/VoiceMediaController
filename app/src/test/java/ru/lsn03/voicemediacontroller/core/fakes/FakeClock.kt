package ru.lsn03.voicemediacontroller.core.fakes

import ru.lsn03.voicemediacontroller.core.time.Clock

/**
 * Deterministic in-memory [Clock] for unit tests.
 *
 * Virtual time starts at [initialMs] and only moves forward in response to
 * explicit [advanceBy] / [set] calls — never with real wall-clock time, so
 * tests that depend on timing stay reproducible.
 *
 * Access is guarded by `synchronized` for predictability when a scheduler
 * drives the clock from several callbacks on the same thread. The fake is
 * not intended for cross-thread timing scenarios.
 *
 * Policy: [advanceBy] with a negative delta throws `IllegalArgumentException`
 * (clock cannot run backwards). Use [set] when a test genuinely needs to
 * rewind to an earlier instant.
 */
class FakeClock(initialMs: Long = 0L) : Clock {

    private val lock = Any()
    private var nowMs: Long = initialMs

    override fun elapsedRealtimeMs(): Long = synchronized(lock) { nowMs }

    /** Advance virtual time by [ms] milliseconds. Must be non-negative. */
    fun advanceBy(ms: Long) {
        require(ms >= 0) { "advanceBy requires non-negative delta, got $ms" }
        synchronized(lock) { nowMs += ms }
    }

    /** Hard-set the virtual clock to [ms]. Allowed to go backwards. */
    fun set(ms: Long) {
        synchronized(lock) { nowMs = ms }
    }
}
