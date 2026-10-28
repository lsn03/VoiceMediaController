package ru.lsn03.voicemediacontroller.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lsn03.voicemediacontroller.core.speech.DownloadProgress
import ru.lsn03.voicemediacontroller.core.speech.ModelCatalog
import ru.lsn03.voicemediacontroller.core.speech.ModelDownloader
import ru.lsn03.voicemediacontroller.core.speech.ModelProvider
import ru.lsn03.voicemediacontroller.core.speech.SpeechLocale
import javax.inject.Inject

data class LocaleUi(
    val locale: SpeechLocale,
    val displayName: String,
    val status: Status,
    val downloadable: Boolean,
    val progress: DownloadProgress = DownloadProgress.Idle,
    val selected: Boolean = false,
) {
    enum class Status { NotInstalled, InstalledFromAsset, Downloaded, Downloading, Error }
}

data class ModelsUiState(
    val items: List<LocaleUi> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val provider: ModelProvider,
    private val downloader: ModelDownloader,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelsUiState())
    val state: StateFlow<ModelsUiState> = _state.asStateFlow()

    private val activeJobs: MutableMap<SpeechLocale, Job> = mutableMapOf()
    private var selected: SpeechLocale? = null

    init {
        refresh()
    }

    fun onDownload(locale: SpeechLocale) {
        if (activeJobs[locale]?.isActive == true) return
        val entry = ModelCatalog.entryFor(locale)
        if (entry == null) {
            updateProgress(locale, DownloadProgress.Failed("No catalog entry for locale '${locale.tag}'"))
            return
        }
        val job = viewModelScope.launch {
            downloader.download(locale, entry).collect { progress ->
                updateProgress(locale, progress)
            }
            refreshLocale(locale)
        }
        activeJobs[locale] = job
    }

    fun onDelete(locale: SpeechLocale) {
        viewModelScope.launch {
            activeJobs[locale]?.cancel()
            activeJobs.remove(locale)
            val result = downloader.delete(locale)
            if (result.isFailure) {
                _state.update { it.copy(message = result.exceptionOrNull()?.message ?: "Delete failed") }
            }
            refreshLocale(locale)
        }
    }

    fun onSelect(locale: SpeechLocale) {
        selected = locale
        _state.update { s ->
            s.copy(items = s.items.map { it.copy(selected = it.locale == locale) })
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun refresh() {
        viewModelScope.launch {
            val items = ModelCatalog.locales().map { locale -> buildItem(locale) }
            _state.update { it.copy(items = items) }
        }
    }

    private suspend fun refreshLocale(locale: SpeechLocale) {
        val rebuilt = buildItem(locale)
        _state.update { s ->
            s.copy(items = s.items.map { if (it.locale == locale) rebuilt else it })
        }
    }

    private suspend fun buildItem(locale: SpeechLocale): LocaleUi {
        val installed = provider.isAvailable(locale)
        val entry = ModelCatalog.entryFor(locale)
        val downloadable = !entry?.url.isNullOrBlank()
        val status = when {
            !installed -> LocaleUi.Status.NotInstalled
            !downloadable -> LocaleUi.Status.InstalledFromAsset
            else -> LocaleUi.Status.Downloaded
        }
        return LocaleUi(
            locale = locale,
            displayName = displayName(locale),
            status = status,
            downloadable = downloadable,
            selected = selected == locale,
        )
    }

    private fun updateProgress(locale: SpeechLocale, progress: DownloadProgress) {
        _state.update { s ->
            s.copy(
                items = s.items.map { item ->
                    if (item.locale != locale) item
                    else {
                        val newStatus = when (progress) {
                            is DownloadProgress.Downloading,
                            is DownloadProgress.Verifying -> LocaleUi.Status.Downloading
                            is DownloadProgress.Done -> LocaleUi.Status.Downloaded
                            is DownloadProgress.Failed -> LocaleUi.Status.Error
                            DownloadProgress.Idle -> item.status
                        }
                        item.copy(status = newStatus, progress = progress)
                    }
                }
            )
        }
    }

    private fun displayName(locale: SpeechLocale): String = when (locale) {
        SpeechLocale.RU -> "Russian"
        SpeechLocale.EN -> "English"
        else -> locale.tag
    }
}
