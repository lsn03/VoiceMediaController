package ru.lsn03.voicemediacontroller.vosk

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.core.speech.ModelProvider
import ru.lsn03.voicemediacontroller.core.speech.SpeechLocale
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.SAMPLE_RATE
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class VoskEngine(
    private val modelProvider: ModelProvider,
    private val locale: SpeechLocale,
    private val logger: AppLogger,
) {

    private lateinit var model: Model
    private lateinit var wakeRecognizer: Recognizer
    private lateinit var wakeCommandRecognizer: Recognizer
    private lateinit var commandRecognizer: Recognizer

    fun start() {
        val location = runBlocking { modelProvider.resolve(locale) }.getOrThrow()
        logger.d(APPLICATION_NAME, "VoskEngine::start model=${location.absolutePath} locale=${location.locale.tag}")
        model = Model(location.absolutePath)
        updateGrammars(
            wakeWordJson = "[\"джарвис\",\"[unk]\"]",
            commandJson = "[\"[unk]\"]",
            wakeCommandJson = "[\"[unk]\"]"
        )
    }

    @Synchronized
    fun updateGrammars(wakeWordJson: String, commandJson: String, wakeCommandJson: String) {
        wakeRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), wakeWordJson)
        commandRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), commandJson)
        wakeCommandRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), wakeCommandJson)
    }

    fun acceptWake(pcm: ByteArray): VoskResult {
        val isFinal = wakeCommandRecognizer.acceptWaveForm(pcm, pcm.size)
        return if (isFinal) {
            val text = parseText(wakeCommandRecognizer.result).trim()
            if (text.isEmpty()) VoskResult.None else VoskResult.Final(text)
        } else {
            val p = parsePartial(wakeCommandRecognizer.partialResult).trim()
            if (p.isEmpty()) VoskResult.None else VoskResult.Partial(p)
        }
    }

    fun acceptCommand(pcm: ByteArray): VoskResult {
        val isFinal = commandRecognizer.acceptWaveForm(pcm, pcm.size)
        return if (isFinal) {
            val text = parseText(commandRecognizer.result).trim()
            if (text.isEmpty()) VoskResult.None else VoskResult.Final(text)
        } else {
            val p = parsePartial(commandRecognizer.partialResult).trim()
            if (p.isEmpty()) VoskResult.None else VoskResult.Partial(p)
        }
    }

    fun resetWake() {
        wakeCommandRecognizer.reset()
    }

    fun resetCommand() {
        commandRecognizer.reset()
    }

    private fun parseText(jsonString: String): String =
        try {
            JSONObject(jsonString).optString("text", "")
        } catch (_: Exception) {
            ""
        }

    private fun parsePartial(jsonString: String): String =
        try {
            JSONObject(jsonString).optString("partial", "")
        } catch (_: Exception) {
            ""
        }
}
