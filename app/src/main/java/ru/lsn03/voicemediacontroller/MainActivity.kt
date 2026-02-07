package ru.lsn03.voicemediacontroller

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.service.VoiceService
import ru.lsn03.voicemediacontroller.ui.AppRoot
import ru.lsn03.voicemediacontroller.ui.theme.VoiceMediaControlTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var recognizedStatus by mutableStateOf("Жду команду")

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == VoiceEvents.ACTION_RECOGNIZED_TEXT) {
                recognizedStatus = intent.getStringExtra(VoiceEvents.EXTRA_TEXT) ?: "Нет текста"
            }
        }
    }

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

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            voiceReceiver,
            IntentFilter(VoiceEvents.ACTION_RECOGNIZED_TEXT)
        )
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceReceiver)
        super.onStop()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isNotificationAccessGranted()) {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        }

        createNotificationChannel(applicationContext)  // 👇
        requestIgnoreBatteryOptimizations(this)

        enableEdgeToEdge()
        setContent {
            VoiceMediaControlTheme {
                AppRoot(recognizedStatus = recognizedStatus)
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
