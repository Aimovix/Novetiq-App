package com.example

import android.app.ActivityManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// --- Generic building blocks --------------------------------------------------------------

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
fun StatusPill(text: String, positive: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (positive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (positive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun CodeBox(text: String, modifier: Modifier = Modifier, copyable: Boolean = false) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMsg = trans("copied")
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SelectionContainer {
                Text(text = text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            if (copyable) {
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Aimovix", text)))
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(trans("copy")) }
            }
        }
    }
}

@Composable
fun DefenseBanner() {
    Surface(shape = MaterialTheme.shapes.large, color = com.example.ui.theme.Slate900) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = com.example.ui.theme.Indigo200, modifier = Modifier.size(18.dp))
            Column {
                Text(trans("defense_active"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Indigo200)
                Text(trans("defense_desc"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

// --- Human in the loop --------------------------------------------------------------------

@Composable
fun HumanInTheLoopDialog(
    command: String,
    risk: CommandRisk,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    fromAgent: Boolean = true,
) {
    var showSecondConfirmation by remember(command) { mutableStateOf(false) }

    if (showSecondConfirmation) {
        AlertDialog(
            onDismissRequest = { showSecondConfirmation = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(trans("critical_warning")) },
            text = { Text(trans("destructive_warning_desc")) },
            confirmButton = {
                Button(
                    onClick = {
                        showSecondConfirmation = false
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(trans("yes_execute")) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(trans("cancel")) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(trans("security_verification")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (fromAgent) trans("ai_wants_execute") else trans("setup_wants_execute"))
                CodeBox(command)
                Text(
                    text = trans("risk_label") + ": " + trans(riskKey(risk)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (risk == CommandRisk.DESTRUCTIVE || risk == CommandRisk.EXTERNAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (risk == CommandRisk.DESTRUCTIVE) showSecondConfirmation = true else onConfirm() },
                colors = if (risk == CommandRisk.DESTRUCTIVE || risk == CommandRisk.EXTERNAL) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors(),
            ) { Text(trans("execute")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(trans("block")) } },
    )
}

fun riskKey(risk: CommandRisk) = when (risk) {
    CommandRisk.READ_ONLY -> "risk_read"
    CommandRisk.WRITE -> "risk_write"
    CommandRisk.DESTRUCTIVE -> "risk_destructive"
    CommandRisk.EXTERNAL -> "risk_external"
}

/** Runs trusted setup commands, asking for confirmation unless the security level is 0 or 1. */
class SetupCommandRunner(private val context: Context, private val settings: SecurityHelper) {
    var pending by mutableStateOf<TermuxCommand?>(null)
        private set

    fun request(command: TermuxCommand) {
        if (settings.getSecurityLevel() <= 1) launch(command) else pending = command
    }

    fun confirm() {
        pending?.let(::launch)
        pending = null
    }

    fun dismiss() {
        pending = null
    }

    private fun launch(command: TermuxCommand) {
        val lang = settings.getLanguage()
        val message = when (val r = TermuxBridge.execute(context, command)) {
            TermuxLaunch.Sent -> Strings.get("cmd_sent", lang)
            TermuxLaunch.NotInstalled -> Strings.get("termux_required", lang)
            TermuxLaunch.PermissionMissing -> Strings.get("err_termux_permission", lang)
            is TermuxLaunch.Failed -> Strings.get("cmd_failed", lang) + r.message
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun rememberSetupCommandRunner(settings: SecurityHelper): SetupCommandRunner {
    val context = LocalContext.current
    val runner = remember(settings) { SetupCommandRunner(context, settings) }
    runner.pending?.let { cmd ->
        HumanInTheLoopDialog(
            command = cmd.displayText,
            risk = cmd.risk,
            onConfirm = runner::confirm,
            onDismiss = runner::dismiss,
            fromAgent = false,
        )
    }
    return runner
}

// --- Security level -----------------------------------------------------------------------

@Composable
fun SecurityLevelSelector(settings: SecurityHelper, onChanged: (Int) -> Unit = {}) {
    var selectedLevel by remember { mutableStateOf(settings.getSecurityLevel()) }
    var askFullAutonomyConsent by remember { mutableStateOf(false) }
    val select = { level: Int ->
        settings.setSecurityLevel(level)
        selectedLevel = settings.getSecurityLevel()
        settings.appendAudit("SETTINGS", "security level -> $selectedLevel")
        onChanged(selectedLevel)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(3, "lvl3_name", "lvl3_desc"),
            Triple(2, "lvl2_name", "lvl2_desc"),
            Triple(1, "lvl1_name", "lvl1_desc"),
            Triple(0, "lvl0_name", "lvl0_desc"),
        ).forEach { (level, name, desc) ->
            SecurityLevelItem(
                title = trans(name),
                description = trans(desc),
                isSelected = selectedLevel == level,
                danger = level == 0,
                onClick = {
                    if (level == 0 && !settings.hasFullAutonomyConsent()) askFullAutonomyConsent = true else select(level)
                },
            )
        }
    }
    if (askFullAutonomyConsent) {
        FullAutonomyConsentDialog(
            onAccept = {
                settings.recordFullAutonomyConsent()
                askFullAutonomyConsent = false
                select(0)
            },
            onDismiss = { askFullAutonomyConsent = false },
        )
    }
}

/**
 * Separate, informed opt-in for full autonomy: the user has to tick the risk statement and type a
 * confirmation word. The consent is stored with a timestamp (see [SecurityHelper.recordFullAutonomyConsent]).
 */
@Composable
fun FullAutonomyConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val word = trans("consent_word")
    val valid = understood && typed.trim().equals(word, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(trans("full_auto_title")) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(trans("full_auto_desc"), style = MaterialTheme.typography.bodyMedium)
                CheckRow(checked = understood, onCheckedChange = { understood = it }, text = trans("full_auto_check"))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(trans("full_auto_type").format(word)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(trans("full_auto_enable")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(trans("cancel")) } },
    )
}

@Composable
fun CheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Lets the user cap how many actions the agent may chain for one message. */
@Composable
fun MaxStepsSelector(settings: SecurityHelper) {
    var steps by remember { mutableStateOf(settings.getMaxSteps()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(trans("max_steps_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 20, 50, 100).forEach { option ->
                FilterChip(
                    selected = steps == option,
                    onClick = {
                        settings.setMaxSteps(option)
                        steps = option
                    },
                    label = { Text(option.toString()) },
                )
            }
        }
    }
}

@Composable
fun SecurityLevelItem(title: String, description: String, isSelected: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val accent = if (danger) colors.error else colors.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) colors.primaryContainer else colors.surface,
        border = BorderStroke(if (danger) 2.dp else 1.dp, if (isSelected || danger) accent else colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isSelected) colors.onPrimaryContainer else colors.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 13.sp, color = if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant)
            }
        }
    }
}

// --- Cloud providers ----------------------------------------------------------------------

@Composable
fun CloudApiSection(settings: SecurityHelper, onChanged: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LlmProviderType.CLOUD.forEach { provider ->
            CloudProviderCard(provider = provider, settings = settings, onChanged = onChanged)
        }
        Text(
            text = trans("keys_encrypted"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CloudProviderCard(provider: LlmProviderType, settings: SecurityHelper, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLanguage.current
    var apiKey by rememberSaveable(provider) { mutableStateOf(settings.getApiKey(provider).orEmpty()) }
    var model by rememberSaveable(provider) { mutableStateOf(settings.getSelectedModel(provider)) }
    var savedKey by remember(provider) { mutableStateOf(settings.getApiKey(provider) != null) }
    var showKey by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ModelCatalog.displayName(provider), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (savedKey) StatusPill(trans("configured"), positive = true)
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim() },
            label = { Text(trans("api_key")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) trans("hide") else trans("show")) }
            },
            shape = MaterialTheme.shapes.medium,
        )

        ModelPicker(
            value = model,
            onValueChange = { model = it },
            options = (fetchedModels.ifEmpty { ModelCatalog.suggestions(provider) }),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = apiKey.isNotBlank() && model.isNotBlank(),
                onClick = {
                    settings.saveApiKey(provider, apiKey)
                    settings.setSelectedModel(provider, model)
                    if (settings.getActiveProvider() == null || settings.getActiveProvider() == provider) {
                        settings.setActiveProvider(provider)
                    }
                    savedKey = true
                    onChanged()
                    Toast.makeText(context, "${ModelCatalog.displayName(provider)}: ${Strings.get("api_key_saved", lang)}", Toast.LENGTH_SHORT).show()
                },
            ) { Text(trans("save_key")) }

            OutlinedButton(
                enabled = apiKey.isNotBlank() && !loadingModels,
                onClick = {
                    loadingModels = true
                    scope.launch {
                        val message = try {
                            val models = when (provider) {
                                LlmProviderType.GEMINI -> GeminiClient(apiKey, model)
                                LlmProviderType.OPENAI -> OpenAiClient(apiKey, model)
                                else -> ClaudeClient(apiKey, model)
                            }.listModels()
                            fetchedModels = models.sorted()
                            Strings.get("key_valid", lang).format(models.size)
                        } catch (e: LlmException) {
                            if (e.kind == LlmException.Kind.AUTH) Strings.get("err_auth", lang)
                            else Strings.get("err_network", lang) + " (${e.message})"
                        }
                        loadingModels = false
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                if (loadingModels) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(trans("test_key"))
            }

            if (savedKey) {
                TextButton(onClick = {
                    settings.removeApiKey(provider)
                    apiKey = ""
                    savedKey = false
                    fetchedModels = emptyList()
                    onChanged()
                }) { Text(trans("remove"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Free-text model id with a dropdown of suggestions. */
@Composable
fun ModelPicker(value: String, onValueChange: (String) -> Unit, options: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.trim()) },
            label = { Text(trans("model_selection")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = trans("model_selection"))
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onValueChange(option)
                    expanded = false
                })
            }
        }
    }
}

// --- Local models (Ollama in Termux) ------------------------------------------------------

fun deviceRamGb(context: Context): Double {
    val am = context.getSystemService(ActivityManager::class.java) ?: return 0.0
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.totalMem / (1024.0 * 1024.0 * 1024.0)
}

@Composable
fun LocalModelSection(settings: SecurityHelper, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLanguage.current
    val runner = rememberSetupCommandRunner(settings)
    val ramGb = remember { deviceRamGb(context) }
    var selectedTag by remember { mutableStateOf(settings.getSelectedModel(LlmProviderType.LOCAL)) }
    var configured by remember { mutableStateOf(settings.isLocalModelConfigured()) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    text = trans("ram_safety") +
                        if (ramGb > 0.1) "\n" + Strings.get("device_ram", lang).format(ramGb) else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    lineHeight = 17.sp,
                )
            }
        }

        LocalModels.AVAILABLE_MODELS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { model ->
                    ModelCard(
                        model = model,
                        selected = model.ollamaTag == selectedTag,
                        tooBig = ramGb > 0 && model.ramRequirementGb > ramGb + 0.5,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedTag = model.ollamaTag
                            settings.setSelectedModel(LlmProviderType.LOCAL, model.ollamaTag)
                            if (configured) {
                                configured = false
                                settings.setLocalModelConfigured(false)
                                onChanged()
                            }
                        },
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trans("local_setup_title"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (configured) StatusPill(trans("ready"), positive = true)
            }
            Text(trans("local_setup_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SetupStepButton("1", trans("install_ollama")) { runner.request(SetupCommands.INSTALL_OLLAMA) }
            SetupStepButton("2", trans("start_ollama")) { runner.request(SetupCommands.START_OLLAMA) }
            SetupStepButton("3", Strings.get("pull_model", lang).format(selectedTag)) { runner.request(SetupCommands.pullModel(selectedTag)) }
            Button(
                enabled = !testing,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    testing = true
                    scope.launch {
                        val message = try {
                            val models = OllamaClient(selectedTag).listModels()
                            val found = models.any { it == selectedTag || it == "$selectedTag:latest" || it.startsWith("$selectedTag-") }
                            if (found) {
                                settings.setLocalModelConfigured(true)
                                if (settings.getActiveProvider() == null) settings.setActiveProvider(LlmProviderType.LOCAL)
                                configured = true
                                onChanged()
                                Strings.get("local_ready", lang)
                            } else {
                                Strings.get("local_model_missing", lang).format(selectedTag)
                            }
                        } catch (e: LlmException) {
                            Strings.get("err_ollama_offline", lang)
                        }
                        testing = false
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                if (testing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("4 · " + trans("test_connection"))
            }
        }
    }
}

@Composable
private fun SetupStepButton(number: String, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$number · $label", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ModelCard(model: LocalModelInfo, selected: Boolean, tooBig: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isHighEnd = model.badgeKey == "high_end"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = modifier
            .heightIn(min = 110.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isHighEnd) colors.error else colors.onSurface)
            Text(trans(model.badgeKey), fontSize = 11.sp, color = if (isHighEnd) colors.error else colors.secondary)
            Surface(shape = CircleShape, color = if (tooBig) colors.errorContainer else colors.surfaceVariant) {
                Text(
                    text = Strings.get("min_ram", LocalLanguage.current).format(model.ramRequirementGb),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tooBig) colors.onErrorContainer else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
            if (tooBig) Text(trans("too_big"), fontSize = 10.sp, color = colors.error)
        }
    }
}

// --- Termux connection --------------------------------------------------------------------

/**
 * Permission request, `allow-external-apps` instructions and a live connection test. This is
 * what makes command execution actually work on a real device.
 */
@Composable
fun TermuxConnectionSection(settings: SecurityHelper) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLanguage.current
    val runner = rememberSetupCommandRunner(settings)
    var hasPermission by remember { mutableStateOf(TermuxBridge.hasRunCommandPermission(context)) }
    var testState by remember { mutableStateOf<Boolean?>(null) }
    var testMessage by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = TermuxBridge.hasRunCommandPermission(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, Strings.get("permission_denied", lang), Toast.LENGTH_LONG).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1 · " + trans("perm_title"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(if (hasPermission) trans("granted") else trans("missing"), positive = hasPermission)
            }
            Text(trans("perm_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!hasPermission) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { permissionLauncher.launch(TermuxConstants.PERMISSION_RUN_COMMAND) }) { Text(trans("grant")) }
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(trans("app_settings")) }
                }
            }
        }

        SectionCard {
            Text("2 · " + trans("external_title"), fontWeight = FontWeight.Bold)
            Text(trans("external_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CodeBox(TermuxConstants.ALLOW_EXTERNAL_APPS_COMMAND, copyable = true)
            OutlinedButton(onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(TermuxConstants.PACKAGE)
                if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(trans("open_termux")) }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("3 · " + trans("test_connection"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                testState?.let { StatusPill(if (it) trans("connected") else trans("failed"), positive = it) }
            }
            if (testMessage.isNotBlank()) {
                Text(testMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        val (ok, message) = runConnectionTest(context, lang)
                        testState = ok
                        testMessage = message
                        testing = false
                        hasPermission = TermuxBridge.hasRunCommandPermission(context)
                    }
                },
            ) {
                if (testing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(trans("run_test"))
            }
        }

        SectionCard {
            Text("4 · " + trans("update_title"), fontWeight = FontWeight.Bold)
            Text(trans("termux_setup_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { runner.request(SetupCommands.UPDATE_PACKAGES) }) { Text(trans("update_packages")) }
                OutlinedButton(onClick = { runner.request(SetupCommands.INSTALL_PYTHON) }) { Text(trans("install_python")) }
            }
        }
    }
}

private suspend fun runConnectionTest(context: Context, lang: String): Pair<Boolean, String> {
    val executionId = TermuxResultHub.newExecutionId()
    val command = TermuxCommand("${TermuxConstants.BIN}/echo", listOf("aimovix-ok"), background = true, label = "Aimovix test", risk = CommandRisk.READ_ONLY)
    return coroutineScope {
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(20_000) { TermuxResultHub.results.first { it.executionId == executionId } }
        }
        when (val launch = TermuxBridge.execute(context, command, executionId)) {
            TermuxLaunch.Sent -> Unit
            else -> {
                awaiting.cancel()
                val msg = when (launch) {
                    TermuxLaunch.NotInstalled -> Strings.get("termux_required", lang)
                    TermuxLaunch.PermissionMissing -> Strings.get("err_termux_permission", lang)
                    is TermuxLaunch.Failed -> Strings.get("cmd_failed", lang) + launch.message
                    TermuxLaunch.Sent -> ""
                }
                return@coroutineScope false to msg
            }
        }
        val result = awaiting.await()
        when {
            result == null -> false to Strings.get("err_termux_timeout", lang)
            !result.started -> false to Strings.get("err_termux_start", lang) + (result.errMessage ?: "")
            result.stdout.contains("aimovix-ok") -> true to Strings.get("test_ok", lang)
            else -> false to (result.stderr.ifBlank { Strings.get("err_termux_start", lang) })
        }
    }
}

@Composable
fun DividerSpacer() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SuccessIcon(modifier: Modifier = Modifier) {
    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = modifier)
}
