package ru.lsn03.voicemediacontroller.core.time

import android.os.SystemClock

class AndroidClock : Clock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
