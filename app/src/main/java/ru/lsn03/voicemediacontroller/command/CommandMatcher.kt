package ru.lsn03.voicemediacontroller.command

import ru.lsn03.voicemediacontroller.service.VoiceAction

class CommandMatcher(private val bindings: List<CommandBinding>) {
    fun match(text: String): VoiceAction? =
        bindings.firstOrNull { text in it.phrases }?.action
}


