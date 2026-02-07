package ru.lsn03.voicemediacontroller.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.lsn03.voicemediacontroller.service.VoiceService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current

    val wakeDraft by viewModel.wakeDraft.collectAsState()
    val happyVol by viewModel.happyVol.collectAsState()
    val sadVol by viewModel.sadVol.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text("Wake-word", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = wakeDraft,
                onValueChange = viewModel::onWakeDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { st ->
                        if (!st.isFocused) viewModel.onWakeEditDone()
                    },
                singleLine = true,
                label = { Text("Например: джарвис") }
            )


            Button(
                onClick = viewModel::saveWakeWord,
                enabled = wakeDraft.trim().isNotEmpty()
            ) {
                Text("Сохранить wake-word")
            }

            Divider()

            Text("Громкость: Wake (весёлая)", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = happyVol,
                onValueChange = viewModel::setHappyVol,
                valueRange = 0f..1f
            )
            Button(onClick = {
                // громкость уже в prefs, сервис проиграет с актуальной
                ctx.startService(Intent(ctx, VoiceService::class.java).apply {
                    action = VoiceService.ACTION_PREVIEW_WAKE
                })
            }) { Text("Прослушать wake") }

            Spacer(Modifier.height(8.dp))

            Text("Громкость: Sleep (грустная)", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = sadVol,
                onValueChange = viewModel::setSadVol,
                valueRange = 0f..1f
            )
            Button(onClick = {
                ctx.startService(Intent(ctx, VoiceService::class.java).apply {
                    action = VoiceService.ACTION_PREVIEW_SLEEP
                })
            }) { Text("Прослушать sleep") }
        }
    }
}
