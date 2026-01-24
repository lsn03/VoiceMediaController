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

    val providers: List<ActionExecutor> = listOf(
        NextTrackActionExecutorImpl(mediaControlGateway),
        PrevTrackActionExecutorImpl(mediaControlGateway),
        SayTimeActionExecutorImpl(speechGateway),
        SayTitleActionExecutorImpl(speechGateway, nowPlayingGateway ),
        StartActionExecutorImpl(mediaControlGateway),
        StopActionExecutorImpl(mediaControlGateway),
        UnknownActionExecutorImpl(),
        VolumeDownActionExecutorImpl(audioManagerControllerProvider),
        VolumeUpActionExecutorImpl(audioManagerControllerProvider)
    )

}