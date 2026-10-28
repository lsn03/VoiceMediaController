package ru.lsn03.voicemediacontroller.ui.screens.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.lsn03.voicemediacontroller.core.speech.DownloadProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.onMessageShown()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items.size) { idx ->
                LocaleRow(
                    item = state.items[idx],
                    onDownload = { viewModel.onDownload(state.items[idx].locale) },
                    onDelete = { viewModel.onDelete(state.items[idx].locale) },
                    onSelect = { viewModel.onSelect(state.items[idx].locale) },
                )
                Divider()
            }
        }
    }
}

@Composable
private fun LocaleRow(
    item: LocaleUi,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = item.displayName + if (item.selected) " (in use)" else "",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(statusLabel(item), style = MaterialTheme.typography.bodySmall)

        val progress = item.progress
        if (progress is DownloadProgress.Downloading) {
            val total = progress.total
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (progress.bytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.downloadable && item.status != LocaleUi.Status.Downloading) {
                Button(onClick = onDownload) {
                    Text(if (item.status == LocaleUi.Status.Downloaded) "Re-download" else "Download")
                }
            }
            if (item.status == LocaleUi.Status.Downloaded) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
            if (item.status != LocaleUi.Status.NotInstalled &&
                item.status != LocaleUi.Status.Downloading &&
                !item.selected
            ) {
                OutlinedButton(onClick = onSelect) { Text("Use") }
            }
        }
    }
}

private fun statusLabel(item: LocaleUi): String {
    val progress = item.progress
    return when (item.status) {
        LocaleUi.Status.NotInstalled -> "Not installed"
        LocaleUi.Status.InstalledFromAsset -> "Installed from asset"
        LocaleUi.Status.Downloaded -> "Downloaded"
        LocaleUi.Status.Downloading -> when (progress) {
            is DownloadProgress.Downloading -> {
                val total = progress.total
                if (total != null && total > 0) {
                    val pct = (progress.bytes * 100 / total).toInt()
                    "Downloading $pct%"
                } else {
                    "Downloading ${progress.bytes} bytes"
                }
            }
            is DownloadProgress.Verifying -> "Verifying"
            else -> "Downloading"
        }
        LocaleUi.Status.Error -> when (progress) {
            is DownloadProgress.Failed -> "Error: ${progress.reason}"
            else -> "Error"
        }
    }
}
