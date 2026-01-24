package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.media.MediaControlGateway

class StartActionExecutorImpl(private val mediaControlGateway: MediaControlGateway) : ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.START;
    }

    override fun execute() {
        mediaControlGateway.play();
    }
}