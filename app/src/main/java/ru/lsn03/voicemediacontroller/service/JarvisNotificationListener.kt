package ru.lsn03.voicemediacontroller.service

import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class JarvisNotificationListener : android.service.notification.NotificationListenerService() {

    companion object {
        val connectedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

        val connected: Boolean get() = connectedFlow.value
    }


    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedFlow.value = true
        Log.d(APPLICATION_NAME, "JarvisNotificationListener connected=true")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connectedFlow.value = false
        Log.d(APPLICATION_NAME, "JarvisNotificationListener connected=false")
        // единственное безопасное действие после disconnect
        requestRebind(android.content.ComponentName(this, JarvisNotificationListener::class.java))
    }
}
