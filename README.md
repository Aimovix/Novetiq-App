# Synaptiq

Autonomer KI-Agent für Android, der Befehle sicher in [Termux](https://termux.dev) ausführt.
Unterstützt Google Gemini, OpenAI und Anthropic Claude (Cloud) sowie lokale Modelle über
Ollama direkt auf dem Handy.

## Funktionen

- **Echter KI-Chat** mit Gemini, OpenAI, Claude oder einem lokalen Ollama-Modell. Der Anbieter
  lässt sich jederzeit im Chat wechseln. Der Verlauf wird verschlüsselt gespeichert.
- **Autonomer Agent**: Du gibst einen Auftrag, die KI plant, führt ihn Schritt für Schritt aus,
  sieht jedes Ergebnis und macht weiter, bis die Aufgabe erledigt ist (Schrittlimit 6–100, einstellbar).
  Werkzeuge:
  - `termux`: Befehle bzw. (bei voller Autonomie) ganze Bash-Skripte in Termux ausführen,
  - `write`: Dateien anlegen oder ersetzen (Inhalt über stdin, kein Quoting-Risiko),
  - `fetch`: öffentliche HTTPS-Webseiten lesen (nur lesend, als „nicht vertrauenswürdig“ markiert).
- **Vier Sicherheitsstufen**
  - *Level 3 – Sandbox*: nur Text, keine Aktionen.
  - *Level 2 – Überwacht*: jede Aktion braucht deine Freigabe.
  - *Level 1 – Autonom*: Whitelist-Befehle, Lesen/Schreiben läuft automatisch. Löschen und
    Überschreiben brauchen immer eine doppelte Bestätigung. Nach einem Webabruf fragt die KI vor
    jeder weiteren schreibenden Aktion (Schutz vor Prompt-Injection).
  - *Level 0 – Volle Autonomie*: beliebige Shell-Befehle mit Pipes, Skripten und Paketinstallationen
    ohne Rückfrage. Nur mit eigener Zustimmung (Checkbox + Bestätigungswort) aktivierbar.
- **Sicherheitsbedingungen, die immer gelten (auch bei voller Autonomie)**
  - Löschen, Verschieben, Prozesse beenden, Deinstallieren, `git reset --hard` usw. → Freigabe nötig.
  - SMS, Anrufe, Uploads, `git push`, `curl … | sh` und andere Aktionen nach außen → Freigabe nötig.
  - Root (`su`/`sudo`), Formatieren, Schreiben auf Geräte, Fork-Bomben, rekursives Löschen von
    Home-, Speicher- oder Systemordnern und Ändern der Termux-Sicherheitseinstellungen → immer gesperrt.
  - Stopp-Taste, Zeitlimit pro Befehl (5 min), verschlüsseltes Aktionsprotokoll.
- **Rechtliches**: Zustimmungsbildschirm beim ersten Start (18+, KI kann halluzinieren,
  Eigenverantwortung, Nutzungsbedingungen mit Haftungsbeschränkung, Datenschutz), KI-Hinweis im Chat,
  Widerruf und „Alle Daten löschen“ in den Einstellungen. Details und Checkliste: [`legal/`](legal/README.md).
- **Sichere Schlüssel**: API-Schlüssel liegen AES-256-verschlüsselt im Android Keystore, Backups
  sind ausgeschlossen, Klartext-HTTP ist nur zum lokalen Ollama-Server erlaubt.
- Deutsch/Englisch (folgt der Gerätesprache), Hell/Dunkel-Modus.

## Voraussetzungen auf dem Handy

1. **Termux von F-Droid oder GitHub** (die Play-Store-Version ist veraltet).
2. In der App: Berechtigung *„Befehlsausführung erlauben“* erteilen.
3. Einmalig in Termux ausführen (die App zeigt die Zeile mit Kopier-Button):
   ```sh
   mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties && termux-reload-settings
   ```
4. In der App *„Verbindung testen“* drücken.

### Lokales Modell (optional, offline)

Im Tab *Lokales Modell*: Ollama installieren → Modellserver starten → Modell herunterladen →
Verbindung testen. Die Termux-Sitzung mit `ollama serve` muss beim Chatten offen bleiben.

## Bauen

Benötigt JDK 17+ und das Android SDK (compileSdk 36.1). Gradle wird über den Wrapper geladen.

```sh
./gradlew assembleDebug          # Debug-APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # Unit- und Robolectric-UI-Tests
./gradlew lintDebug
```

Release-Signatur (optional): `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` und optional
`KEY_ALIAS` setzen. Ohne diese Variablen entsteht ein unsigniertes Release-APK, und Debug-Builds
nutzen den Standard-Debug-Schlüssel (oder `debug.keystore` im Projektordner, falls vorhanden).

## Projektstruktur

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg und Navigation |
| `SetupScreen.kt` / `ChatScreen.kt` / `SettingsScreen.kt` | Bildschirme |
| `Components.kt` | Gemeinsame UI (Freigabedialog, Anbieter, lokale Modelle, Termux-Verbindung) |
| `ChatViewModel.kt` | Agenten-Schleife: Antwort → Befehl → Freigabe → Ausführung → Ausgabe |
| `AgentProtocol.kt` | System-Prompt und Erkennung der Werkzeugaufrufe |
| `AgentTools.kt` | Werkzeuge, Freigabe-Regeln (`AutonomyPolicy`) und Sperrliste für volle Autonomie (`ShellGuard`) |
| `WebTool.kt` | Lesender HTTPS-Abruf von Webseiten |
| `LegalTexts.kt` / `LegalScreens.kt` | Nutzungsbedingungen, Datenschutz, Zustimmungsbildschirm, Aktionsprotokoll |
| `LlmProvider.kt` | HTTP-Clients für Gemini, OpenAI, Claude und Ollama |
| `CommandValidator.kt` | Whitelist und Risikoeinstufung |
| `TermuxBridge.kt` | RUN_COMMAND-Intent und Rückgabe der Ausgabe |
| `SecurityHelper.kt` | Verschlüsselte Einstellungen |
