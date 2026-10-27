package ru.lsn03.voicemediacontroller.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.core.speech.SpeechRecognizer
import ru.lsn03.voicemediacontroller.core.time.Clock
import ru.lsn03.voicemediacontroller.core.time.Scheduler
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.tts.SpeechGateway
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinator
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinatorConfig
import ru.lsn03.voicemediacontroller.voice.VoiceEffects
import ru.lsn03.voicemediacontroller.voice.VoiceEffectsImpl
import ru.lsn03.voicemediacontroller.voice.VoiceStateMachine
import ru.lsn03.voicemediacontroller.voice.WakeParser
import ru.lsn03.voicemediacontroller.voice.repo.VoicePhraseRepository
import ru.lsn03.voicemediacontroller.vosk.VoskEngine

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    @ServiceScoped
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
    @ServiceScoped
    fun provideVoiceEffects(
        @ApplicationContext ctx: Context,
        actionExecutorProvider: ActionExecutorProvider,
        audioDucker: AudioDucker,
        voskEngine: VoskEngine,
        soundPoolProvider: SoundPoolProvider,
        phraseRepo: VoicePhraseRepository,
        textNormalizer: TextNormalizer,
    ): VoiceEffects = VoiceEffectsImpl(
        ctx,
        actionExecutorProvider = actionExecutorProvider,
        audioDucker = audioDucker,
        vosk = voskEngine,
        soundPoolProvider = soundPoolProvider,
        phraseRepo = phraseRepo,
        normalizer = textNormalizer,
    )

    @Provides
    fun provideVoiceCoordinator(
        speechRecognizer: SpeechRecognizer,
        voiceEffects: VoiceEffects,
        wakeParser: WakeParser,
        stateMachine: VoiceStateMachine,
        scheduler: Scheduler,
        clock: Clock,
        logger: AppLogger,
        normalizer: TextNormalizer,
        config: VoiceCoordinatorConfig,
    ): VoiceCoordinator = VoiceCoordinator(
        recognizer = speechRecognizer,
        effects = voiceEffects,
        wakeParser = wakeParser,
        stateMachine = stateMachine,
        scheduler = scheduler,
        clock = clock,
        logger = logger,
        normalizer = normalizer,
        config = config,
    )
}
