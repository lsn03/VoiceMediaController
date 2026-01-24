package ru.lsn03.voicemediacontroller.action

import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME


class UnknownActionExecutorImpl : ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.UNKNOWN;
    }

    override fun execute() {
       Log.d(APPLICATION_NAME, "Unknown executor")
    }
}