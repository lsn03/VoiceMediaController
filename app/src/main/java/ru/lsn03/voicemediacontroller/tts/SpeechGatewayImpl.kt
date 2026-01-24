package ru.lsn03.voicemediacontroller.tts

import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME


class SpeechGatewayImpl(
    private val handler: Handler,
    private val isReady: () -> Boolean,
    private val ttsProvider: () -> TextToSpeech?,
) : SpeechGateway {

    override fun speak(text: String, utteranceId: String) {
        handler.post {
            if (!isReady()) {
                Log.w(APPLICATION_NAME, "SpeechGateway: TTS not ready, skip: $text")
                return@post
            }
            val params = Bundle()
            ttsProvider()?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    override fun shutdown() {
        handler.post {
            ttsProvider()?.stop()
            ttsProvider()?.shutdown()
        }
    }
}


