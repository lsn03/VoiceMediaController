package ru.lsn03.voicemediacontroller.core.time

interface Clock {
    fun elapsedRealtimeMs(): Long
}
