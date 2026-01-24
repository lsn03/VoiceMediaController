package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.media.MediaControlGateway

class NextTrackActionExecutorImpl (private val mediaControlGateway: MediaControlGateway): ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.NEXT;
    }

    override fun execute() {
        mediaControlGateway.next()
    }
}