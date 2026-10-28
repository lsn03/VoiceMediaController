package ru.lsn03.voicemediacontroller.core.speech

import kotlinx.coroutines.flow.Flow

interface ModelDownloader {
    fun download(locale: SpeechLocale, entry: ModelCatalogEntry): Flow<DownloadProgress>
    suspend fun delete(locale: SpeechLocale): Result<Unit>
}

sealed interface DownloadProgress {
    data object Idle : DownloadProgress
    data class Downloading(val bytes: Long, val total: Long?) : DownloadProgress
    data class Verifying(val sha: String) : DownloadProgress
    data object Done : DownloadProgress
    data class Failed(val reason: String) : DownloadProgress
}

class DownloadRequired(val locale: SpeechLocale) :
    IllegalStateException("Speech model for locale '${locale.tag}' must be downloaded")
