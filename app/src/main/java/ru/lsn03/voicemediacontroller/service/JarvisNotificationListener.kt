package ru.lsn03.voicemediacontroller.service

import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class JarvisNotificationListener : android.service.notification.NotificationListenerService() {

    companion object {
        @Volatile var connected: Boolean = false
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Log.d(APPLICATION_NAME, "JarvisNotificationListener connected=true")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        Log.d(APPLICATION_NAME, "JarvisNotificationListener connected=false")
        // единственное безопасное действие после disconnect
        requestRebind(android.content.ComponentName(this, JarvisNotificationListener::class.java))
    }
}
