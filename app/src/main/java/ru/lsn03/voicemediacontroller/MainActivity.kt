package ru.lsn03.voicemediacontroller

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.Observer
import ru.lsn03.voicemediacontroller.service.VoiceService
import ru.lsn03.voicemediacontroller.ui.theme.VoiceMediaControlTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

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

        createNotificationChannel(applicationContext)  // 👇
        requestIgnoreBatteryOptimizations(this)

        enableEdgeToEdge()
        setContent {
            VoiceMediaControlTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceControlScreen(modifier = Modifier.padding(innerPadding))
                }
            }
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


@Composable
fun VoiceControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Остановлен") }
    var recognizedStatus by remember { mutableStateOf("Нет текста") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
            isRunning = true
            status = "Слушает Джарвис..."
        } else {
            isRunning = false
            status = "Нет разрешения на микрофон"
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = Observer<String> { text -> recognizedStatus = text }
        VoiceService.recognizedText.observe(lifecycleOwner, observer)
        onDispose { VoiceService.recognizedText.removeObserver(observer) }
    }

    // Авто-старт при запуске приложения (один раз при заходе на экран)
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

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = status, style = MaterialTheme.typography.headlineMedium)
        Text(text = recognizedStatus, modifier = Modifier.padding(top = 8.dp))

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
            },
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}
