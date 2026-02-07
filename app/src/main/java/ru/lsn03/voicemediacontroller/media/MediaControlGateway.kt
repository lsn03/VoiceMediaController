package ru.lsn03.voicemediacontroller.media

interface MediaControlGateway {
    fun play(): Boolean
    fun pause(): Boolean
    fun next(): Boolean
    fun prev(): Boolean
}