package ru.lsn03.voicemediacontroller.ui.screens.home


import android.Manifest.permission.RECORD_AUDIO
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.lsn03.voicemediacontroller.service.JarvisNotificationListener
import ru.lsn03.voicemediacontroller.service.VoiceService
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

@Composable
fun HomeScreen(
    recognizedStatus: String,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val wake by viewModel.wakeWord.collectAsState()

    val titleWake = wake.replaceFirstChar { it.uppercaseChar() }

    var isRunning by remember { mutableStateOf(false) }
    var statusText =
        if (!isRunning) "Остановлен"
        else if (wake.isBlank()) "Слушает…"   // или "Загрузка…"
        else "Слушает ${wake.replaceFirstChar { it.uppercaseChar() }}..."

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
            isRunning = true
            statusText = statusString(titleWake)
        } else {
            isRunning = false
            statusText = "Нет разрешения на микрофон"
        }
    }


    // Автозапуск при входе (как у тебя было раньше)
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
            isRunning = true
            statusText = statusString(titleWake)
        } else {
            micPermissionLauncher.launch(RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = recognizedStatus,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        NotificationAccessBanner()

        Button(
            onClick = {
                if (isRunning) {
                    context.stopService(Intent(context, VoiceService::class.java))
                    isRunning = false
                    statusText = "Остановлен"
                } else {
                    val granted = ContextCompat.checkSelfPermission(context, RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
                        isRunning = true
                        statusText = statusString(titleWake)
                    } else {
                        micPermissionLauncher.launch(RECORD_AUDIO)
                    }
                }
            }
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}

@Composable
private fun NotificationAccessBanner() {
    val context = LocalContext.current
    val listenerConnected by JarvisNotificationListener.connectedFlow.collectAsState()

    var notifAccess by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun updateAccess() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val cn = ComponentName(context, JarvisNotificationListener::class.java)
        notifAccess = nm.isNotificationListenerAccessGranted(cn)
    }

    DisposableEffect(lifecycleOwner, context) {
        // ВАЖНО: обновляем сразу, иначе notifAccess останется false
        updateAccess()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                updateAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (notifAccess && !listenerConnected) {
        Text(
            text = "Доступ к уведомлениям включён, но сервис не подключился.\n" +
                    "Открой настройки доступа и выключи/включи переключатель для приложения.",
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
            color = Color(0xFFB00020)
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        ) { Text("Переподключить доступ") }
    }
}



private fun statusString(titleWake: String): String {
   Log.d(APPLICATION_NAME, "HomeScreen::statusString titleWake=$titleWake")
    return "Слушает $titleWake..."
}
