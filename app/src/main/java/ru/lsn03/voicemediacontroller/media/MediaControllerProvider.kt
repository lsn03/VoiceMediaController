package ru.lsn03.voicemediacontroller.media

import android.media.session.MediaController

interface MediaControllerProvider {
    fun getTopMediaController(): MediaController?
}
