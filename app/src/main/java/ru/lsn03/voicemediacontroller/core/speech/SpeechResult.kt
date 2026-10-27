package ru.lsn03.voicemediacontroller.core.speech

sealed interface SpeechResult {
    data class Final(val text: String) : SpeechResult
    data class Partial(val text: String) : SpeechResult
    data object None : SpeechResult
}
