package ru.lsn03.voicemediacontroller.service

sealed interface RecEvent {

    data class WakePartial(val text: String) : RecEvent
    data class WakeFinal(val text: String) : RecEvent
    data class CommandPartial(val text: String) : RecEvent
    data class CommandFinal(val text: String) : RecEvent

}
