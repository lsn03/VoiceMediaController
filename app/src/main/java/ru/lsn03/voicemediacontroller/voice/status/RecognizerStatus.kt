package ru.lsn03.voicemediacontroller.voice.status

sealed interface RecognizerStatus {
    data object Idle : RecognizerStatus
    data class WakeHint(val partial: String) : RecognizerStatus
    data class WakeAccepted(val wakeTitleCased: String) : RecognizerStatus
    data class CommandHint(val partial: String) : RecognizerStatus
    data class CommandExecuting(val command: String) : RecognizerStatus
    data class Error(val message: String) : RecognizerStatus
}
