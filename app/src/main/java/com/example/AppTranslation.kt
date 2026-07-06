package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalLanguage = staticCompositionLocalOf { "English" }

@Composable
fun AppTranslationProvider(language: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLanguage provides language) {
        content()
    }
}

@Composable
fun trans(key: String): String {
    val language = LocalLanguage.current
    return Strings.get(key, language)
}

object Strings {
    fun get(key: String, language: String): String {
        return when (language) {
            "Deutsch" -> de[key] ?: en[key] ?: key
            else -> en[key] ?: key
        }
    }
    
    private val en = mapOf(
        "security_block" to "Security Block: Command not allowed by whitelisting.",
        "cmd_sent" to "Command sent to Termux",
        "cmd_failed" to "Failed to send to Termux: ",
        "step_3" to "Step 3: Termux Setup",
        "termux_setup_started" to "Termux setup started. You can now complete the setup.",
        "termux_setup_desc" to "To use Termux optimally, the packages must be updated. Please confirm this process.",
        "start_setup" to "Start Setup (Confirm)",
        "complete_setup" to "Complete Setup",
        "termux_required" to "Termux is required",
        "termux_required_desc" to "Synaptiq requires the Termux app as a secure execution environment for local AI commands.\n\nPlease note: The version in the Google Play Store is outdated and no longer works for our purposes. Instead, download Termux from F-Droid or directly via GitHub.",
        "open_fdroid" to "Open via F-Droid",
        "open_github" to "Download via GitHub",
        "lead_architect" to "LEAD ARCHITECT MODE",
        "secure_path" to "Secure Path",
        "setup_wizard" to "Setup Wizard",
        "step_2" to "Step 2 of 3",
        "env_check" to "ENVIRONMENT CHECK",
        "connected" to "CONNECTED",
        "termux_detected" to "Termux runtime detected",
        "cloud_api" to "Cloud API",
        "local_model" to "Local Model",
        "defense_active" to "DEFENSE IN DEPTH ACTIVE",
        "defense_desc" to "Human-in-the-Loop + Whitelist Validation Enforced",
        "please_setup_model" to "Please set up a model first (Save Cloud API Key or test Local Model)",
        "continue_identity" to "Continue to Identity Setup",
        "keys_encrypted" to "Keys are encrypted on device using EncryptedSharedPreferences.",
        "api_key" to "API Key",
        "model_selection" to "Model Selection",
        "enter_key_to_select" to "Enter key to select model",
        "save_key" to "Save Key",
        "ram_safety" to "RAM Safety Rule: Android requires 3–4 GB RAM to survive. Model selection determines stability. App will crash on OOM if exceeded.",
        "recommended" to "Recommended",
        "efficient" to "Efficient",
        "fast_inference" to "Fast Inference",
        "high_end" to "High-End Only",
        "min" to "MIN",
        "security_verification" to "Security Verification",
        "ai_wants_execute" to "The AI agent wants to execute the following command in Termux. Please review carefully:",
        "execute" to "Execute",
        "block" to "Block",
        "agent_greeting" to "Hello! I am your Autonomous AI Agent. How can I help you today?",
        "agent_placeholder" to "I am a placeholder agent at the moment, but I receive your message: ",
        "type_message" to "Type message...",
        "send" to "Send",
        "settings" to "Settings",
        "language" to "Language",
        "api_key_saved" to "API Key saved",
        "critical_warning" to "CRITICAL WARNING",
        "rm_warning_desc" to "This command contains 'rm' which will permanently delete files. Are you absolutely sure?",
        "yes_delete" to "YES, DELETE",
        "cancel" to "CANCEL",
        "step_2_5" to "Step 3 of 4",
        "finish_setup" to "Finish Setup",
        "sec_level_title" to "Security Level",
        "lvl3_name" to "Level 3: Sandbox / Isolated",
        "lvl3_desc" to "AI runs in an isolated environment. No write access, no API calls, pure data processing.",
        "lvl2_name" to "Level 2: Supervised",
        "lvl2_desc" to "Read/write enabled, strict permission protocol. Every action must be manually authorized.",
        "lvl1_name" to "Level 1: Unrestricted",
        "lvl1_desc" to "Full access. AI acts agentically, creates sub-tasks and executes without intervention.",
        "sandbox_blocked" to "Blocked by Sandbox (Level 3)"
    )
    
