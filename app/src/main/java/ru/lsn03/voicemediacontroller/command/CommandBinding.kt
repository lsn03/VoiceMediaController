package ru.lsn03.voicemediacontroller.command

import ru.lsn03.voicemediacontroller.service.VoiceAction

data class CommandBinding(
    val phrases: List<String>,
    val action: VoiceAction
)
