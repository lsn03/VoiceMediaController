package ru.lsn03.voicemediacontroller.ui.screens.home


import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.lsn03.voicemediacontroller.service.VoiceService

@Composable
fun HomeScreen(
    recognizedStatus: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Запуск...") }

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

    // Автозапуск при входе (как у тебя было раньше)
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
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = recognizedStatus,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Button(
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
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}
