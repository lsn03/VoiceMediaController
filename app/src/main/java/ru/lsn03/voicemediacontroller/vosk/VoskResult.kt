package ru.lsn03.voicemediacontroller.vosk

sealed interface VoskResult {

    data class Final(val text: String) : VoskResult
    data class Partial(val text: String) : VoskResult
    data object None : VoskResult

}