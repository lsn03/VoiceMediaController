package ru.lsn03.voicemediacontroller.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import ru.lsn03.voicemediacontroller.core.audio.AudioSource
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import java.util.concurrent.atomic.AtomicBoolean

class AndroidAudioSource(
    private val sampleRate: Int,
    private val logger: AppLogger,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
) : AudioSource {

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    override fun start(onPcm: (ByteArray) -> Unit, onError: (String) -> Unit) {
        if (running.getAndSet(true)) return

        thread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize <= 0) {
                onError("AudioRecord.getMinBufferSize failed: $bufferSize")
                running.set(false)
                return@Thread
            }

            val ar = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
            audioRecord = ar

            try {
                ar.startRecording()
            } catch (t: Throwable) {
                logger.e(APPLICATION_NAME, "startRecording failed", t)
                onError("AudioRecord startRecording failed")
                stop()
                return@Thread
            }

            val shortBuf = ShortArray(bufferSize / 2)

            while (running.get()) {
                val read = ar.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) continue

                val pcm = ByteArray(read * 2)
                for (i in 0 until read) {
                    pcm[i * 2] = (shortBuf[i].toInt() and 0x00ff).toByte()
                    pcm[i * 2 + 1] = (shortBuf[i].toInt() shr 8).toByte()
                }

                onPcm(pcm)
            }

            try {
                ar.stop()
            } catch (ex: Throwable) {
                logger.e(APPLICATION_NAME, "ar.stop: $ex")
            }
            try {
                ar.release()
            } catch (ex: Throwable) {
                logger.e(APPLICATION_NAME, "ar.release: $ex")
            }
            audioRecord = null
        }.apply { name = "AudioRecorderThread" }

        thread?.start()
    }

    override fun stop() {
        running.set(false)

        try { audioRecord?.stop() } catch (_: Throwable) {}

        // Contractual bound: callers (e.g. VoicePipeline.stop) must observe the
        // worker thread terminated within 500 ms so tests can assert thread lifecycle.
        val worker = thread
        try {
            worker?.join(JOIN_TIMEOUT_MS)
        } catch (_: Throwable) {}
        if (worker != null && worker.isAlive) {
            logger.w(APPLICATION_NAME, "AudioRecorderThread did not terminate within ${JOIN_TIMEOUT_MS}ms")
        }

        thread = null
    }

    private companion object {
        const val JOIN_TIMEOUT_MS = 500L
    }
}
