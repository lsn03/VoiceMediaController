package ru.lsn03.voicemediacontroller.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGateway

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    fun provideActionExecutorProvider(
        audioManagerControllerProvider: AudioManagerControllerProvider,
        mediaControlGateway: MediaControlGateway,
        nowPlayingGateway: NowPlayingGateway,
        speechGateway: SpeechGateway,
    ): ActionExecutorProvider = ActionExecutorProvider(
        audioManagerControllerProvider,
        mediaControlGateway,
        speechGateway,
        nowPlayingGateway,
    )
}
