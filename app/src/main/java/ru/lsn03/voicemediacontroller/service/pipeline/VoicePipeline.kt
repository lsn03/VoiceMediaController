package ru.lsn03.voicemediacontroller.service.pipeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.lsn03.voicemediacontroller.core.audio.AudioSource
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.core.speech.SpeechRecognizer
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinator
import ru.lsn03.voicemediacontroller.voice.VoiceEffects
import ru.lsn03.voicemediacontroller.voice.repo.GrammarRepository
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatus
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatusBus

class VoicePipeline(
    private val audio: AudioSource,
    private val recognizer: SpeechRecognizer,
    private val coordinator: VoiceCoordinator,
    private val grammarRepo: GrammarRepository,
    private val wakeRepo: WakeWordRepository,
    private val effects: VoiceEffects,
    private val statusBus: RecognizerStatusBus,
    private val logger: AppLogger,
) {

    fun start(scope: CoroutineScope) {
        effects.start(scope)

        scope.launch {
            wakeRepo.observe().collect { wake ->
                logger.d(APPLICATION_NAME, "VoicePipeline: wake word updated: $wake")
                coordinator.setWakeWord(wake)
            }
        }

        scope.launch {
            grammarRepo.observeGrammars().collect { g ->
                logger.d(
                    APPLICATION_NAME,
                    "VoicePipeline: applying grammars wake=${g.wakeWordGrammarJson.length}, " +
                        "cmd=${g.commandGrammarJson.length}",
                )
                recognizer.updateGrammars(
                    wakeJson = g.wakeWordGrammarJson,
                    commandJson = g.commandGrammarJson,
                    wakeCommandJson = g.wakeCommandGrammarJson,
                )
            }
        }

        audio.start(
            onPcm = coordinator::onPcm,
            onError = { msg ->
                logger.e(APPLICATION_NAME, msg)
                statusBus.post(RecognizerStatus.Error(msg))
            },
        )
    }

    fun stop() {
        audio.stop()
    }
}
