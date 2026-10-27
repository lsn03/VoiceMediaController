package ru.lsn03.voicemediacontroller.core.time

import android.os.Handler

class HandlerScheduler(private val handler: Handler) : Scheduler {

    // Token -> Runnable map; mutated from any caller thread, so access is synchronized.
    private val pending = mutableMapOf<Any, Runnable>()
    private val lock = Any()

    override fun postDelayed(delayMs: Long, token: Any, action: () -> Unit) {
        val runnable = Runnable {
            synchronized(lock) { pending.remove(token) }
            action()
        }
        synchronized(lock) {
            pending.remove(token)?.let(handler::removeCallbacks)
            pending[token] = runnable
        }
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(token: Any) {
        val runnable = synchronized(lock) { pending.remove(token) } ?: return
        handler.removeCallbacks(runnable)
    }

    override fun cancelAll() {
        val snapshot = synchronized(lock) {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        snapshot.forEach(handler::removeCallbacks)
    }
}
