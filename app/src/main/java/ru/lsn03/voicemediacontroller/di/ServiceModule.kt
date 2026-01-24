package ru.lsn03.voicemediacontroller.di

import android.content.Context
import android.os.Handler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGateway
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinator
import ru.lsn03.voicemediacontroller.voice.VoiceEffects
import ru.lsn03.voicemediacontroller.voice.VoiceEffectsImpl
import ru.lsn03.voicemediacontroller.vosk.VoskEngine

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

    @Provides
    fun provideVoiceEffects(
        @ApplicationContext ctx: Context,
        actionExecutorProvider: ActionExecutorProvider,
        audioDucker: AudioDucker,
        voskEngine: VoskEngine,
        soundPoolProvider: SoundPoolProvider
    ): VoiceEffects = VoiceEffectsImpl(
        ctx,
        actionExecutorProvider = actionExecutorProvider,
        audioDucker = audioDucker,
        vosk = voskEngine,
        soundPoolProvider = soundPoolProvider
    )

    @Provides
    fun provideVoiceCoordinator(
        voiceEffects: VoiceEffects,
        handler: Handler,
        voskEngine: VoskEngine,
    ): VoiceCoordinator = VoiceCoordinator(
        voskEngine,
        voiceEffects,
        handler
    )


}
