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



@Composable
fun VoiceControlScreen(modifier: Modifier = Modifier,
                       recognizedStatus: String
) {
    val context = LocalContext.current

    val nm = remember { context.getSystemService(NotificationManager::class.java) }
    val cn = remember { android.content.ComponentName(context, ru.lsn03.voicemediacontroller.service.JarvisNotificationListener::class.java) }

    fun isNotifAccessGranted(): Boolean = nm.isNotificationListenerAccessGranted(cn)

    var notifAccess by remember { mutableStateOf(false) }
    var listenerConnected by remember { mutableStateOf(false) }


    val scrollState = rememberScrollState()

    val prefs = remember { context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE) }
    var happyVol by remember { mutableStateOf(prefs.getFloat("happy_vol", 0.6f)) }
    var sadVol by remember { mutableStateOf(prefs.getFloat("sad_vol", 0.6f)) }

    fun sendVolumes() {
        val i = Intent(context, VoiceService::class.java).apply {
            putExtra("happy_vol", happyVol)
            putExtra("sad_vol", sadVol)
        }
        ContextCompat.startForegroundService(context, i)
    }

    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Остановлен") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
            isRunning = true
            status = "Ассисент слушает вас..."
        } else {
            isRunning = false
            status = "Нет разрешения на микрофон"
        }
    }

//    DisposableEffect(Unit) {
//        val lbm = LocalBroadcastManager.getInstance(context)
//
//        lbm.registerReceiver(voiceReceiver, IntentFilter(VoiceEvents.ACTION_RECOGNIZED_TEXT))
//        onDispose { lbm.unregisterReceiver(voiceReceiver) }
//    }


    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
            isRunning = true
            status = "Слушает Джарвис..."
        } else {
            micPermissionLauncher.launch(RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            notifAccess = isNotifAccessGranted()
            listenerConnected = ru.lsn03.voicemediacontroller.service.JarvisNotificationListener.connected
            kotlinx.coroutines.delay(1000)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        Text(text = status, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(text = recognizedStatus, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)

        if (notifAccess && !listenerConnected) {
            Text(
                text = "Доступ к уведомлениям включён, но сервис не подключился.\nОткрой настройки доступа и выключи/включи переключатель для приложения.",
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center,
                color = androidx.compose.ui.graphics.Color(0xFFB00020) // красный/ошибка
            )

            Button(
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            ) {
                Text("Переподключить доступ")
            }
        }


        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                if (isRunning) {
                    context.stopService(Intent(context, VoiceService::class.java))
                    isRunning = false
                    status = "Остановлен"
                } else {
                    val granted = ContextCompat.checkSelfPermission(context, RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
                        isRunning = true
                        status = "Слушает Джарвис..."
                    } else {
                        micPermissionLauncher.launch(RECORD_AUDIO)
                    }
                }
            }
        ) { Text(if (isRunning) "Stop" else "Start") }

        Text("Громкость: Wake (весёлая)", modifier = Modifier.padding(top = 24.dp))
        Slider(
            value = happyVol,
            onValueChange = { v ->
                happyVol = v
                prefs.edit().putFloat("happy_vol", v).apply()
                sendVolumes()
            },
            valueRange = 0f..1f
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                val i = Intent(context, VoiceService::class.java).apply {
                    action = "ru.lsn03.voicemediacontroller.action.PREVIEW_WAKE"
                    putExtra("happy_vol", happyVol)
                    putExtra("sad_vol", sadVol)
                }
                ContextCompat.startForegroundService(context, i)
            }
        ) { Text("Прослушать wake") }

        Text("Громкость: Sleep (грустная)", modifier = Modifier.padding(top = 16.dp))
        Slider(
            value = sadVol,
            onValueChange = { v ->
                sadVol = v
                prefs.edit().putFloat("sad_vol", v).apply()
                sendVolumes()
            },
            valueRange = 0f..1f
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                val i = Intent(context, VoiceService::class.java).apply {
                    action = "ru.lsn03.voicemediacontroller.action.PREVIEW_SLEEP"
                    putExtra("happy_vol", happyVol)
                    putExtra("sad_vol", sadVol)
                }
                ContextCompat.startForegroundService(context, i)
            }
        ) { Text("Прослушать sleep") }
    }
}
