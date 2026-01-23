package ru.lsn03.voicemediacontroller.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder(
    private val sampleRate: Int,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
) {
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    fun start(onPcm: (pcm: ByteArray) -> Unit, onError: (String) -> Unit = {}) {
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
                Log.e(APPLICATION_NAME, "startRecording failed", t)
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

            // cleanup
            try {
                ar.stop()
            } catch (ex: Throwable) {
                Log.e(APPLICATION_NAME, "ar.stop: $ex")
            }
            try {
                ar.release()
            } catch (ex: Throwable) {
                Log.e(APPLICATION_NAME, "ar.release: $ex")
            }
            audioRecord = null
        }.apply { name = "AudioRecorderThread" }

        thread?.start()
    }

    fun stop() {
        running.set(false)

        // Разбудить блокирующий read(): stop() обычно достаточно
        try { audioRecord?.stop() } catch (_: Throwable) {}

        // Подождать завершения потока чуть-чуть (не обязательно, но полезно)
        try { thread?.join(300) } catch (_: Throwable) {}

        thread = null
        // audioRecord не release'им здесь — это сделает поток в одном месте
    }


}