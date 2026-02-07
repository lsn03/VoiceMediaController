package ru.lsn03.voicemediacontroller.ui.screens.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    modifier: Modifier = Modifier,
    viewModel: ActionDetailViewModel = hiltViewModel()
) {
    // phrases из БД
    val phrases by viewModel.phrases.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // события ошибок
    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var sheetMode by remember { mutableStateOf<SheetMode?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                viewModel.resetToDefaults()
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
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = phrases,
                key = { it.id }
            ) { p ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { false }, // не подтверждаем визуальное удаление
                    positionalThreshold = { it * 0.35f }
                )

                LaunchedEffect(dismissState.targetValue) {
                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                        viewModel.deletePhrase(p.id)
                        dismissState.reset()
                    }
                }

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
                                        viewModel.toggleEnabled(p.id, checked)
                                    }
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    sheetMode = SheetMode.Edit(UiPhrase(p.id, p.text, p.enabled))
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                                }
                            },
                            modifier = Modifier.clickable {
                                sheetMode = SheetMode.Edit(UiPhrase(p.id, p.text, p.enabled))
                            }
                        )
                    }
                )
                Divider()
            }
        }
    }

    // bottom sheet
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
                        SheetMode.Add -> viewModel.addPhrase(newText)
                        is SheetMode.Edit -> viewModel.editPhrase(current.phrase.id, newText)
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
