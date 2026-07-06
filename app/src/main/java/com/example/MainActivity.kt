package com.example

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var securityHelper: SecurityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        securityHelper = SecurityHelper(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SynaptiqApp(
                        modifier = Modifier.padding(innerPadding),
                        securityHelper = securityHelper,
                        onExecuteCommand = { command ->
                            executeInTermux(command)
                        }
                    )
                }
            }
        }
    }

    private fun executeInTermux(command: String) {
        val lang = securityHelper.getLanguage()
        val level = securityHelper.getSecurityLevel()
        
        if (level == 3 && command != "pkg upgrade && pkg update -y") {
            Toast.makeText(this, Strings.get("sandbox_blocked", lang), Toast.LENGTH_LONG).show()
            return
        }

        if (!CommandValidator.isValid(command)) {
            Toast.makeText(this, Strings.get("security_block", lang), Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent()
        intent.setClassName("com.termux", "com.termux.app.RunCommandService")
        intent.action = "com.termux.RUN_COMMAND"
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        
        try {
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, Strings.get("cmd_sent", lang), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "${Strings.get("cmd_failed", lang)} ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun SynaptiqApp(
    modifier: Modifier = Modifier,
    securityHelper: SecurityHelper,
    onExecuteCommand: (String) -> Unit
) {
    val context = LocalContext.current
    var isTermuxInstalled by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(2) }
    var showSettings by remember { mutableStateOf(false) }
    var appLanguage by remember { mutableStateOf(securityHelper.getLanguage()) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        isTermuxInstalled = try {
            pm.getPackageInfo("com.termux", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    AppTranslationProvider(language = appLanguage) {
        if (showSettings) {
            SettingsScreen(
                modifier = modifier,
                currentLanguage = appLanguage,
                securityHelper = securityHelper,
                onLanguageSelected = { newLang ->
                    appLanguage = newLang
                    securityHelper.setLanguage(newLang)
                },
                onBack = { showSettings = false }
            )
        } else {
            Box(modifier = modifier.fillMaxSize()) {
                if (!isTermuxInstalled) {
                    MissingTermuxScreen(Modifier)
                } else {
                    if (currentStep == 2) {
                        SetupWizard(
                            modifier = Modifier,
                            securityHelper = securityHelper,
                            onExecuteCommand = onExecuteCommand,
                            onContinue = { currentStep = 3 }
                        )
                    } else if (currentStep == 3) {
                        IdentitySetupScreen(
                            modifier = Modifier,
                            securityHelper = securityHelper,
                            onExecuteCommand = onExecuteCommand,
                            onComplete = { currentStep = 4 }
                        )
                    } else {
                        ChatScreen(modifier = Modifier, securityHelper = securityHelper)
                    }
                }

                androidx.compose.material3.IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚙",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    currentLanguage: String,
    securityHelper: SecurityHelper,
    onLanguageSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = trans("settings"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = trans("language"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(
                selected = currentLanguage == "English",
                onClick = { onLanguageSelected("English") }
            )
            Text(text = "English", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onBackground)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(
                selected = currentLanguage == "Deutsch",
                onClick = { onLanguageSelected("Deutsch") }
            )
            Text(text = "Deutsch", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onBackground)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SecurityLevelTab(securityHelper)
    }
}

@Composable
fun IdentitySetupScreen(modifier: Modifier = Modifier, securityHelper: SecurityHelper, onExecuteCommand: (String) -> Unit, onComplete: () -> Unit) {
    var showCommandDialog by remember { mutableStateOf(false) }
    var isCommandExecuted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Outlined.CheckCircle,
            contentDescription = "Success",
            tint = com.example.ui.theme.Green500,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = trans("step_3"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isCommandExecuted) trans("termux_setup_started") else trans("termux_setup_desc"),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (!isCommandExecuted) {
            Button(
                onClick = { 
                    val level = securityHelper.getSecurityLevel()
                    if (level == 1) {
                        isCommandExecuted = true
                        onExecuteCommand("pkg upgrade && pkg update -y")
                    } else {
                        showCommandDialog = true 
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(trans("start_setup"), fontWeight = FontWeight.Bold, fontSize = 16.dp.value.sp)
            }
        } else {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(trans("complete_setup"), fontWeight = FontWeight.Bold, fontSize = 16.dp.value.sp)
            }
        }
    }

    if (showCommandDialog) {
        HumanInTheLoopDialog(
            command = "pkg upgrade && pkg update -y",
            onConfirm = {
                showCommandDialog = false
                isCommandExecuted = true
                onExecuteCommand("pkg upgrade && pkg update -y")
            },
            onDismiss = {
                showCommandDialog = false
            }
        )
    }
}

@Composable
fun MissingTermuxScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TermuxDownloadSection()
    }
}

@Composable
fun TermuxDownloadSection() {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = trans("termux_required"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = trans("termux_required_desc"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { uriHandler.openUri("https://f-droid.org/packages/com.termux/") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(trans("open_fdroid"), fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/termux/termux-app/releases") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(trans("open_github"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SetupWizard(
    modifier: Modifier = Modifier,
    securityHelper: SecurityHelper,
    onExecuteCommand: (String) -> Unit,
    onContinue: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var wizardStep by remember { mutableStateOf(1) }
    val context = LocalContext.current
    
    // For demonstrating the execution pipeline
    var showCommandDialog by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf("") }
    
    val pleaseSetupModelText = trans("please_setup_model")
    val sandboxBlockedMsg = trans("sandbox_blocked")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.White)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text("Synaptiq", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(trans("lead_architect"), fontSize = 10.dp.value.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(com.example.ui.theme.Green500, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Text(trans("secure_path"), fontSize = 12.dp.value.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Setup Wizard Progress
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(trans("setup_wizard"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(if (wizardStep == 1) trans("step_2") else trans("step_2_5"), fontSize = 14.dp.value.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (wizardStep == 1) 0.5f else 0.75f)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
        }

        // Main Content Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (wizardStep == 1) {
                // Environment Check Card
                Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(trans("env_check"), fontSize = 12.dp.value.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Slate400)
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = com.example.ui.theme.Green100
                        ) {
                            Text(
                                text = trans("connected"),
                                fontSize = 10.dp.value.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Green700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = com.example.ui.theme.Slate600,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(trans("termux_detected"), fontSize = 14.dp.value.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            // LLM Provider Tabs Area
            Column(modifier = Modifier.weight(1f)) {
                // Tab Switcher
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTabIndex = 0 }
                                .background(
                                    if (selectedTabIndex == 0) Color.White else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(trans("cloud_api"), fontSize = 14.dp.value.sp, fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.SemiBold, color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTabIndex = 1 }
                                .background(
                                    if (selectedTabIndex == 1) Color.White else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(trans("local_model"), fontSize = 14.dp.value.sp, fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.SemiBold, color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTabIndex == 0) {
                        CloudApiTab(securityHelper)
                    } else {
                        LocalModelsTab(
                            onTestCommand = { cmd ->
                                val level = securityHelper.getSecurityLevel()
                                if (level == 3) {
                                    Toast.makeText(context, sandboxBlockedMsg, Toast.LENGTH_SHORT).show()
                                } else if (level == 1) {
                                    onExecuteCommand(cmd)
                                } else {
                                    pendingCommand = cmd
                                    showCommandDialog = true
                                }
                            }
                        )
                    }
                }
            }

            // Security Context
            Surface(
                shape = MaterialTheme.shapes.large,
                color = com.example.ui.theme.Slate900
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = com.example.ui.theme.Indigo200,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(trans("defense_active"), fontSize = 10.dp.value.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Indigo200, letterSpacing = (-0.5).sp)
                        Text(trans("defense_desc"), fontSize = 11.dp.value.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            } else {
                SecurityLevelTab(securityHelper)
            }
        }

        // Bottom Action Bar
        Surface(
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                Button(
                    onClick = { 
                        if (wizardStep == 1) {
                            val hasCloudApiKey = LlmProviderType.values().any { securityHelper.getApiKey(it)?.isNotBlank() == true }
                            val hasLocalModel = securityHelper.isLocalModelConfigured()
                            
                            if (hasCloudApiKey || hasLocalModel) {
                                wizardStep = 2
                            } else {
                                Toast.makeText(context, pleaseSetupModelText, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            onContinue()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (wizardStep == 1) trans("continue_identity") else trans("finish_setup"), fontWeight = FontWeight.Bold, fontSize = 16.dp.value.sp)
                }
            }
        }
    }

    if (showCommandDialog) {
        HumanInTheLoopDialog(
            command = pendingCommand,
            onConfirm = {
                showCommandDialog = false
                securityHelper.setLocalModelConfigured(true)
                onExecuteCommand(pendingCommand)
            },
            onDismiss = {
                showCommandDialog = false
            }
        )
    }
}

@Composable
fun CloudApiTab(securityHelper: SecurityHelper) {
    val context = LocalContext.current
    
    val providers = listOf(
        CloudProviderInfo(
            type = LlmProviderType.GEMINI,
            name = "Google Gemini",
            models = listOf("gemini-1.5-pro", "gemini-1.5-flash", "Gemini 3.5 Flash", "Gemini 3.1 Flash Lite", "Gemma 4 31B")
        ),
        CloudProviderInfo(
            type = LlmProviderType.OPENAI,
            name = "OpenAI",
            models = listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")
        ),
        CloudProviderInfo(
            type = LlmProviderType.CLAUDE,
            name = "Anthropic Claude",
            models = listOf("claude-3-5-sonnet", "claude-3-opus", "claude-3-haiku")
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(providers) { provider ->
            CloudProviderCard(
                provider = provider,
                securityHelper = securityHelper,
                onSave = {
                    val msg = Strings.get("api_key_saved", securityHelper.getLanguage())
                    Toast.makeText(context, "${provider.name} $msg", Toast.LENGTH_SHORT).show()
                }
            )
        }
        item {
            Text(
                text = trans("keys_encrypted"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

data class CloudProviderInfo(
    val type: LlmProviderType,
    val name: String,
    val models: List<String>
)

@Composable
fun CloudProviderCard(
    provider: CloudProviderInfo,
    securityHelper: SecurityHelper,
    onSave: () -> Unit
) {
    var apiKey by remember { mutableStateOf(securityHelper.getApiKey(provider.type) ?: "") }
    var selectedModel by remember { mutableStateOf(provider.models.first()) }
    var expanded by remember { mutableStateOf(false) }
    val hasKey = apiKey.isNotBlank()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = provider.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.dp.value.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(trans("api_key")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasKey) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = trans("model_selection"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = true }
                                    .padding(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = selectedModel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Icon(imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                provider.models.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            selectedModel = model
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = trans("enter_key_to_select"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        securityHelper.saveApiKey(provider.type, apiKey)
                        onSave()
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(trans("save_key"))
                }
            }
        }
    }
}

@Composable
fun SecurityLevelTab(securityHelper: SecurityHelper) {
    var selectedLevel by remember { mutableStateOf(securityHelper.getSecurityLevel()) }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(trans("sec_level_title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        
        // Level 3
        SecurityLevelItem(
            title = trans("lvl3_name"),
            description = trans("lvl3_desc"),
            isSelected = selectedLevel == 3,
            onClick = { 
                selectedLevel = 3
                securityHelper.setSecurityLevel(3)
            }
        )

        // Level 2
        SecurityLevelItem(
            title = trans("lvl2_name"),
            description = trans("lvl2_desc"),
            isSelected = selectedLevel == 2,
            onClick = { 
                selectedLevel = 2
                securityHelper.setSecurityLevel(2)
            }
        )

        // Level 1
        SecurityLevelItem(
            title = trans("lvl1_name"),
            description = trans("lvl1_desc"),
            isSelected = selectedLevel == 1,
            onClick = { 
                selectedLevel = 1
                securityHelper.setSecurityLevel(1)
            }
        )
    }
}

@Composable
fun SecurityLevelItem(title: String, description: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.dp.value.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 12.dp.value.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LocalModelsTab(onTestCommand: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Warning Banner
        Surface(
            shape = MaterialTheme.shapes.large,
            color = com.example.ui.theme.Red50,
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.Red100),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                    contentDescription = null,
                    tint = com.example.ui.theme.Red600,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = trans("ram_safety"),
                    fontSize = 11.dp.value.sp,
                    color = com.example.ui.theme.Red700,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }
        }

        // Model Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            gridItems(LocalModels.AVAILABLE_MODELS) { model ->
                ModelCard(model = model, onTestCommand = onTestCommand)
            }
        }
    }
}

@Composable
fun ModelCard(model: LocalModelInfo, onTestCommand: (String) -> Unit) {
    val isRecommended = model.id == "gemma_2_2b"
    val isHighEnd = model.id == "llama_3_1_8b"

    val borderColor = when {
        isRecommended -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderWidth = if (isRecommended) 2.dp else 1.dp
    val titleColor = if (isHighEnd) com.example.ui.theme.Red900 else MaterialTheme.colorScheme.onBackground
    val subtitleColor = if (isHighEnd) com.example.ui.theme.Red600 else MaterialTheme.colorScheme.secondary
    val tagBg = when {
        isRecommended -> com.example.ui.theme.Indigo50
        isHighEnd -> com.example.ui.theme.Red50
        else -> com.example.ui.theme.Slate100
    }
    val tagColor = when {
        isRecommended -> MaterialTheme.colorScheme.primary
        isHighEnd -> com.example.ui.theme.Red700
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp) // Fixed height to match design
            .clickable { onTestCommand("ls -la") }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = model.name, fontWeight = FontWeight.Bold, fontSize = 14.dp.value.sp, color = titleColor)
                Text(
                    text = when(model.id) {
                        "gemma_2_2b" -> trans("recommended")
                        "phi_3_mini" -> trans("efficient")
                        "qwen_2_5_1_5b" -> trans("fast_inference")
                        "llama_3_1_8b" -> trans("high_end")
                        else -> ""
                    },
                    fontSize = 10.dp.value.sp,
                    color = subtitleColor
                )
            }
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = tagBg
            ) {
                Text(
                    text = model.ramRequirement.replace("Mind.", trans("min")).uppercase(),
                    fontSize = 10.dp.value.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontStyle = if (isHighEnd) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HumanInTheLoopDialog(
    command: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSecondConfirmation by remember { mutableStateOf(false) }

    if (showSecondConfirmation) {
        AlertDialog(
            onDismissRequest = { showSecondConfirmation = false },
            title = {
                Text(text = trans("critical_warning"))
            },
            text = {
                Text(trans("rm_warning_desc"))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSecondConfirmation = false
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(trans("yes_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecondConfirmation = false }) {
                    Text(trans("cancel"))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = trans("security_verification"))
            },
            text = {
                Column {
                    Text(trans("ai_wants_execute"))
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = command,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (command.trim().startsWith("rm ") || command.trim() == "rm") {
                            showSecondConfirmation = true
                        } else {
                            onConfirm()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(trans("execute"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(trans("block"))
                }
            }
        )
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatScreen(modifier: Modifier = Modifier, securityHelper: SecurityHelper) {
    var message by remember { mutableStateOf("") }
    
    // We need to keep track of the initial greeting dynamically or update it. 
    // To make it simple, we'll recreate the initial message if the language changes, 
    // or just let it be translated on the fly if it's the default message.
    val agentGreeting = trans("agent_greeting")
    val agentPlaceholder = trans("agent_placeholder")
    
    var messages by remember { mutableStateOf(listOf(ChatMessage(agentGreeting, false))) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages.size) { index ->
                val msg = messages[index]
                // If it's the exact default greeting in any language, update it.
                // For a real chat app, you wouldn't translate historical messages, but this is a mockup.
                val displayText = if (!msg.isUser && (msg.text == Strings.get("agent_greeting", "English") || msg.text == Strings.get("agent_greeting", "Deutsch"))) {
                    agentGreeting
                } else if (!msg.isUser && msg.text.startsWith(Strings.get("agent_placeholder", "English"))) {
                    agentPlaceholder + msg.text.removePrefix(Strings.get("agent_placeholder", "English"))
                } else if (!msg.isUser && msg.text.startsWith(Strings.get("agent_placeholder", "Deutsch"))) {
                    agentPlaceholder + msg.text.removePrefix(Strings.get("agent_placeholder", "Deutsch"))
                } else {
                    msg.text
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = displayText,
                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
        
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text(trans("type_message")) },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.IconButton(
                    onClick = {
                        if (message.isNotBlank()) {
                            val userMsg = message
                            messages = messages + ChatMessage(userMsg, true)
                            message = ""
                            // Dummy reply
                            messages = messages + ChatMessage(agentPlaceholder + "\"$userMsg\"", false)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.CheckCircle,
                        contentDescription = trans("send"),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
