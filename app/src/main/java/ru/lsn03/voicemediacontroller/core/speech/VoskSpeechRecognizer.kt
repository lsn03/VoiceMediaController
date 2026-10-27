package ru.lsn03.voicemediacontroller.core.speech

import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import ru.lsn03.voicemediacontroller.vosk.VoskResult

class VoskSpeechRecognizer(
    private val voskEngine: VoskEngine,
) : SpeechRecognizer {

    override fun updateGrammars(
        wakeJson: String,
        commandJson: String,
        wakeCommandJson: String,
    ) {
        voskEngine.updateGrammars(
            wakeWordJson = wakeJson,
            commandJson = commandJson,
            wakeCommandJson = wakeCommandJson,
        )
    }

    override fun acceptWake(pcm: ByteArray): SpeechResult =
        voskEngine.acceptWake(pcm).toSpeechResult()

    override fun acceptCommand(pcm: ByteArray): SpeechResult =
        voskEngine.acceptCommand(pcm).toSpeechResult()

    override fun resetWake() {
        voskEngine.resetWake()
    }

    override fun resetCommand() {
        voskEngine.resetCommand()
    }

    private fun VoskResult.toSpeechResult(): SpeechResult = when (this) {
        is VoskResult.Final -> SpeechResult.Final(text)
        is VoskResult.Partial -> SpeechResult.Partial(text)
        VoskResult.None -> SpeechResult.None
    }
}
