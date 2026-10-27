package ru.lsn03.voicemediacontroller.voice

import org.json.JSONObject
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository

class GrammarBuilder(
    private val normalizer: TextNormalizer,
    private val logger: AppLogger,
) {

    fun build(wakeWord: String, phrases: List<String>): VoskGrammars {
        val wake = normalizer.normalize(wakeWord).ifBlank { WakeWordRepository.DEFAULT_WAKE_WORD }

        val commands = phrases
            .map(normalizer::normalize)
            .filter { it.isNotBlank() }
            .distinct()

        val commandJson = (commands.ifEmpty { listOf("[unk]") }).toVoskJsonArray()
        val wakeCommandJson = (commands.map { "$wake $it" } + "[unk]").distinct().toVoskJsonArray()
        val wakeWordJson = listOf(wake, "[unk]").toVoskJsonArray()

        return VoskGrammars(wakeWordJson, commandJson, wakeCommandJson)
    }

    private fun List<String>.toVoskJsonArray(): String {
        val sb = StringBuilder()
        sb.append('[')
        forEachIndexed { i, s ->
            if (i > 0) sb.append(',')
            sb.append(JSONObject.quote(s))
            if (sb.length > MAX_JSON_CHARS) {
                logger.w(
                    APPLICATION_NAME,
                    "Grammar JSON too big! len=${sb.length}, items=$size, lastLen=${s.length}"
                )
                return UNK_FALLBACK_JSON
            }
        }
        sb.append(']')
        return sb.toString()
    }

    private companion object {
        const val MAX_JSON_CHARS = 50_000
        const val UNK_FALLBACK_JSON = "[\"[unk]\"]"
    }
}