    private val de = mapOf(
        "security_block" to "Sicherheitsblockade: Befehl nicht erlaubt.",
        "cmd_sent" to "Befehl an Termux gesendet",
        "cmd_failed" to "Senden an Termux fehlgeschlagen: ",
        "step_3" to "Schritt 3: Termux Setup",
        "termux_setup_started" to "Termux Setup wurde gestartet. Du kannst nun das Setup abschließen.",
        "termux_setup_desc" to "Um Termux optimal nutzen zu können, müssen die Pakete aktualisiert werden. Bitte bestätige diesen Vorgang.",
        "start_setup" to "Setup Starten (Bestätigen)",
        "complete_setup" to "Setup Abschließen",
        "termux_required" to "Termux wird benötigt",
        "termux_required_desc" to "Synaptiq benötigt die Termux-App als sichere Ausführungsumgebung für lokale KI-Befehle.\n\nBitte beachte: Die Version im Google Play Store ist veraltet und funktioniert nicht mehr für unsere Zwecke. Lade Termux stattdessen von F-Droid oder direkt über GitHub herunter.",
        "open_fdroid" to "Via F-Droid öffnen",
        "open_github" to "Via GitHub laden",
        "lead_architect" to "LEITENDER ARCHITEKT MODUS",
        "secure_path" to "Sicherer Pfad",
        "setup_wizard" to "Einrichtungsassistent",
        "step_2" to "Schritt 2 von 3",
        "env_check" to "UMGEBUNGSPRÜFUNG",
        "connected" to "VERBUNDEN",
        "termux_detected" to "Termux (F-Droid) Laufzeit erkannt",
        "cloud_api" to "Cloud API",
        "local_model" to "Lokales Modell",
        "defense_active" to "TIEFENVERTEIDIGUNG AKTIV",
        "defense_desc" to "Mensch-in-der-Schleife + Whitelist-Validierung erzwungen",
        "please_setup_model" to "Bitte richte zuerst ein Modell ein (Cloud API Key speichern oder Lokales Modell testen)",
        "continue_identity" to "Weiter zur Identitätseinrichtung",
        "keys_encrypted" to "Schlüssel werden auf dem Gerät verschlüsselt gespeichert.",
        "api_key" to "API-Schlüssel",
        "model_selection" to "Modellauswahl",
        "enter_key_to_select" to "Schlüssel eingeben zur Modellauswahl",
        "save_key" to "Schlüssel speichern",
        "ram_safety" to "RAM Sicherheitsregel: Android benötigt 3-4 GB RAM. App stürzt ab bei Überschreitung.",
        "recommended" to "Empfohlen",
        "efficient" to "Effizient",
        "fast_inference" to "Schnelle Inferenz",
        "high_end" to "Nur High-End",
        "min" to "MIN",
        "security_verification" to "Sicherheitsüberprüfung",
        "ai_wants_execute" to "Der KI-Agent möchte folgenden Befehl in Termux ausführen:",
        "execute" to "Ausführen",
        "block" to "Blockieren",
        "agent_greeting" to "Hallo! Ich bin dein Autonomer KI-Agent. Wie kann ich dir heute helfen?",
        "agent_placeholder" to "Ich bin im Moment ein Platzhalter-Agent, aber ich empfange deine Nachricht: ",
        "type_message" to "Nachricht schreiben...",
        "send" to "Senden",
        "settings" to "Einstellungen",
        "language" to "Sprache",
        "api_key_saved" to "API-Schlüssel gespeichert",
        "critical_warning" to "KRITISCHE WARNUNG",
        "rm_warning_desc" to "Dieser Befehl enthält 'rm', was Dateien dauerhaft löscht. Bist du absolut sicher?",
        "yes_delete" to "JA, LÖSCHEN",
        "cancel" to "ABBRECHEN",
        "step_2_5" to "Schritt 3 von 4",
        "finish_setup" to "Setup abschließen",
        "sec_level_title" to "Sicherheitsstufe",
        "lvl3_name" to "Level 3: Sandbox / Isoliert",
        "lvl3_desc" to "Die KI läuft isoliert. Kein Schreibzugriff, keine API-Calls, reine Datenverarbeitung.",
        "lvl2_name" to "Level 2: Supervised / Überwacht",
        "lvl2_desc" to "Lese-/Schreibzugriff aktiv, strenges Berechtigungs-Protokoll (manuelle Autorisierung).",
        "lvl1_name" to "Level 1: Unrestricted / Unbeschränkt",
        "lvl1_desc" to "Vollzugriff. Die KI agiert agentisch, erstellt Sub-Tasks ohne Nutzer-Intervention.",
        "sandbox_blocked" to "Blockiert durch Sandbox (Level 3)"
    )
}
