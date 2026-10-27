package ru.lsn03.voicemediacontroller.service.pipeline

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.lsn03.voicemediacontroller.core.fakes.FakeAudioSource
import ru.lsn03.voicemediacontroller.core.fakes.FakeSpeechRecognizer
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import ru.lsn03.voicemediacontroller.voice.RecordingVoiceEffects
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinator
import ru.lsn03.voicemediacontroller.voice.VoskGrammars
import ru.lsn03.voicemediacontroller.voice.repo.GrammarRepository
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatus
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatusBus

/**
 * Behavioural pin for [VoicePipeline] — the thin orchestration layer extracted
 * from `VoiceService` in PR 6.
 *
 * The pipeline wires four reactive sources into one shell:
 *   * `wakeRepo.observe()`  -> `coordinator.setWakeWord`
 *   * `grammarRepo.observeGrammars()` -> `recognizer.updateGrammars`
 *   * `audio.onPcm` -> `coordinator.onPcm`
 *   * `audio.onError` -> `statusBus.post(Error)` + `logger.e(...)`
 *
 * All four are exercised here against hand-written fakes. [VoiceCoordinator]
 * is a `final` class; it is mocked with `mockk(relaxed = true)` so the test
 * can assert the exact bytes handed through the `onPcm` function reference
 * and the exact wake-word strings sent through `setWakeWord` — without
 * wiring the whole real coordinator (which has its own 9-dep constructor
 * covered elsewhere by `VoiceCoordinatorTest`).
 *
 * [runTest]'s `backgroundScope` is used so collector launches do not leak
 * beyond the test, and so [stop] semantics (which do NOT cancel the scope
 * — see pin on `stop_doesNotCancelScope_...`) can be asserted deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineTest {

    private val APP_TAG = "VoiceMediaController"

    /**
     * In-file fake: single [MutableStateFlow] of grammars, exposed via the
     * `GrammarRepository` interface. The pipeline only reads
     * `observeGrammars()`, so no other method is needed.
     */
    private class FakeGrammarRepository(initial: VoskGrammars) : GrammarRepository {
        val state = MutableStateFlow(initial)
        override fun observeGrammars(): Flow<VoskGrammars> = state.asStateFlow()
    }

    /**
     * In-file fake: single [MutableStateFlow] of wake word strings. `current()`
     * returns the latest value; `set` updates it synchronously.
     */
    private class FakeWakeWordRepository(initial: String) : WakeWordRepository {
        val state = MutableStateFlow(initial)
        override fun observe(): Flow<String> = state.asStateFlow()
        override suspend fun set(newWord: String) {
            state.value = newWord
        }
        override fun current(): String = state.value
    }

    private fun grammars(wake: String, cmd: String, wakeCmd: String): VoskGrammars =
        VoskGrammars(
            wakeWordGrammarJson = wake,
            commandGrammarJson = cmd,
            wakeCommandGrammarJson = wakeCmd,
        )

    private class Fixture {
        val audio = FakeAudioSource()
        val recognizer = FakeSpeechRecognizer()
        val effects = RecordingVoiceEffects()
        val coordinator: VoiceCoordinator = mockk(relaxed = true)
        val statusBus = RecognizerStatusBus()
        val logger = RecordingLogger()
        val wakeRepo = FakeWakeWordRepository(initial = "джарвис")
        val grammarRepo = FakeGrammarRepository(
            initial = VoskGrammars(
                wakeWordGrammarJson = "W0",
                commandGrammarJson = "C0",
                wakeCommandGrammarJson = "WC0",
            )
        )
        val pipeline = VoicePipeline(
            audio = audio,
            recognizer = recognizer,
            coordinator = coordinator,
            grammarRepo = grammarRepo,
            wakeRepo = wakeRepo,
            effects = effects,
            statusBus = statusBus,
            logger = logger,
        )
    }

    @Test
    fun start_withTwoSequentialGrammarEmissions_forwardsLatestToRecognizer() = runTest {
        val f = Fixture()

        f.pipeline.start(backgroundScope)
        // Allow the grammar collector to pick up the initial StateFlow value.
        testScheduler.runCurrent()

        assertThat(f.recognizer.lastGrammars)
            .isEqualTo(Triple("W0", "C0", "WC0"))

        f.grammarRepo.state.value = grammars("W1", "C1", "WC1")
        testScheduler.runCurrent()

        assertThat(f.recognizer.lastGrammars)
            .isEqualTo(Triple("W1", "C1", "WC1"))
    }

    @Test
    fun start_withWakeWordChange_forwardsToCoordinatorSetWakeWord() = runTest {
        val f = Fixture()

        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        f.wakeRepo.state.value = "алиса"
        testScheduler.runCurrent()

        // Initial "джарвис" was also observed by the collector before "алиса",
        // so verify the exact ordered sequence of setWakeWord calls.
        verify(exactly = 1) { f.coordinator.setWakeWord("джарвис") }
        verify(exactly = 1) { f.coordinator.setWakeWord("алиса") }
    }

    @Test
    fun start_withAudioPcm_forwardsExactBytesToCoordinatorOnPcm() = runTest {
        val f = Fixture()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        val pcm = byteArrayOf(10, 20, 30, 40)
        f.audio.emit(pcm)

        verify(exactly = 1) { f.coordinator.onPcm(pcm) }
    }

    @Test
    fun start_withAudioError_postsErrorStatusAndLogsAtErrorLevel() = runTest {
        val f = Fixture()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        f.audio.emitError("boom")

        assertThat(f.statusBus.status.value)
            .isEqualTo(RecognizerStatus.Error("boom"))

        val errorEntries = f.logger.entries.filter { it.level == 'e' }
        assertThat(errorEntries).hasSize(1)
        assertThat(errorEntries.single().tag).isEqualTo(APP_TAG)
        assertThat(errorEntries.single().message).isEqualTo("boom")
    }

    @Test
    fun start_startsAudioSource() = runTest {
        val f = Fixture()

        assertThat(f.audio.isRunning).isFalse()

        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        assertThat(f.audio.isRunning).isTrue()
        assertThat(f.audio.startCalls).isEqualTo(1)
    }

    @Test
    fun start_invokesEffectsStartExactlyOnce() = runTest {
        val f = Fixture()

        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        val startCalls = f.effects.calls.count { it.kind == "start" }
        assertThat(startCalls).isEqualTo(1)
    }

    @Test
    fun stop_stopsAudioSource() = runTest {
        val f = Fixture()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()
        assertThat(f.audio.isRunning).isTrue()

        f.pipeline.stop()

        assertThat(f.audio.isRunning).isFalse()
        assertThat(f.audio.stopCalls).isEqualTo(1)
    }

    @Test
    fun stop_doesNotCancelScope_wakeCollectorContinuesToDeliverLaterEmissions() = runTest {
        // Pins: VoicePipeline.stop() ONLY stops the audio source; it does NOT
        // cancel the collector coroutines launched on the caller-provided
        // scope. The service owns that scope and cancels it separately in
        // onDestroy. Here we observe a post-stop wake emission still reaching
        // the coordinator.
        val f = Fixture()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        f.pipeline.stop()

        f.wakeRepo.state.value = "post-stop-wake"
        testScheduler.runCurrent()

        verify(exactly = 1) { f.coordinator.setWakeWord("post-stop-wake") }
    }

    @Test
    fun stop_withoutStart_isNoOp_doesNotThrowAndStillCallsAudioStop() = runTest {
        // Pins: stop() does not require a prior start(); it unconditionally
        // forwards to audio.stop(). The audio source itself must tolerate
        // a stop-without-start — our FakeAudioSource mirrors that.
        val f = Fixture()

        f.pipeline.stop()

        assertThat(f.audio.stopCalls).isEqualTo(1)
        assertThat(f.audio.isRunning).isFalse()
    }

    @Test
    fun start_twiceOnSameFixture_pinsAudioStartIdempotence() = runTest {
        // Pins what happens when the same pipeline is started twice in a row
        // without a stop() in between.
        //
        // VoicePipeline.start() does NOT guard against re-entrance — every
        // call launches two new collector coroutines AND re-invokes
        // audio.start(). On the audio side, FakeAudioSource matches the real
        // AndroidAudioSource contract: a second start() while already running
        // is a no-op (early return in `AndroidAudioSource.start`,
        // `running.getAndSet(true)` branch).
        //
        // So after two start() calls we observe:
        //   * audio.startCalls == 2 (two attempts),
        //   * audio.isRunning == true (still the first run),
        //   * TWO identical wake-word observations (one per collector).
        val f = Fixture()

        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        assertThat(f.audio.startCalls).isEqualTo(2)
        assertThat(f.audio.isRunning).isTrue()
        // Both wake collectors sampled the initial StateFlow value.
        verify(exactly = 2) { f.coordinator.setWakeWord("джарвис") }
    }

    @Test
    fun start_logsDebugLineWhenGrammarsAreApplied() = runTest {
        // Pin: on every grammar emission the pipeline writes a single debug
        // line reporting the three JSON lengths. This lets the operator
        // correlate recognizer re-arming with grammar updates in device logs.
        val f = Fixture()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        val applyLines = f.logger.entries.filter {
            it.level == 'd' && it.message.contains("applying grammars")
        }
        assertThat(applyLines).isNotEmpty()
        val line = applyLines.last().message
        // Fixture initial grammars: W0 (2 chars), C0 (2 chars).
        assertThat(line).contains("wake=2")
        assertThat(line).contains("cmd=2")
    }

    @Test
    fun start_logsDebugLineWhenWakeWordChanges() = runTest {
        // Pin: every wake-word emission produces a debug log line that embeds
        // the new value. Operators rely on this to confirm user edits in
        // Settings propagate through the collector.
        val f = Fixture()
        f.pipeline.start(backgroundScope)
        testScheduler.runCurrent()

        f.wakeRepo.state.value = "siri"
        testScheduler.runCurrent()

        val wakeLines = f.logger.entries.filter {
            it.level == 'd' && it.message.contains("wake word updated")
        }
        assertThat(wakeLines.map { it.message })
            .containsExactly(
                "VoicePipeline: wake word updated: джарвис",
                "VoicePipeline: wake word updated: siri",
            )
            .inOrder()
    }
}
