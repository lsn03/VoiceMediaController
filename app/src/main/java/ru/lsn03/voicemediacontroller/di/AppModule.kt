package ru.lsn03.voicemediacontroller.di

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProviderImpl
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDuckerImpl
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.MediaControlGatewayImpl
import ru.lsn03.voicemediacontroller.media.MediaControllerProvider
import ru.lsn03.voicemediacontroller.media.MediaControllerProviderImpl
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGatewayImpl
import ru.lsn03.voicemediacontroller.service.AudioRecorder
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.SAMPLE_RATE
import ru.lsn03.voicemediacontroller.tts.SpeechGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGatewayImpl
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMediaControllerProvider(
        @ApplicationContext ctx: Context
    ): MediaControllerProvider = MediaControllerProviderImpl(ctx)

    @Provides
    @Singleton
    fun provideNowPlayingGateway(
        provider: MediaControllerProvider
    ): NowPlayingGateway = NowPlayingGatewayImpl(provider)

    @Provides
    @Singleton
    fun provideMediaControlGateway(
        provider: MediaControllerProvider
    ): MediaControlGateway = MediaControlGatewayImpl(provider)

    @Provides
    @Singleton
    fun provideAudioRecorder(): AudioRecorder = AudioRecorder(sampleRate = SAMPLE_RATE)

    @Provides
    @Singleton
    fun provideAudioManagerControllerProvider(
        @ApplicationContext ctx: Context
    ): AudioManagerControllerProvider = AudioManagerControllerProviderImpl(ctx)

    @Provides
    @Singleton
    fun provideVoskEngine(
        @ApplicationContext ctx: Context
    ): VoskEngine = VoskEngine(ctx)


    @Provides
    @Singleton
    fun provideAudioManager(@ApplicationContext ctx: Context): AudioManager =
        ctx.getSystemService(AudioManager::class.java)

    @Provides
    @Singleton
    fun provideMainHandler(): Handler = Handler(Looper.getMainLooper())

    @Provides
    @Singleton
    fun provideAudioDucker(
        audioManager: AudioManager,
        handler: Handler,
    ): AudioDucker = AudioDuckerImpl(audioManager, handler)

    @Provides
    @Singleton
    fun provideSpeechGateway(impl: SpeechGatewayImpl): SpeechGateway = impl


    @Provides
    @Singleton
    fun provideSoundPoolProvider(
        @ApplicationContext ctx: Context,
        soundPrefs: SoundPrefs
    ): SoundPoolProvider = SoundPoolProvider(ctx, soundPrefs)

}

