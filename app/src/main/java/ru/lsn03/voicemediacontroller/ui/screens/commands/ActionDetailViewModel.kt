package ru.lsn03.voicemediacontroller.ui.screens.commands

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.db.VoicePhraseEntity
import ru.lsn03.voicemediacontroller.voice.VoiceCommandRepository
import javax.inject.Inject

data class PhraseUi(
    val id: Long,
    val text: String,
    val enabled: Boolean
)

@HiltViewModel
class ActionDetailViewModel @Inject constructor(
    private val repo: VoiceCommandRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // ожидаем, что навигация кладёт actionName в аргументы
    private val actionName: VoiceAction = VoiceAction.valueOf(checkNotNull(savedStateHandle["action"]))

    val phrases: StateFlow<List<PhraseUi>> =
        repo.observePhrases(actionName)
            .map { list -> list.map { it.toUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<String>(capacity = Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    fun addPhrase(text: String) = viewModelScope.launch {
        val r = repo.addPhrase(actionName, text)
        if (r.isFailure) emitError(r.exceptionOrNull())
    }

    fun editPhrase(id: Long, text: String) = viewModelScope.launch {
        val r = repo.updatePhraseText(id, text)
        if (r.isFailure) emitError(r.exceptionOrNull())
    }

    fun toggleEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        val r = repo.setPhraseEnabled(id, enabled)
        if (r.isFailure) emitError(r.exceptionOrNull())
    }

    fun deletePhrase(id: Long) = viewModelScope.launch {
        val r = repo.deletePhrase(id)
        if (r.isFailure) emitError(r.exceptionOrNull())
    }

    fun resetToDefaults() = viewModelScope.launch {
        val r = repo.resetToDefaults(actionName)
        if (r.isFailure) emitError(r.exceptionOrNull())
    }

    private suspend fun emitError(e: Throwable?) {
        val msg = when (e?.message) {
            "EMPTY" -> "Пустая фраза"
            "DUPLICATE" -> "Такая фраза уже существует"
            "LAST_ENABLED" -> "Нельзя отключить/удалить последнюю фразу"
            else -> "Ошибка"
        }
        _events.send(msg)
    }


    private fun VoicePhraseEntity.toUi() = PhraseUi(
        id = id,
        text = phrase,
        enabled = enabled
    )
}