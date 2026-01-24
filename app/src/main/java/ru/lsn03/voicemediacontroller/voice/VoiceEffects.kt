package ru.lsn03.voicemediacontroller.voice

interface VoiceEffects {

    fun publishText(text: String)
    fun onFinalCommand(text: String)

    fun enterWake()
    fun enterCommand()

    fun scheduleCommandTimeout()
    fun cancelCommandTimeout()

}