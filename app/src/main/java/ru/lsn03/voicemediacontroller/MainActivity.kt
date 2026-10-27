package ru.lsn03.voicemediacontroller

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import ru.lsn03.voicemediacontroller.ui.AppRoot
import ru.lsn03.voicemediacontroller.ui.theme.VoiceMediaControlTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private fun isNotificationAccessGranted(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        val cn = android.content.ComponentName(this, ru.lsn03.voicemediacontroller.service.JarvisNotificationListener::class.java)
        return nm.isNotificationListenerAccessGranted(cn)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = activity.packageName
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isNotificationAccessGranted()) {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        }

        createNotificationChannel(applicationContext)
        requestIgnoreBatteryOptimizations(this)

        enableEdgeToEdge()
        setContent {
            VoiceMediaControlTheme {
                AppRoot()
            }
        }

    }


    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "voice_channel",
                "Voice Control",
                NotificationManager.IMPORTANCE_LOW
            )

            val systemService = getSystemService(context, NotificationManager::class.java)
            systemService?.createNotificationChannel(channel)

        }

    }

}
