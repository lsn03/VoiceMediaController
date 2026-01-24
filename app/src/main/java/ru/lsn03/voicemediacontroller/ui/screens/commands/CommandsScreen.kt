package ru.lsn03.voicemediacontroller.ui.screens.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.lsn03.voicemediacontroller.action.VoiceAction

@Composable
fun CommandsScreen(
    onOpenAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = VoiceAction.entries.filter { it != VoiceAction.UNKNOWN }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(actions.size) { idx ->
            val action = actions[idx]
            ListItem(
                headlineContent = { Text(action.name) },
                supportingContent = { Text(action.description, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.clickable { onOpenAction(action.name) }
            )
            Divider()
        }
    }
}
