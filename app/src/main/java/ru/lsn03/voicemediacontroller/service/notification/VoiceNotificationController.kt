package ru.lsn03.voicemediacontroller.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository

class VoiceNotificationController(
    private val context: Context,
    private val channelId: String,
) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun build(currentWake: String): Notification {
        val w = currentWake.trim().ifBlank { WakeWordRepository.DEFAULT_WAKE_WORD }
        val titleWake = w.replaceFirstChar { it.uppercaseChar() }

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("🎤 Слушает $titleWake")
            .setContentText("Говори '$titleWake, следующий трек'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun refresh(currentWake: String) {
        notificationManager.notify(NOTIF_ID, build(currentWake))
    }

    companion object {
        const val NOTIF_ID = 1
        private const val CHANNEL_NAME = "Voice Control"
    }
}
