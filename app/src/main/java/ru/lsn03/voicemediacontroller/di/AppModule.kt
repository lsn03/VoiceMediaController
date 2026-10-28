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
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProviderImpl
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDuckerImpl
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.core.audio.AudioSource
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.core.speech.AssetModelProvider
import ru.lsn03.voicemediacontroller.core.speech.ModelProvider
import ru.lsn03.voicemediacontroller.core.speech.SpeechLocale
import ru.lsn03.voicemediacontroller.media.*
import ru.lsn03.voicemediacontroller.service.AndroidAudioSource
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.SAMPLE_RATE
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.tts.SpeechGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGatewayImpl
import ru.lsn03.voicemediacontroller.voice.GrammarBuilder
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinatorConfig
import ru.lsn03.voicemediacontroller.voice.VoiceStateMachine
import ru.lsn03.voicemediacontroller.voice.WakeParser
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
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
    fun provideAudioSource(logger: AppLogger): AudioSource =
        AndroidAudioSource(sampleRate = SAMPLE_RATE, logger = logger)

    @Provides
    @Singleton
    fun provideAudioManagerControllerProvider(
        @ApplicationContext ctx: Context
    ): AudioManagerControllerProvider = AudioManagerControllerProviderImpl(ctx)

    @Provides
    @Singleton
    fun provideModelProvider(
        @ApplicationContext ctx: Context,
        logger: AppLogger,
    ): ModelProvider = AssetModelProvider(ctx, logger)

    @Provides
    @Singleton
    fun provideVoskEngine(
        modelProvider: ModelProvider,
        logger: AppLogger,
    ): VoskEngine = VoskEngine(modelProvider, SpeechLocale.RU, logger)


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

    @Provides
    @Singleton
    fun provideTextNormalizer(): TextNormalizer = TextNormalizer()

    @Provides
    @Singleton
    fun provideVoiceCoordinatorConfig(): VoiceCoordinatorConfig = VoiceCoordinatorConfig.default()

    @Provides
    @Singleton
    fun provideVoiceStateMachine(): VoiceStateMachine = VoiceStateMachine()

    @Provides
    @Singleton
    fun provideGrammarBuilder(
        normalizer: TextNormalizer,
        logger: AppLogger,
    ): GrammarBuilder = GrammarBuilder(normalizer, logger)

    @Provides
    @Singleton
    fun provideWakeParser(wakeRepo: WakeWordRepository): WakeParser =
        WakeParser { wakeRepo.current() }

}

