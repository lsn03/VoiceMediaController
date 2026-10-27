package ru.lsn03.voicemediacontroller.core.audio

interface AudioSource {
    fun start(onPcm: (ByteArray) -> Unit, onError: (String) -> Unit)
    fun stop()
}
