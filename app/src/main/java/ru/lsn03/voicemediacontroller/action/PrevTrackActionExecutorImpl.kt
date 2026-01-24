package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.media.MediaControlGateway

class PrevTrackActionExecutorImpl (private val mediaControlGateway: MediaControlGateway): ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.PREV;
    }

    override fun execute() {
        mediaControlGateway.prev()
    }
}