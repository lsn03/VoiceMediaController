package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGateway

class SayTitleActionExecutorImpl(
    private val speechGateway: SpeechGateway,
    private val nowPlayingGateway: NowPlayingGateway
) : ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.SAY_TITLE;
    }

    override fun execute() {
        val phrase = nowPlayingGateway.nowPlayingPhrase()
        speechGateway.speak(phrase, "cmd_title")
    }
}