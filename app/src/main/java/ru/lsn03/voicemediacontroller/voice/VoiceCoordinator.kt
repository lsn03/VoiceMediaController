package ru.lsn03.voicemediacontroller.voice

import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.core.speech.SpeechRecognizer
import ru.lsn03.voicemediacontroller.core.speech.SpeechResult
import ru.lsn03.voicemediacontroller.core.time.Clock
import ru.lsn03.voicemediacontroller.core.time.Scheduler
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository

class VoiceCoordinator(
    private val recognizer: SpeechRecognizer,
    private val effects: VoiceEffects,
    private val wakeParser: WakeParser,
    private val stateMachine: VoiceStateMachine,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val logger: AppLogger,
    private val normalizer: TextNormalizer,
    private val config: VoiceCoordinatorConfig,
) {

    // Mirrored from WakeWordRepository.observe() via VoiceService; read on the audio
    // callback thread. @Volatile guarantees visibility of the String reference.
    @Volatile
    private var wakeWord: String = WakeWordRepository.DEFAULT_WAKE_WORD

    // Throttling timestamps and the visible-state mirror are mutated from the audio
    // callback thread exclusively; marked @Volatile so main-thread observers (timeout
    // scheduler callback) read a consistent value.
    @Volatile
    private var lastUiUpdateMs = 0L

    @Volatile
    private var lastWakeTriggerMs = 0L

    // Coordinator-local view of the state. Mirrors the state machine, with the
    // addition of a transient SWITCHING flag held while transition side-effects run.
    @Volatile
    private var state: VoiceState = stateMachine.state

    // Sentinel key for the single command-timeout callback registered with the scheduler.
    private object CommandTimeoutToken

    fun onPcm(pcm: ByteArray) {
        val now = clock.elapsedRealtimeMs()

        when (state) {
            VoiceState.SWITCHING -> Unit
            VoiceState.WAKE_LISTENING -> handleWakePcm(pcm, now)
            VoiceState.COMMAND_LISTENING -> handleCommandPcm(pcm, now)
        }
    }

    fun setWakeWord(word: String) {
        logger.d(APPLICATION_NAME, "Coordinator::setWakeWord new wake word=$word")
        wakeWord = word.trim().lowercase().ifBlank { WakeWordRepository.DEFAULT_WAKE_WORD }
    }

    fun onTimeout() {
        if (state == VoiceState.COMMAND_LISTENING) {
            logger.d(APPLICATION_NAME, "VoiceCoordinator::onTimeout -> WAKE")
            val next = stateMachine.on(VoiceStateEvent.CommandTimeout)
            transitionTo(next)
        }
    }

    private fun handleCommandPcm(pcm: ByteArray, now: Long) {
        when (val r = recognizer.acceptCommand(pcm)) {
            is SpeechResult.Final -> {
                logger.d(APPLICATION_NAME, "VoiceService:: CMD final: ${r.text}")
                effects.onFinalCommand(r.text)
                val next = stateMachine.on(VoiceStateEvent.CommandRecognised)
                transitionTo(next)
            }

            is SpeechResult.Partial -> {
                val partialText = r.text.trim()
                if (partialText.isNotEmpty() && now - lastUiUpdateMs >= config.uiThrottleMs) {
                    lastUiUpdateMs = now
                    effects.publishText("Команда: $partialText")
                }
            }

            SpeechResult.None -> Unit
        }
    }

    private fun handleWakePcm(pcm: ByteArray, now: Long) {
        when (val r = recognizer.acceptWake(pcm)) {
            is SpeechResult.Final -> {
                val txt = normalizer.normalize(r.text)
                logger.d(APPLICATION_NAME, "VoiceCoordinator::handleWakePcm WAKE final: $txt")

                when (val outcome = wakeParser.parseFinal(txt)) {
                    is WakeOutcome.WakeHeard -> {
                        val now2 = clock.elapsedRealtimeMs()
                        if (now2 - lastWakeTriggerMs >= config.wakeDebounceMs) {
                            lastWakeTriggerMs = now2
                            val previous = stateMachine.state
                            val next = stateMachine.on(VoiceStateEvent.WakeHeard)
                            if (next == previous) {
                                logger.d(APPLICATION_NAME, "Wake ignored: SM rejected transition from $previous")
                                return
                            }
                            effects.publishText("${outcome.titleCased}! Слушаю команду...")
                            transitionTo(next)
                        } else {
                            logger.d(APPLICATION_NAME, "Wake debounce: ignored")
                        }
                    }

                    is WakeOutcome.WakeThenCommand -> {
                        effects.publishText("Выполняю: ${outcome.command}")
                        effects.onFinalCommand(outcome.command)
                        // Staying in WAKE: reset the wake recognizer so the next utterance starts clean.
                        recognizer.resetWake()
                    }

                    WakeOutcome.Nothing -> {
                        recognizer.resetWake()
                    }

                    is WakeOutcome.HeardPartial -> Unit
                }
            }

            is SpeechResult.Partial -> {
                when (val outcome = wakeParser.parsePartial(r.text)) {
                    is WakeOutcome.HeardPartial -> {
                        if (now - lastUiUpdateMs >= config.uiThrottleMs) {
                            lastUiUpdateMs = now
                            effects.publishText("Слышу: ${outcome.text}")
                        }
                    }
                    else -> Unit
                }
            }

            SpeechResult.None -> Unit
        }
    }

    private fun transitionTo(target: VoiceState) {
        if (state == target) return

        state = VoiceState.SWITCHING
        when (target) {
            VoiceState.WAKE_LISTENING -> {
                scheduler.cancel(CommandTimeoutToken)
                effects.enterWake()
            }

            VoiceState.COMMAND_LISTENING -> {
                effects.enterCommand()
                scheduler.cancel(CommandTimeoutToken)
                scheduler.postDelayed(config.commandTimeoutMs, CommandTimeoutToken) { onTimeout() }
            }

            VoiceState.SWITCHING -> Unit
        }
        state = target
    }
}
