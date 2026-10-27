package ru.lsn03.voicemediacontroller.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    wakeRepo: WakeWordRepository
) : ViewModel() {

    val wakeWord: StateFlow<String> =
        wakeRepo.observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
