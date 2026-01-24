package ru.lsn03.voicemediacontroller.command

import ru.lsn03.voicemediacontroller.action.VoiceAction

data class CommandBinding(
    val phrases: List<String>,
    val action: VoiceAction
)
