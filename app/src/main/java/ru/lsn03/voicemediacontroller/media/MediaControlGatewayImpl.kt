package ru.lsn03.voicemediacontroller.media

import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class MediaControlGatewayImpl(
    private val provider: MediaControllerProvider,
) : MediaControlGateway {

    override fun play(): Boolean {
        val controller = provider.getTopMediaController() ?: run {
            Log.d(APPLICATION_NAME, "MediaControlGateway: no controller for PLAY")
            return false
        }
        controller.transportControls.play()
        Log.d(APPLICATION_NAME, "MediaControlGateway: PLAY -> ${controller.packageName}")
        return true
    }

    override fun pause(): Boolean {
        val controller = provider.getTopMediaController() ?: run {
            Log.d(APPLICATION_NAME, "MediaControlGateway: no controller for PAUSE")
            return false
        }
        controller.transportControls.pause()
        Log.d(APPLICATION_NAME, "MediaControlGateway: PAUSE -> ${controller.packageName}")
        return true
    }

    override fun next(): Boolean {
        val controller = provider.getTopMediaController() ?: run {
            Log.d(APPLICATION_NAME, "MediaControlGateway: no controller for NEXT")
            return false
        }
        controller.transportControls.skipToNext()
        Log.d(APPLICATION_NAME, "MediaControlGateway: NEXT -> ${controller.packageName}")
        return true
    }

    override fun prev(): Boolean {
        val controller = provider.getTopMediaController() ?: run {
            Log.d(APPLICATION_NAME, "MediaControlGateway: no controller for PREV")
            return false
        }
        controller.transportControls.skipToPrevious()
        Log.d(APPLICATION_NAME, "MediaControlGateway: PREV -> ${controller.packageName}")
        return true
    }
}
