package ru.lsn03.voicemediacontroller.core.time

interface Scheduler {
    fun postDelayed(delayMs: Long, token: Any, action: () -> Unit)
    fun cancel(token: Any)
    fun cancelAll()
}
