package ru.lsn03.voicemediacontroller.text

class TextNormalizer {
    fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
}
