package ru.lsn03.voicemediacontroller.core.speech

@JvmInline
value class SpeechLocale(val tag: String) {

    init {
        require(tag.isNotBlank()) { "SpeechLocale tag must not be blank" }
    }

    companion object {
        val RU: SpeechLocale = SpeechLocale("ru")
        val EN: SpeechLocale = SpeechLocale("en")
    }
}
