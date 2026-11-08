package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settings: SecurityHelper,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.entries.size, state.isThinking) {
        val count = state.entries.size + if (state.isThinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        ChatTopBar(
            state = state,
            settings = settings,
            onSelectProvider = viewModel::selectProvider,
            onClear = { confirmClear = true },
            onOpenSettings = onOpenSettings,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.entries.isEmpty() && !state.isThinking) {
                EmptyChat(onSuggestion = { input = it })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry -> ChatEntryView(entry) }
                    if (state.isThinking) item(key = "thinking") { ThinkingIndicator() }
                }
            }
        }

        ChatInput(
            value = input,
            onValueChange = { input = it },
            busy = state.isBusy,
            enabled = state.provider != null,
            onSend = {
                viewModel.send(input)
                input = ""
            },
            onStop = viewModel::stop,
        )
    }

    state.pendingApproval?.let { pending ->
        HumanInTheLoopDialog(
            command = pending.command,
            risk = pending.risk,
            onConfirm = viewModel::approvePending,
            onDismiss = viewModel::denyPending,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(trans("clear_chat")) },
            text = { Text(trans("clear_chat_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text(trans("clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(trans("cancel")) } },
        )
    }
}

@Composable
private fun ChatTopBar(
    state: ChatUiState,
    settings: SecurityHelper,
    onSelectProvider: (LlmProviderType) -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aimovix", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Box {
                    Row(
                        modifier = Modifier.clickable { menuOpen = true }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val label = state.provider?.let { "${ModelCatalog.displayName(it)} · ${state.model}" } ?: trans("no_model")
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = trans("switch_model"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val configured = settings.configuredProviders()
                        configured.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text("${ModelCatalog.displayName(provider)} · ${settings.getSelectedModel(provider)}") },
                                onClick = {
                                    onSelectProvider(provider)
                                    menuOpen = false
                                },
                            )
                        }
                        DropdownMenuItem(text = { Text(trans("manage_models")) }, onClick = {
                            menuOpen = false
                            onOpenSettings()
                        })
                    }
                }
            }
            SecurityLevelBadge(settings.getSecurityLevel())
            IconButton(onClick = onClear, enabled = state.entries.isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = trans("clear_chat"))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = trans("settings"))
            }
        }
    }
}

@Composable
private fun SecurityLevelBadge(level: Int) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (level) {
        0 -> colors.error to colors.onError
        1 -> colors.errorContainer to colors.onErrorContainer
        3 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Surface(shape = CircleShape, color = bg) {
        Text(if (level == 0) "L0 AUTO" else "L$level", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        Spacer(Modifier.size(16.dp))
        Text(
            trans("agent_greeting"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("suggest_1", "suggest_2", "suggest_3", "suggest_4").forEach { key ->
                val text = trans(key)
                AssistChip(onClick = { onSuggestion(text) }, label = { Text(text) })
            }
        }
    }
}

@Composable
private fun ChatEntryView(entry: ChatEntry) {
    val colors = MaterialTheme.colorScheme
    when (entry.kind) {
        EntryKind.USER -> Bubble(entry.text, alignEnd = true, container = colors.primary, content = colors.onPrimary)
        EntryKind.ASSISTANT -> Bubble(entry.text, alignEnd = false, container = colors.surfaceVariant, content = colors.onSurfaceVariant)
        EntryKind.ERROR -> Bubble(entry.text, alignEnd = false, container = colors.errorContainer, content = colors.onErrorContainer)
        EntryKind.INFO -> Text(
            entry.text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = colors.secondary,
        )
        EntryKind.COMMAND -> CommandCard(entry)
    }
}

@Composable
private fun Bubble(text: String, alignEnd: Boolean, container: Color, content: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (alignEnd) 18.dp else 4.dp, bottomEnd = if (alignEnd) 4.dp else 18.dp,
            ),
            color = container,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(text = text, color = content, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun CommandCard(entry: ChatEntry) {
    val colors = MaterialTheme.colorScheme
    val (label, positive) = when (entry.status) {
        CommandStatus.PENDING_APPROVAL -> trans("status_pending") to true
        CommandStatus.RUNNING -> trans("status_running") to true
        CommandStatus.DONE -> trans("status_done") to true
        CommandStatus.FAILED -> trans("status_failed") to false
        CommandStatus.BLOCKED, null -> trans("status_blocked") to false
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val toolLabel = when (entry.tool) {
                    AgentTool.FILE -> trans("tool_file")
                    AgentTool.WEB -> trans("tool_web")
                    AgentTool.TERMUX, null -> "Termux"
                }
                Text(toolLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.secondary, modifier = Modifier.weight(1f))
                if (entry.status == CommandStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                }
                StatusPill(label, positive)
            }
            Spacer(Modifier.size(8.dp))
            Surface(color = com.example.ui.theme.Slate900, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        if (entry.tool == null || entry.tool == AgentTool.TERMUX) "$ " + entry.command.orEmpty() else entry.command.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = com.example.ui.theme.Green100,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            if (entry.text.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                SelectionContainer {
                    Text(
                        entry.text,
                        fontFamily = if (entry.status == CommandStatus.DONE || entry.status == CommandStatus.FAILED) FontFamily.Monospace else FontFamily.Default,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(4.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(trans("thinking"), fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.navigationBarsPadding()) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(if (enabled) trans("type_message") else trans("no_model")) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!busy && value.isNotBlank()) onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Spacer(Modifier.size(8.dp))
            if (busy) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Icon(Icons.Default.Close, contentDescription = trans("stop")) }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = trans("send")) }
            }
        }
        // Transparency notice (AI interaction + hallucinations), always visible below the input.
        Text(
            trans("ai_disclaimer_short"),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
        }
    }
}
