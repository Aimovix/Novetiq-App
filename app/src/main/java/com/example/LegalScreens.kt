package com.example

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Shown before anything else until the user accepted the current [LegalTexts.VERSION]. Every
 * statement has its own checkbox so the consent is explicit and informed.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    var adult by rememberSaveable { mutableStateOf(false) }
    var hallucinations by rememberSaveable { mutableStateOf(false) }
    var responsibility by rememberSaveable { mutableStateOf(false) }
    var terms by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var openDoc by remember { mutableStateOf<LegalTexts.Doc?>(null) }
    val allChecked = adult && hallucinations && responsibility && terms && privacy

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(trans("consent_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(trans("consent_intro"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(trans("consent_ai_notice"), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openDoc = LegalTexts.Doc.TERMS }, modifier = Modifier.weight(1f)) {
                    Text(trans("read_terms"), fontSize = 13.sp)
                }
                OutlinedButton(onClick = { openDoc = LegalTexts.Doc.PRIVACY }, modifier = Modifier.weight(1f)) {
                    Text(trans("read_privacy"), fontSize = 13.sp)
                }
            }

            SectionCard {
                CheckRow(adult, { adult = it }, trans("consent_adult"))
                CheckRow(hallucinations, { hallucinations = it }, trans("consent_hallucinations"))
                CheckRow(responsibility, { responsibility = it }, trans("consent_responsibility"))
                CheckRow(terms, { terms = it }, trans("consent_terms"))
                CheckRow(privacy, { privacy = it }, trans("consent_privacy"))
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.height(52.dp)) { Text(trans("decline")) }
                Button(onClick = onAccept, enabled = allChecked, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(trans("accept_continue"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    openDoc?.let { LegalDocDialog(it, onDismiss = { openDoc = null }) }
}

@Composable
fun LegalDocDialog(doc: LegalTexts.Doc, onDismiss: () -> Unit) {
    val language = LocalLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LegalTexts.title(doc, language)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(LegalTexts.text(doc, language), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(trans("close")) } },
    )
}

@Composable
fun AuditLogDialog(settings: SecurityHelper, onDismiss: () -> Unit) {
    var lines by remember { mutableStateOf(settings.getAuditLog()) }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMsg = trans("copied")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(trans("audit_log")) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (lines.isEmpty()) {
                    Text(trans("audit_empty"), color = MaterialTheme.colorScheme.secondary)
                } else {
                    SelectionContainer {
                        Text(lines.reversed().joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(trans("close")) } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Synaptiq log", lines.joinToString("\n"))))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    }
                }, enabled = lines.isNotEmpty()) { Text(trans("copy")) }
                TextButton(onClick = {
                    settings.clearAuditLog()
                    lines = emptyList()
                }, enabled = lines.isNotEmpty()) { Text(trans("clear"), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
