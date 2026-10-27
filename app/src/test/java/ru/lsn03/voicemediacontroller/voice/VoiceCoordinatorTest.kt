package ru.lsn03.voicemediacontroller.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.lsn03.voicemediacontroller.core.fakes.FakeClock
import ru.lsn03.voicemediacontroller.core.fakes.FakeScheduler
import ru.lsn03.voicemediacontroller.core.fakes.FakeSpeechRecognizer
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import ru.lsn03.voicemediacontroller.core.speech.SpeechResult
import ru.lsn03.voicemediacontroller.text.TextNormalizer

/**
 * Behavioural pin for [VoiceCoordinator] against the fake-driven dispatch loop.
 *
 * The coordinator-local `lastWakeTriggerMs` starts at `0L`, so the real production
 * debounce rule `now - lastWakeTriggerMs >= wakeDebounceMs` requires the virtual
 * clock to be at least `wakeDebounceMs` for the first wake to pass. All fixtures
 * therefore start the [FakeClock] at [START_MS] = 2000, well past the 1200 ms
 * debounce window.
 */
class VoiceCoordinatorTest {

    private companion object {
        const val START_MS = 2_000L
        const val WAKE_WORD = "джарвис"
        const val PCM_BYTE: Byte = 1
    }

    private class Fixture {
        val clock = FakeClock(initialMs = START_MS)
        val scheduler = FakeScheduler(clock)
        val recognizer = FakeSpeechRecognizer()
        val effects = RecordingVoiceEffects()
        val logger = RecordingLogger()
        val normalizer = TextNormalizer()
        val stateMachine = VoiceStateMachine()
        val wakeParser = WakeParser(wakeWord = { WAKE_WORD })
        val coordinator = VoiceCoordinator(
            recognizer = recognizer,
            effects = effects,
            wakeParser = wakeParser,
            stateMachine = stateMachine,
            scheduler = scheduler,
            clock = clock,
            logger = logger,
            normalizer = normalizer,
            config = VoiceCoordinatorConfig.default(),
        )

        /** Shorthand: deliver one unit of PCM to the coordinator. */
        fun pcm(): ByteArray = byteArrayOf(PCM_BYTE)

        fun feedPcm() { coordinator.onPcm(pcm()) }
    }

