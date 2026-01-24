package ru.lsn03.voicemediacontroller.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.MEDIA_SESSION_SERVICE
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log
import ru.lsn03.voicemediacontroller.service.JarvisNotificationListener
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class MediaControllerProviderImpl(
    private val context: Context,
) : MediaControllerProvider {

    private fun canControlMediaSessions(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val cn = ComponentName(context, JarvisNotificationListener::class.java)

        val accessGranted = nm.isNotificationListenerAccessGranted(cn)
        val connected = JarvisNotificationListener.Companion.connected

        if (accessGranted && !connected) {
            NotificationListenerService.requestRebind(cn)
            Log.d(APPLICATION_NAME, "requestRebind() called for NotificationListener")
        }

        Log.d(APPLICATION_NAME, "NotifAccess=$accessGranted, listenerConnected=$connected")
        return accessGranted && connected
    }

    override fun getTopMediaController(): MediaController? {
        if (!canControlMediaSessions()) {
            Log.e(
                APPLICATION_NAME,
                "VoiceService::getTopMediaController Notification access не готов: включите доступ и перезапустите приложение/сервис"
            )
            return null
        }

        val msm = context.getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, JarvisNotificationListener::class.java)

        return try {
            msm.getActiveSessions(component).firstOrNull()
        } catch (e: SecurityException) {
            Log.e(
                APPLICATION_NAME,
                "VoiceService::getTopMediaController SecurityException в getActiveSessions (listener ещё не активен)",
                e
            )
            null
        }
    }
}