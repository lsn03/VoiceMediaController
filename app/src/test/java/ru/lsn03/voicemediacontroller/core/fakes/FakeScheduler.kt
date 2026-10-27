package ru.lsn03.voicemediacontroller.core.fakes

import ru.lsn03.voicemediacontroller.core.time.Scheduler

/**
 * Virtual-time [Scheduler] driven by a [FakeClock].
 *
 * Posted actions are stored as `(fireAtMs, token, action)` entries and only
 * executed when [advanceBy] moves the clock past their fire-at. Entries are
 * fired in ascending `fireAtMs` order; ties fire in insertion order.
 *
 * Each entry is removed from the pending list BEFORE its action runs, which
 * mirrors [ru.lsn03.voicemediacontroller.core.time.HandlerScheduler] and lets
 * a callback re-post with the same token safely.
 *
 * This fake does not use any real executor or looper — zero Android deps.
 */
class FakeScheduler(private val clock: FakeClock) : Scheduler {

    private data class Entry(
        val fireAtMs: Long,
        val token: Any,
        val action: () -> Unit,
        val seq: Long,
    )

    private val entries: MutableList<Entry> = mutableListOf()
    private var sequence: Long = 0L

    override fun postDelayed(delayMs: Long, token: Any, action: () -> Unit) {
        // Match HandlerScheduler semantics: posting with a known token
        // supersedes the previous entry for that token.
        entries.removeAll { it.token == token }
        entries.add(
            Entry(
                fireAtMs = clock.elapsedRealtimeMs() + delayMs,
                token = token,
                action = action,
                seq = sequence++,
            )
        )
    }

    override fun cancel(token: Any) {
        entries.removeAll { it.token == token }
    }

    override fun cancelAll() {
        entries.clear()
    }

    /**
     * Advance the backing [FakeClock] by [ms] milliseconds and fire every
     * pending entry whose `fireAtMs <= clock.now` in ascending order.
     *
     * Each entry is removed from the pending list before its action runs,
     * so a reentrant [postDelayed] from inside the callback remains valid.
     */
    fun advanceBy(ms: Long) {
        clock.advanceBy(ms)
        drainDue()
    }

    private fun drainDue() {
        while (true) {
            val now = clock.elapsedRealtimeMs()
            val next = entries
                .filter { it.fireAtMs <= now }
                .minWithOrNull(compareBy({ it.fireAtMs }, { it.seq }))
                ?: return
            entries.remove(next)
            next.action()
        }
    }

    /** Snapshot of currently pending entries as `(token, fireAtMs)` pairs. */
    fun pending(): List<Pair<Any, Long>> =
        entries.map { it.token to it.fireAtMs }
}
