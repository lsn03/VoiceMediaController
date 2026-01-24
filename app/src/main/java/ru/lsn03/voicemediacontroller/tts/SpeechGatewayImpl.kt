package ru.lsn03.voicemediacontroller.tts

import ru.lsn03.voicemediacontroller.di.TtsManager
import javax.inject.Inject


class SpeechGatewayImpl @Inject constructor(
    private val ttsManager: TtsManager
) : SpeechGateway {
    override fun speak(text: String, utteranceId: String) =
        ttsManager.speak(text, utteranceId)

    override fun shutdown() =
        ttsManager.shutdown()
}



