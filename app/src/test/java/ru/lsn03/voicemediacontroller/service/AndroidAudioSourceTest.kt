package ru.lsn03.voicemediacontroller.service

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Robolectric-driven tests for [AndroidAudioSource].
 *
 * Robolectric 4.13 ships a `ShadowAudioRecord` whose default source returns
 * zero bytes per `read(...)`. That makes the normal happy-path loop spin
 * forever reading zero-length frames, which is harmless and matches how
 * the real device behaves when no mic input is available.
 *
 * To exercise the error branch deterministically we pass an invalid
 * `channelConfig` so `AudioRecord.getMinBufferSize(...)` returns a
 * non-positive sentinel and the worker thread falls into:
 *
 * ```kotlin
 * if (bufferSize <= 0) {
 *     onError("AudioRecord.getMinBufferSize failed: $bufferSize")
 *     running.set(false)
 *     return@Thread
 * }
 * ```
 *
 * Tests that exercise the normal `start()` -> `stop()` sequence use the
 * default channel config; `stop()` sets `running = false`, calls
 * `ar.stop()` on the shadowed AudioRecord, and joins the worker within
 * [AndroidAudioSource]'s 500 ms contract.
 *
 * SDK 33 keeps the Robolectric sandbox consistent with the rest of the
 * suite (`HandlerSchedulerTest`, `GrammarRepositoryTest`,
 * `VoiceNotificationControllerTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AndroidAudioSourceTest {

    private val logger = RecordingLogger()

    private val INVALID_CHANNEL_CONFIG = AudioFormat.CHANNEL_INVALID

    /** Finds any live thread named "AudioRecorderThread". */
    private fun findLiveWorker(): Thread? =
        Thread.getAllStackTraces().keys.firstOrNull {
            it.name == "AudioRecorderThread" && it.isAlive
        }

    /** Spin until [cond] holds or [timeoutMs] elapses. */
    private fun waitUntil(timeoutMs: Long = 1_000L, cond: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (cond()) return true
            Thread.yield()
        }
        return cond()
    }

    @Test
    fun start_withInvalidChannelConfig_invokesOnErrorAndClearsRunningState() {
        // Invalid channel config forces AudioRecord.getMinBufferSize(...)
        // to return a non-positive sentinel. The worker must take the error
        // branch, call onError exactly once, clear `running`, and exit.
        val source = AndroidAudioSource(
            sampleRate = 16_000,
            logger = logger,
            channelConfig = INVALID_CHANNEL_CONFIG,
        )
        val errors = CopyOnWriteArrayList<String>()
        val errorLatch = CountDownLatch(1)

        source.start(
            onPcm = { /* never called in the error branch */ },
            onError = { msg ->
                errors.add(msg)
                errorLatch.countDown()
            },
        )

        val reported = errorLatch.await(1, TimeUnit.SECONDS)
        assertThat(reported).isTrue()
        assertThat(errors).hasSize(1)
        assertThat(errors.single()).startsWith("AudioRecord.getMinBufferSize failed:")

        // Worker must terminate soon after reporting.
        assertThat(waitUntil(500L) { findLiveWorker() == null }).isTrue()

        // `running` was cleared: a second start() is permitted and must
        // follow the same error branch, producing another onError.
        val errorLatch2 = CountDownLatch(1)
        source.start(
            onPcm = { },
            onError = { errorLatch2.countDown() },
        )
        assertThat(errorLatch2.await(1, TimeUnit.SECONDS)).isTrue()

        source.stop()
    }

    @Test
    fun startStopStart_sequenceTerminatesEachWorkerThreadWithin500ms() {
        // Default channel config takes the happy path through the shadow:
        // AudioRecord constructor succeeds, read() returns 0 forever, and
        // the worker spins until stop() sets running = false. The contract
        // we pin is that stop() joins the worker within 500 ms, so no live
        // "AudioRecorderThread" exists after each stop().
        val source = AndroidAudioSource(sampleRate = 16_000, logger = logger)

        repeat(3) { iteration ->
            source.start(onPcm = { }, onError = { })

            // Let the worker enter the read-loop before we tear it down.
            assertThat(waitUntil(500L) { findLiveWorker() != null }).isTrue()

            source.stop()

            // Contract: stop() joins the worker within JOIN_TIMEOUT_MS (500 ms).
            // After stop() returns no live "AudioRecorderThread" must exist.
            val gone = waitUntil(500L) { findLiveWorker() == null }
            check(gone) { "AudioRecorderThread still live after stop() on iteration $iteration" }
            assertThat(gone).isTrue()

            // No leak warning: stop() joined cleanly without exceeding 500 ms.
            val leakWarnings = logger.entries.filter {
                it.level == 'w' && it.message.contains("did not terminate")
            }
            assertThat(leakWarnings).isEmpty()
        }
    }

    @Test
    fun stop_onNeverStartedSource_isANoOp_doesNotThrowOrEmitError() {
        val source = AndroidAudioSource(sampleRate = 16_000, logger = logger)
        val errors = CopyOnWriteArrayList<String>()

        // No start(); stop() alone must be safe.
        source.stop()

        // No live worker thread.
        assertThat(findLiveWorker()).isNull()
        // No warning or error log entries from stop().
        val warnings = logger.entries.filter { it.level == 'w' }
        val errorLogs = logger.entries.filter { it.level == 'e' }
        assertThat(warnings).isEmpty()
        assertThat(errorLogs).isEmpty()
        assertThat(errors).isEmpty()
    }

    @Test
    fun start_whileAlreadyRunning_isANoOp_secondStartDoesNotSpawnSecondThread() {
        // Pins the running flag guard:
        // `if (running.getAndSet(true)) return`. A second start() while the
        // first worker is still looping must not spawn a second thread.
        val source = AndroidAudioSource(sampleRate = 16_000, logger = logger)

        source.start(onPcm = { }, onError = { })
        assertThat(waitUntil(500L) { findLiveWorker() != null }).isTrue()

        // Count live workers before and after a second start().
        val before = Thread.getAllStackTraces().keys.count {
            it.name == "AudioRecorderThread" && it.isAlive
        }
        source.start(onPcm = { }, onError = { })
        val after = Thread.getAllStackTraces().keys.count {
            it.name == "AudioRecorderThread" && it.isAlive
        }
        assertThat(after).isEqualTo(before)

        source.stop()
        assertThat(waitUntil(500L) { findLiveWorker() == null }).isTrue()
    }
}
