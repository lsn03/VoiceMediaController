package ru.lsn03.voicemediacontroller.action

interface ActionExecutor {

    fun getAction(): VoiceAction;

    fun execute()

}