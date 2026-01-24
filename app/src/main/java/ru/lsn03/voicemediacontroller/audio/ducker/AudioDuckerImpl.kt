package ru.lsn03.voicemediacontroller.audio.ducker

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class AudioDuckerImpl(
    private val audioManager: AudioManager,
    private val handler: Handler,
) : AudioDucker {

    private var focusReq: AudioFocusRequest? = null
    @Volatile
    private var active = false

    private val afListener = AudioManager.OnAudioFocusChangeListener { /* ignore */ }

    override fun start() {
        if (active) return

        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(afListener, handler)
            .build()

        val granted = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(APPLICATION_NAME, "duckStart granted=$granted")

        if (granted) {
            focusReq = req
            active = true
        }
    }

    override fun stop() {
        val req = focusReq ?: run {
            active = false
            return
        }
        audioManager.abandonAudioFocusRequest(req)
        focusReq = null
        active = false
        Log.d(APPLICATION_NAME, "duckStop")
    }

}
