package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.action.VoiceAction

interface ActionExecutor {

    fun getAction(): VoiceAction;

    fun execute()

}