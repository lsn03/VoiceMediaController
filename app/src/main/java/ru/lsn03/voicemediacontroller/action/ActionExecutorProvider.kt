package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGateway

class ActionExecutorProvider(
    audioManagerControllerProvider: AudioManagerControllerProvider,
    mediaControlGateway: MediaControlGateway,
    speechGateway: SpeechGateway,
    nowPlayingGateway: NowPlayingGateway
) {

    private val providers: Map<VoiceAction, ActionExecutor> = mapOf(
        VoiceAction.NEXT to NextTrackActionExecutorImpl(mediaControlGateway),
        VoiceAction.PREV to PrevTrackActionExecutorImpl(mediaControlGateway),
        VoiceAction.SAY_TIME to SayTimeActionExecutorImpl(speechGateway),
        VoiceAction.SAY_TITLE to SayTitleActionExecutorImpl(speechGateway, nowPlayingGateway),
        VoiceAction.START to StartActionExecutorImpl(mediaControlGateway),
        VoiceAction.STOP to StopActionExecutorImpl(mediaControlGateway),
        VoiceAction.UNKNOWN to UnknownActionExecutorImpl(),
        VoiceAction.VOLUME_DOWN to VolumeDownActionExecutorImpl(audioManagerControllerProvider),
        VoiceAction.VOLUME_UP to VolumeUpActionExecutorImpl(audioManagerControllerProvider)
    )

    fun getExecutor(action: VoiceAction): ActionExecutor {
        return providers[action] ?: providers.getValue(VoiceAction.UNKNOWN);
    }

}