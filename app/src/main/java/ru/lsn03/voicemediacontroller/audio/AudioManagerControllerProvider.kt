package ru.lsn03.voicemediacontroller.audio

import android.media.AudioManager

interface AudioManagerControllerProvider {

    fun getAudioManager():AudioManager

}