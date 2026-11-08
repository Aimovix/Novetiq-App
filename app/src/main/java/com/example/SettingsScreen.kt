package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: SecurityHelper,
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onRestartSetup: () -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit,
    onConsentWithdrawn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    // Bumped whenever a provider changes so the "active model" list recomposes.
    var revision by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var openDoc by remember { mutableStateOf<LegalTexts.Doc?>(null) }
    var showAudit by remember { mutableStateOf(false) }
    var confirmWithdraw by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var levelRevision by remember { mutableIntStateOf(0) }
    var fullAutoConsent by remember { mutableStateOf(settings.hasFullAutonomyConsent()) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = trans("back"))
                }
                Text(trans("settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(trans("language"))
            SectionCard {
                Strings.LANGUAGES.forEach { language ->
                    RadioRow(language, selected = currentLanguage == language) { onLanguageSelected(language) }
                }
            }

            SectionTitle(trans("active_model"))
            SectionCard {
                val configured = remember(revision) { settings.configuredProviders() }
                var active by remember(revision) { mutableStateOf(settings.getActiveProvider()) }
                if (configured.isEmpty()) {
                    Text(trans("no_model"), color = MaterialTheme.colorScheme.error)
                }
                configured.forEach { provider ->
                    RadioRow(
                        "${ModelCatalog.displayName(provider)} · ${settings.getSelectedModel(provider)}",
                        selected = active == provider,
                    ) {
                        settings.setActiveProvider(provider)
                        active = provider
                    }
                }
            }

            SectionTitle(trans("sec_level_title"))
            androidx.compose.runtime.key(levelRevision) {
                SecurityLevelSelector(settings, onChanged = { fullAutoConsent = settings.hasFullAutonomyConsent() })
            }
            if (fullAutoConsent) {
                OutlinedButton(
                    onClick = {
                        settings.revokeFullAutonomyConsent()
                        fullAutoConsent = false
                        levelRevision++
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(trans("revoke_full_auto")) }
            }

            SectionTitle(trans("max_steps"))
            SectionCard { MaxStepsSelector(settings) }

            SectionTitle(trans("cloud_api"))
            CloudApiSection(settings, onChanged = { revision++ })

            SectionTitle(trans("local_model"))
            LocalModelSection(settings, onChanged = { revision++ })

            SectionTitle("Termux")
            TermuxConnectionSection(settings)

            SectionTitle(trans("chat"))
            SectionCard {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("clear_chat"), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onRestartSetup, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("restart_setup"))
                }
                OutlinedButton(onClick = { showAudit = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("audit_log"))
                }
            }

            SectionTitle(trans("legal"))
            SectionCard {
                OutlinedButton(onClick = { openDoc = LegalTexts.Doc.TERMS }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("read_terms"))
                }
                OutlinedButton(onClick = { openDoc = LegalTexts.Doc.PRIVACY }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("read_privacy"))
                }
                settings.legalAcceptedAt()?.let { at ->
                    val time = remember(at) {
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))
                    }
                    Text(
                        trans("consent_given_at").format(time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                OutlinedButton(onClick = { confirmWithdraw = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("withdraw_consent"), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(trans("wipe_all"), color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                "Synaptiq ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(trans("clear_chat")) },
            text = { Text(trans("clear_chat_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    onClearChat()
                    confirmClear = false
                }) { Text(trans("clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(trans("cancel")) } },
        )
    }

    openDoc?.let { LegalDocDialog(it, onDismiss = { openDoc = null }) }
    if (showAudit) AuditLogDialog(settings, onDismiss = { showAudit = false })

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text(trans("withdraw_consent")) },
            text = { Text(trans("withdraw_consent_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWithdraw = false
                    settings.withdrawLegal()
                    onConsentWithdrawn()
                }) { Text(trans("withdraw"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text(trans("cancel")) } },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(trans("wipe_all")) },
            text = { Text(trans("wipe_all_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    onClearChat()
                    settings.wipeAllData()
                    onConsentWithdrawn()
                }) { Text(trans("clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(trans("cancel")) } },
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}
