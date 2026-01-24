package ru.lsn03.voicemediacontroller.action

import android.media.AudioManager
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider

class VolumeUpActionExecutorImpl(private val audioManagerControllerProvider: AudioManagerControllerProvider) : ActionExecutor {

    override fun getAction(): VoiceAction {
        return VoiceAction.VOLUME_UP;
    }

    override fun execute() {
        val audioManager = audioManagerControllerProvider.getAudioManager();
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }
}