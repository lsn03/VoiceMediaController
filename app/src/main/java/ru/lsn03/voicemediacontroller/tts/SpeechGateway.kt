package ru.lsn03.voicemediacontroller.tts

interface SpeechGateway {
    fun speak(text: String, utteranceId: String)
    fun shutdown()
}