    @Test
    fun onPcm_wakeOnlyFinal_entersCommandAndSchedulesTimeout() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))

        f.feedPcm()

        assertThat(f.stateMachine.state).isEqualTo(VoiceState.COMMAND_LISTENING)
        assertThat(f.effects.calls).contains(
            RecordingVoiceEffects.Call("publishText", "Джарвис! Слушаю команду...")
        )
        assertThat(f.effects.calls).contains(RecordingVoiceEffects.Call("enterCommand", null))
        assertThat(f.scheduler.pending()).hasSize(1)
        assertThat(f.scheduler.pending().single().second)
            .isEqualTo(START_MS + VoiceCoordinatorConfig.default().commandTimeoutMs)
    }

    @Test
    fun onPcm_wakeThenCommandFinal_executesCommandAndStaysInWake() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final("джарвис следующий трек"))

        f.feedPcm()

        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(f.effects.calls).containsAtLeast(
            RecordingVoiceEffects.Call("publishText", "Выполняю: следующий трек"),
            RecordingVoiceEffects.Call("onFinalCommand", "следующий трек"),
        ).inOrder()
        // No pending timeout because we stayed in WAKE_LISTENING.
        assertThat(f.scheduler.pending()).isEmpty()
        // Wake recognizer gets reset so next utterance starts clean.
        assertThat(f.recognizer.resetWakeCalls).isEqualTo(1)
    }

    @Test
    fun onPcm_twoWakeFinalsInsideDebounce_firesOnlyTheFirst() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        // Return to WAKE_LISTENING so a second wake would otherwise fire.
        f.coordinator.onTimeout()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))

        // 1199 ms after the first trigger — strictly inside the 1200 ms window.
        f.scheduler.advanceBy(1199L)
        f.feedPcm()

        val commandEntries = f.effects.calls
            .count { it == RecordingVoiceEffects.Call("publishText", "Джарвис! Слушаю команду...") }
        assertThat(commandEntries).isEqualTo(1)
        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(f.logger.entries.map { it.message })
            .contains("Wake debounce: ignored")
    }

    @Test
    fun onPcm_secondWakeFinalExactlyAtDebounceBoundary_fires() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        f.coordinator.onTimeout()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))

        // Exactly 1200 ms — boundary condition, `>=` means it should fire.
        f.scheduler.advanceBy(1200L)
        f.feedPcm()

        val commandEntries = f.effects.calls
            .count { it == RecordingVoiceEffects.Call("publishText", "Джарвис! Слушаю команду...") }
        assertThat(commandEntries).isEqualTo(2)
        assertThat(f.stateMachine.state).isEqualTo(VoiceState.COMMAND_LISTENING)
    }

    @Test
    fun onPcm_multiplePartialWakeResultsWithin250ms_publishesAtMostOnce() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Partial("дж"))
        f.recognizer.scheduleWake(SpeechResult.Partial("джар"))
        f.recognizer.scheduleWake(SpeechResult.Partial("джарв"))

        f.feedPcm()                 // t = 2000, publishes "Слышу: дж"
        f.scheduler.advanceBy(100L) // t = 2100
        f.feedPcm()                 // throttled, no new publish
        f.scheduler.advanceBy(100L) // t = 2200
        f.feedPcm()                 // still throttled (<250 ms since 2000)

        val slyhsuEntries = f.effects.calls.filter {
            it.kind == "publishText" && it.arg?.startsWith("Слышу: ") == true
        }
        assertThat(slyhsuEntries).hasSize(1)
        assertThat(slyhsuEntries.single().arg).isEqualTo("Слышу: дж")
    }

    @Test
    fun onPcm_partialWakeAfterThrottleWindow_publishesAgain() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Partial("дж"))
        f.recognizer.scheduleWake(SpeechResult.Partial("джарв"))

        f.feedPcm()                  // t = 2000
        f.scheduler.advanceBy(250L)  // t = 2250, boundary of throttle window
        f.feedPcm()

        val slyhsuEntries = f.effects.calls.filter {
            it.kind == "publishText" && it.arg?.startsWith("Слышу: ") == true
        }
        assertThat(slyhsuEntries.map { it.arg })
            .containsExactly("Слышу: дж", "Слышу: джарв").inOrder()
    }

    @Test
    fun onTimeout_whileCommandListening_returnsToWakeAndFiresEnterWake() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        f.effects.clear()

        // Advance by exactly commandTimeoutMs — scheduler fires the callback.
        f.scheduler.advanceBy(VoiceCoordinatorConfig.default().commandTimeoutMs)

        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(f.effects.calls).contains(RecordingVoiceEffects.Call("enterWake", null))
        assertThat(f.scheduler.pending()).isEmpty()
    }

    @Test
    fun onPcm_lateCommandFinalAfterTimeoutFires_isNotReexecuted() {
        // KEY REGRESSION GUARD: once the timeout has flipped the SM back to
        // WAKE_LISTENING, a late command final must NOT be executed as a command.
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        // Fire the timeout.
        f.scheduler.advanceBy(VoiceCoordinatorConfig.default().commandTimeoutMs)
        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)

        val commandCallsBefore = f.effects.calls.count { it.kind == "onFinalCommand" }

        // Deliver a late command final. Coordinator is in WAKE now, so the
        // dispatch goes to handleWakePcm, which consults acceptWake — not
        // acceptCommand. The command text was staged on commandQueue and will
        // sit there untouched.
        f.recognizer.scheduleCommand(SpeechResult.Final("следующий трек"))
        f.feedPcm()

        val commandCallsAfter = f.effects.calls.count { it.kind == "onFinalCommand" }
        assertThat(commandCallsAfter).isEqualTo(commandCallsBefore)
        // The command result was never dequeued, since routing went to wake.
        f.recognizer.scheduleWake(SpeechResult.None)
    }

    @Test
    fun onPcm_tripleWake_secondInsideDebounceIgnored_thirdAfterWindowFires() {
        val f = Fixture()
        // t = 2000 — first wake fires.
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        f.coordinator.onTimeout()

        // t = 2000 + 500 = 2500 — strictly inside debounce (2500 - 2000 = 500 < 1200).
        f.scheduler.advanceBy(500L)
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)

        // t = 2500 + 800 = 3300 — 1300 ms after first trigger, past the debounce.
        f.scheduler.advanceBy(800L)
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()

        val wakePublishes = f.effects.calls
            .count { it == RecordingVoiceEffects.Call("publishText", "Джарвис! Слушаю команду...") }
        assertThat(wakePublishes).isEqualTo(2)
        assertThat(f.stateMachine.state).isEqualTo(VoiceState.COMMAND_LISTENING)
    }

    @Test
    fun onPcm_wakeFinalWhileCommandListening_isRejectedAndLogged() {
        val f = Fixture()
        // Enter COMMAND via normal wake path.
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        val publishesBefore = f.effects.calls.count { it.kind == "publishText" }

        // While in COMMAND the coordinator routes PCM to handleCommandPcm —
        // feed a wake-ish final via commandQueue to prove the SM-level invariant.
        // To exercise the "SM rejected" branch, instead construct a coordinator
        // whose SM already sits in COMMAND_LISTENING, then feed a wake final.
        val f2 = Fixture()
        // Shift SM into COMMAND without going through the coordinator so we can
        // then push a WakeHeard event directly through onPcm/handleWakePcm.
        f2.stateMachine.on(VoiceStateEvent.WakeHeard)
        // Coordinator's internal `state` still reads WAKE from stateMachine.state
        // when it was constructed — but we built f2 fresh, so it took the initial
        // snapshot (WAKE). This mirrors the real race: SM says COMMAND, coordinator
        // dispatch still reads WAKE and goes through handleWakePcm → SM rejects.
        f2.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f2.feedPcm()

        val rejectLogged = f2.logger.entries.any {
            it.level == 'd' &&
            it.tag == "VoiceMediaController" &&
            it.message.contains("Wake ignored: SM rejected transition from")
        }
        assertThat(rejectLogged).isTrue()
        // The wake publishText must NOT fire — rejection returns early.
        val wakePublishes = f2.effects.calls.count {
            it == RecordingVoiceEffects.Call("publishText", "Джарвис! Слушаю команду...")
        }
        assertThat(wakePublishes).isEqualTo(0)
        // Sanity: untouched happy-path fixture still saw its own publish.
        assertThat(publishesBefore).isAtLeast(1)
    }

    @Test
    fun onPcm_commandPartial_publishesThrottledPreview() {
        val f = Fixture()
        // Enter COMMAND_LISTENING first.
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        f.effects.clear()

        // Three partials inside the throttle window — only one should publish.
        f.recognizer.scheduleCommand(SpeechResult.Partial("след"))
        f.recognizer.scheduleCommand(SpeechResult.Partial("следующ"))
        f.recognizer.scheduleCommand(SpeechResult.Partial("следующий"))
        f.feedPcm()
        f.scheduler.advanceBy(100L)
        f.feedPcm()
        f.scheduler.advanceBy(100L)
        f.feedPcm()

        val commandPreviews = f.effects.calls.filter {
            it.kind == "publishText" && it.arg?.startsWith("Команда: ") == true
        }
        assertThat(commandPreviews).hasSize(1)
        assertThat(commandPreviews.single().arg).isEqualTo("Команда: след")
    }

    @Test
    fun onPcm_commandFinal_executesCommandAndCancelsTimeout() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        assertThat(f.scheduler.pending()).hasSize(1)
        f.effects.clear()

        f.recognizer.scheduleCommand(SpeechResult.Final("следующий трек"))
        f.feedPcm()

        assertThat(f.effects.calls).contains(
            RecordingVoiceEffects.Call("onFinalCommand", "следующий трек")
        )
        assertThat(f.effects.calls).contains(RecordingVoiceEffects.Call("enterWake", null))
        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(f.scheduler.pending()).isEmpty()
    }

    @Test
    fun onPcm_blankCommandPartial_doesNotPublish() {
        val f = Fixture()
        f.recognizer.scheduleWake(SpeechResult.Final(WAKE_WORD))
        f.feedPcm()
        f.effects.clear()

        f.recognizer.scheduleCommand(SpeechResult.Partial("   "))
        f.feedPcm()

        val publishes = f.effects.calls.count { it.kind == "publishText" }
        assertThat(publishes).isEqualTo(0)
    }

    @Test
    fun setWakeWord_updatesFallbackAndLogsRawInput() {
        // Current coder behaviour: the log line contains the RAW argument,
        // not the normalised one. The normalisation is applied to the field
        // used by subsequent dispatch, but the log pins what the caller sent.
        val f = Fixture()

        f.coordinator.setWakeWord("  АЛИСА  ")

        val logged = f.logger.entries.any {
            it.level == 'd' && it.message.contains("setWakeWord new wake word=  АЛИСА  ")
        }
        assertThat(logged).isTrue()
    }

    @Test
    fun setWakeWord_blankValue_isLoggedAsBlankInput() {
        // Falls back internally to "джарвис" (private state), but the log
        // mirrors the raw input the caller sent.
        val f = Fixture()

        f.coordinator.setWakeWord("   ")

        val logged = f.logger.entries.any {
            it.message.contains("setWakeWord new wake word=   ")
        }
        assertThat(logged).isTrue()
    }

    @Test
    fun onTimeout_whileAlreadyInWakeListening_isNoOp() {
        val f = Fixture()

        f.coordinator.onTimeout()

        assertThat(f.stateMachine.state).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(f.effects.calls).isEmpty()
    }

    @Test
    fun onPcm_wakeNoneResult_producesNoEffects() {
        val f = Fixture()
        // Default: wakeQueue empty => FakeSpeechRecognizer returns None.

        f.feedPcm()

        assertThat(f.effects.calls).isEmpty()
        assertThat(f.scheduler.pending()).isEmpty()
    }
}
