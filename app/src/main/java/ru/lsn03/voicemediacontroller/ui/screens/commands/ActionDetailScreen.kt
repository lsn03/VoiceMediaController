package ru.lsn03.voicemediacontroller.ui.screens.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class UiPhrase(
    val id: Long,
    val text: String,
    val enabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDetailScreen(
    actionName: String,
    navUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: заменить на ViewModel + Room
    var phrases by remember {
        mutableStateOf(
            listOf(
                UiPhrase(1, "пример фразы 1", true),
                UiPhrase(2, "пример фразы 2", true),
            )
        )
    }

    var sheetMode by remember { mutableStateOf<SheetMode?>(null) }
    val scope = rememberCoroutineScope()

    fun deletePhrase(p: UiPhrase) {
        phrases = phrases.filterNot { it.id == p.id }
        // TODO: repo.deletePhrase(p.id)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(actionName) },
                navigationIcon = {
                    IconButton(onClick = navUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Сбросить к дефолту") },
                            onClick = {
                                menu = false
                                // TODO: repo.resetToDefaults(actionName)
                                phrases = emptyList()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { sheetMode = SheetMode.Add }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = phrases,
                key = { it.id }
            ) { p ->
                // Swipe-to-delete
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            deletePhrase(p)
                            true
                        } else {
                            false
                        }
                    },
                    positionalThreshold = { it * 0.35f }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        val bg = MaterialTheme.colorScheme.errorContainer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bg)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    },
                    content = {
                        ListItem(
                            headlineContent = { Text(p.text, maxLines = 1) },
                            leadingContent = {
                                Switch(
                                    checked = p.enabled,
                                    onCheckedChange = { checked ->
                                        phrases = phrases.map {
                                            if (it.id == p.id) it.copy(enabled = checked) else it
                                        }
                                        // TODO: repo.setEnabled(p.id, checked)
                                    }
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { sheetMode = SheetMode.Edit(p) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                                }
                            },
                            modifier = Modifier.clickable { sheetMode = SheetMode.Edit(p) }
                        )
                    }
                )

                Divider()
            }
        }
    }

    // Bottom sheet (add/edit)
    val current = sheetMode
    if (current != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { sheetMode = null },
            sheetState = sheetState
        ) {
            PhraseEditSheet(
                initialText = when (current) {
                    SheetMode.Add -> ""
                    is SheetMode.Edit -> current.phrase.text
                },
                onCancel = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { sheetMode = null }
                },
                onSave = { newText ->
                    when (current) {
                        SheetMode.Add -> {
                            val newId = (phrases.maxOfOrNull { it.id } ?: 0L) + 1
                            phrases = phrases + UiPhrase(newId, newText, true)
                            // TODO: repo.addPhrase(actionName, newText)
                        }
                        is SheetMode.Edit -> {
                            phrases = phrases.map {
                                if (it.id == current.phrase.id) it.copy(text = newText) else it
                            }
                            // TODO: repo.updatePhraseText(current.phrase.id, newText)
                        }
                    }
                    scope.launch { sheetState.hide() }.invokeOnCompletion { sheetMode = null }
                }
            )
        }
    }
}


private sealed class SheetMode {
    data object Add : SheetMode()
    data class Edit(val phrase: UiPhrase) : SheetMode()
}

@Composable
private fun PhraseEditSheet(
    initialText: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Фраза", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("Например: следующий трек") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onCancel
            ) { Text("Отмена") }

            Button(
                modifier = Modifier.weight(1f),
                onClick = { onSave(text.trim()) },
                enabled = text.trim().isNotEmpty()
            ) { Text("Сохранить") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SwipeDeleteItem(
    item: T,
    onDelete: (T) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.35f } // чтобы не удалялось от лёгкого свайпа
    )

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // фон при свайпе
            val color = MaterialTheme.colorScheme.errorContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = { content() }
    )
}
