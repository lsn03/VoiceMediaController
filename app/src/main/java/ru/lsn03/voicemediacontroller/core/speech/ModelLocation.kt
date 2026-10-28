package ru.lsn03.voicemediacontroller.core.speech

data class ModelLocation(
    val locale: SpeechLocale,
    val absolutePath: String,
    val source: Source,
) {
    enum class Source { ASSET, DOWNLOADED }
}
