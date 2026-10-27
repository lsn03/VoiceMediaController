package ru.lsn03.voicemediacontroller.core.fakes

import ru.lsn03.voicemediacontroller.core.audio.AudioSource

/**
 * Hand-written fake [AudioSource] for single-threaded tests.
 *
 * The pipeline under test calls [start] with two lambdas; the fake stores
 * them and tests drive them synchronously via [emit] (PCM frame) and
 * [emitError] (error path). [isRunning] mirrors the start/stop contract so
 * callers can assert whether the source is currently producing frames.
 *
 * Zero Android deps; zero threading of its own.
 */
class FakeAudioSource : AudioSource {

    private var onPcm: ((ByteArray) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    var isRunning: Boolean = false
        private set

    var startCalls: Int = 0
        private set

    var stopCalls: Int = 0
        private set

    override fun start(onPcm: (ByteArray) -> Unit, onError: (String) -> Unit) {
        startCalls += 1
        // Mirror AndroidAudioSource.start: a second start() while running is a no-op.
        // Keeping the SAME callbacks lets subsequent emit/emitError still reach a sink.
        if (isRunning) return
        this.onPcm = onPcm
        this.onError = onError
        isRunning = true
    }

    override fun stop() {
        stopCalls += 1
        isRunning = false
    }

    /** Deliver a PCM frame to whoever called [start]. No-op if nobody has started yet. */
    fun emit(pcm: ByteArray) {
        onPcm?.invoke(pcm)
    }

    /** Deliver an error to whoever called [start]. No-op if nobody has started yet. */
    fun emitError(msg: String) {
        onError?.invoke(msg)
    }
}
