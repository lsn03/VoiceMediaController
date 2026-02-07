package ru.lsn03.voicemediacontroller.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.lsn03.voicemediacontroller.voice.VoiceCommandRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: VoiceCommandRepository
) : ViewModel() {

    val wakeWord: StateFlow<String> =
        repo.observeWakeWord()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
