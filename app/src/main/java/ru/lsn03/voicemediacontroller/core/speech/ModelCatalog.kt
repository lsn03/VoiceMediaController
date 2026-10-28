package ru.lsn03.voicemediacontroller.core.speech

data class ModelCatalogEntry(
    val url: String?,
    val sha256: String,
    val sizeBytes: Long?,
)

object ModelCatalog {

    private val entries: Map<SpeechLocale, ModelCatalogEntry> = mapOf(
        SpeechLocale.RU to ModelCatalogEntry(
            url = null,
            sha256 = "",
            sizeBytes = null,
        ),
        SpeechLocale.EN to ModelCatalogEntry(
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            // TODO(checksum): fill in verified SHA-256 for the Vosk small English model.
            sha256 = "",
            sizeBytes = null,
        ),
    )

    fun entryFor(locale: SpeechLocale): ModelCatalogEntry? = entries[locale]

    fun locales(): List<SpeechLocale> = entries.keys.toList()
}
