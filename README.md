# Synaptiq

Autonomer KI-Agent für Android, der Befehle sicher in [Termux](https://termux.dev) ausführt.
Unterstützt Google Gemini, OpenAI und Anthropic Claude (Cloud) sowie lokale Modelle über
Ollama direkt auf dem Handy.

## Funktionen

- **Echter KI-Chat** mit Gemini, OpenAI, Claude oder einem lokalen Ollama-Modell. Der Anbieter
  lässt sich jederzeit im Chat wechseln. Der Verlauf wird verschlüsselt gespeichert.
- **Agenten-Modus**: Die KI schlägt Termux-Befehle vor, sieht deren Ausgabe und arbeitet damit
  weiter (bis zu 6 Schritte pro Nachricht).
- **Drei Sicherheitsstufen**
  - *Level 3 – Sandbox*: nur Text, keine Befehle.
  - *Level 2 – Überwacht*: jeder Befehl braucht deine Freigabe.
  - *Level 1 – Autonom*: Lesen/Schreiben läuft automatisch. Löschen und Überschreiben brauchen
    immer eine doppelte Bestätigung.
- **Tiefenverteidigung**: Whitelist-Prüfung, keine Shell (Befehle werden direkt ausgeführt, also
  ist Shell-Injection unmöglich), kein rekursives `rm`, kein `python -c`, kein `find -exec`.
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
| `AgentProtocol.kt` | System-Prompt und Erkennung von Befehlsvorschlägen |
| `LlmProvider.kt` | HTTP-Clients für Gemini, OpenAI, Claude und Ollama |
| `CommandValidator.kt` | Whitelist und Risikoeinstufung |
| `TermuxBridge.kt` | RUN_COMMAND-Intent und Rückgabe der Ausgabe |
| `SecurityHelper.kt` | Verschlüsselte Einstellungen |
