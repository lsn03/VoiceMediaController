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

//    @Provides
//    fun provideSpeechGateway(
//        handler: Handler,
//        service: Service // или Context, см. ниже
//    ): SpeechGateway {
//        // Тут проблема: нужен доступ к ttsReady/ttsProvider из VoiceService
//        // Поэтому этот вариант заработает только после выноса TTS в TtsManager (следующий шаг).
//        TODO()
//    }
//
//    @Provides
//    fun provideActionExecutorProvider(
//        audioManagerControllerProvider: AudioManagerControllerProvider,
//        mediaControlGateway: MediaControlGateway,
//        nowPlayingGateway: NowPlayingGateway,
//        speechGateway: SpeechGateway,
//    ): ActionExecutorProvider = ActionExecutorProvider(
//        audioManagerControllerProvider,
//        mediaControlGateway,
//        speechGateway,
//        nowPlayingGateway,
//    )
}
