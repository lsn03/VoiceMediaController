package ru.lsn03.voicemediacontroller.voice

import kotlinx.coroutines.CoroutineScope

interface VoiceEffects {

    fun start(scope: CoroutineScope)

    fun publishText(text: String)
    fun onFinalCommand(text: String)

    fun enterWake()
    fun enterCommand()

}