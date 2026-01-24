package ru.lsn03.voicemediacontroller.di

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val handler: Handler,
) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    fun init(onStart: () -> Unit, onStop: () -> Unit, onNoTts: () -> Unit) {
        if (tts != null) return

        tts = TextToSpeech(ctx) { status ->
            ready = (status == TextToSpeech.SUCCESS)
            Log.d(APPLICATION_NAME, "TtsManager::initialization TTS, status=$status")
            if (ready) {
                tts?.language = Locale.getDefault()

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) { handler.post { onStart() } }
                    override fun onDone(utteranceId: String) { handler.post { onStop() } }
                    override fun onError(utteranceId: String) { handler.post { onStop() } }
                })

            } else {
                Log.d(
                    APPLICATION_NAME,
                    "В системе не настроен движок синтеза речи. Нажми «Установить» или открой «Настройки»."
                )
                handler.post(onNoTts)
            }
        }
    }

    fun speak(text: String, utteranceId: String) {
        handler.post {
            if (!ready) {
                Log.w(APPLICATION_NAME, "SpeechGateway: TTS not ready, skip: $text")
                return@post
            }
            val params = Bundle()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun shutdown() {
        handler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ready = false
        }
    }
}
