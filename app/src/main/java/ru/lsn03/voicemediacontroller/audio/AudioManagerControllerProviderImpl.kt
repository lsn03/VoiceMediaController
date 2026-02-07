package ru.lsn03.voicemediacontroller.audio

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager

class AudioManagerControllerProviderImpl(private val context: Context) : AudioManagerControllerProvider {

    override fun getAudioManager(): AudioManager {
        return context.getSystemService(AUDIO_SERVICE) as AudioManager;
    }

}