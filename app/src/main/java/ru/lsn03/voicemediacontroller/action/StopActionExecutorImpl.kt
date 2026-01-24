package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.media.MediaControlGateway

class StopActionExecutorImpl (private val mediaControlGateway: MediaControlGateway): ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.STOP;
    }

    override fun execute() {
        mediaControlGateway.pause()
    }
}