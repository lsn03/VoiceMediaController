package ru.lsn03.voicemediacontroller.audio.ducker

interface AudioDucker {
    fun start()
    fun stop()
    fun release() = stop()
}