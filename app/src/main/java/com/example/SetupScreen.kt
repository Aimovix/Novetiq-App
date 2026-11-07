package com.example

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SETUP_STEPS = 3

@Composable
fun SetupWizard(
    settings: SecurityHelper,
    onOpenSettings: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    var modelReady by rememberSaveable { mutableStateOf(settings.hasAnyModelConfigured()) }
    val pleaseSetupModel = trans("please_setup_model")

    BackHandler(enabled = step > 1) { step-- }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppHeader(onOpenSettings = onOpenSettings)

        val progress by animateFloatAsState(step / SETUP_STEPS.toFloat(), label = "progress")
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    trans("setup_wizard"),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Strings.get("step_x_of_y", LocalLanguage.current).format(step, SETUP_STEPS),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                trans(
                    when (step) {
                        1 -> "step_model_title"
                        2 -> "sec_level_title"
                        else -> "step_termux_title"
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                1 -> ModelStep(settings = settings, onChanged = { modelReady = settings.hasAnyModelConfigured() })
                2 -> {
                    Text(trans("sec_level_intro"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SecurityLevelSelector(settings)
                    DefenseBanner()
                }
                else -> {
                    Text(trans("termux_intro"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TermuxConnectionSection(settings)
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 1) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.height(52.dp)) { Text(trans("back")) }
                }
                Button(
                    onClick = {
                        when {
                            step == 1 && !modelReady -> Toast.makeText(context, pleaseSetupModel, Toast.LENGTH_LONG).show()
                            step < SETUP_STEPS -> step++
                            else -> {
                                settings.setSetupComplete(true)
                                onFinished()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(
                        text = trans(if (step < SETUP_STEPS) "continue" else "finish_setup"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStep(settings: SecurityHelper, onChanged: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EnvironmentCheckCard()
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(trans("cloud_api")) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(trans("local_model")) })
        }
        if (tab == 0) CloudApiSection(settings, onChanged) else LocalModelSection(settings, onChanged)
    }
}

@Composable
private fun EnvironmentCheckCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(trans("env_check"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            StatusPill(trans("detected"), positive = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SuccessIcon(Modifier.size(20.dp))
            Text(trans("termux_detected"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AppHeader(onOpenSettings: (() -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text("S", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Synaptiq", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(7.dp).background(com.example.ui.theme.Green500, shape = CircleShape))
                    Text(trans("secure_path"), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (onOpenSettings != null) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = trans("settings"))
                }
            }
        }
    }
}

@Composable
fun MissingTermuxScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Text(trans("termux_required"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    trans("termux_required_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { uriHandler.openUri("https://f-droid.org/packages/com.termux/") }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("open_fdroid"), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { uriHandler.openUri("https://github.com/termux/termux-app/releases") }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("open_github"))
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(trans("check_again")) }
            }
        }
    }
}
