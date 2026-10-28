package ru.lsn03.voicemediacontroller.core.speech

interface ModelProvider {
    suspend fun resolve(locale: SpeechLocale): Result<ModelLocation>
    fun isAvailable(locale: SpeechLocale): Boolean
}

class ModelUnavailableException(locale: SpeechLocale) :
    IllegalStateException("No speech model available for locale '${locale.tag}'")
