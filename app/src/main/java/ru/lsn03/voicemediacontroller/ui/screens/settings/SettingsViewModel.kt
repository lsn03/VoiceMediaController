package ru.lsn03.voicemediacontroller.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.voice.VoiceCommandRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: VoiceCommandRepository,
    private val soundPrefs: SoundPrefs
) : ViewModel() {

    val wakeWord: StateFlow<String> =
        repo.observeWakeWord()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "джарвис")

    // draft для поля ввода
    private val _wakeDraft = MutableStateFlow("")
    val wakeDraft: StateFlow<String> = _wakeDraft

    // громкости
    private val _happyVol = MutableStateFlow(soundPrefs.getHappyVol())
    val happyVol: StateFlow<Float> = _happyVol

    private val _sadVol = MutableStateFlow(soundPrefs.getSadVol())
    val sadVol: StateFlow<Float> = _sadVol

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()


    private val _isEditingWake = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            wakeWord.collect { ww ->
                if (!_isEditingWake.value) {
                    _wakeDraft.value = ww
                }
            }
        }
    }

    fun onWakeDraftChange(v: String) {
        _isEditingWake.value = true
        _wakeDraft.value = v
    }

    fun onWakeEditDone() {
        _isEditingWake.value = false
    }


    fun saveWakeWord() = viewModelScope.launch {
        val text = _wakeDraft.value.trim()
        Log.d(APPLICATION_NAME, "SettingiewModel::saveWakeWord save wake word=$text")
        repo.setWakeWord(text)
        _events.send("Wake-word сохранён")
        _wakeDraft.value = text.trim().lowercase()
    }

    fun setHappyVol(v: Float) {
        val vv = v.coerceIn(0f, 1f)
        _happyVol.value = vv
        soundPrefs.setHappyVol(vv)
    }

    fun setSadVol(v: Float) {
        val vv = v.coerceIn(0f, 1f)
        _sadVol.value = vv
        soundPrefs.setSadVol(vv)
    }
}
