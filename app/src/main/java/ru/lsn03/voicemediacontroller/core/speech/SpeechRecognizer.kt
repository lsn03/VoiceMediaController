package ru.lsn03.voicemediacontroller.core.speech

interface SpeechRecognizer {
    fun updateGrammars(wakeJson: String, commandJson: String, wakeCommandJson: String)
    fun acceptWake(pcm: ByteArray): SpeechResult
    fun acceptCommand(pcm: ByteArray): SpeechResult
    fun resetWake()
    fun resetCommand()
}
