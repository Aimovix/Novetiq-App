package com.example

/**
 * Terms of use, disclaimer and privacy notice shown in the app.
 *
 * IMPORTANT for the publisher: fill in [OPERATOR] and [CONTACT] before publishing and let a lawyer
 * review the texts. Raise [VERSION] whenever the texts change materially - every user then has to
 * accept them again on the next start.
 */
object LegalTexts {

    const val VERSION = 1
    const val DATE = "08.11.2026"

    // TODO(publisher): Aimovix is a private hobby project - put in your own name and an e-mail
    // address where you can be reached. A company is not needed.
    const val OPERATOR = "[Vor- und Nachname]"
    const val CONTACT = "[E-Mail-Adresse]"

    enum class Doc { TERMS, PRIVACY }

    fun title(doc: Doc, language: String): String = when (doc) {
        Doc.TERMS -> if (language == Strings.GERMAN) "Nutzungsbedingungen & Haftungsausschluss" else "Terms of Use & Disclaimer"
        Doc.PRIVACY -> if (language == Strings.GERMAN) "Datenschutzhinweise" else "Privacy Notice"
    }

    fun text(doc: Doc, language: String): String = when (doc) {
        Doc.TERMS -> if (language == Strings.GERMAN) TERMS_DE else TERMS_EN
        Doc.PRIVACY -> if (language == Strings.GERMAN) PRIVACY_DE else PRIVACY_EN
    }

    private val TERMS_DE = """
Stand: $DATE · Version $VERSION

1. Anbieter und Geltungsbereich
Diese Bedingungen gelten für die Nutzung der App „Aimovix“ (die „App“), angeboten von $OPERATOR, $CONTACT (der „Anbieter“). Aimovix ist ein privates, nicht kommerzielles Hobbyprojekt einer Privatperson. Mit dem Tippen auf „Akzeptieren“ schließt du mit dem Anbieter einen unentgeltlichen Nutzungsvertrag zu diesen Bedingungen.

2. Was die App macht
Die App ist eine Software-Oberfläche. Sie leitet deine Anfragen an KI-Sprachmodelle von Drittanbietern (z. B. Google, OpenAI, Anthropic) oder an ein von dir lokal betriebenes Modell (Ollama) weiter. Je nach gewählter Sicherheitsstufe kann die KI Befehle in der App „Termux“ auf deinem Gerät ausführen, Dateien schreiben und Webseiten abrufen. Der Anbieter betreibt selbst keine KI-Modelle und keine Server. Den Vertrag mit einem KI-Anbieter schließt du selbst ab (eigener API-Schlüssel). Ein bestimmter Erfolg, eine bestimmte Verfügbarkeit oder Fehlerfreiheit ist nicht geschuldet.

3. Künstliche Intelligenz – Halluzinationen und Fehler
(1) Du kommunizierst mit einem KI-System, nicht mit einem Menschen.
(2) KI-Modelle arbeiten mit Wahrscheinlichkeiten. Sie können „halluzinieren“, d. h. Inhalte erzeugen, die falsch, unvollständig, veraltet, erfunden, widersprüchlich oder unangemessen sind, auch wenn sie überzeugend klingen. Sie können außerdem fehlerhafte, ungeeignete oder schädliche Befehle vorschlagen oder ausführen.
(3) Ausgaben der KI sind keine Rechts-, Steuer-, Medizin-, Finanz-, Sicherheits- oder sonstige fachliche Beratung und ersetzen diese nicht.
(4) Du prüfst Ausgaben und vorgeschlagene Aktionen selbst, bevor du sie verwendest oder freigibst. Entscheidungen auf Grundlage von KI-Ausgaben triffst du auf eigenes Risiko.

4. Autonome Ausführung und deine Verantwortung
(1) Über die Sicherheitsstufe bestimmst du selbst, wie viel die KI ohne Rückfrage tun darf. In den Stufen „Autonom“ und „Volle Autonomie“ führt die KI Aktionen ohne Einzelfreigabe aus.
(2) Die Schutzmechanismen der App (Freigabedialoge, Sperrlisten, Whitelist, Schrittlimit, Stopp-Taste, Aktionsprotokoll) verringern Risiken, schließen sie aber nicht aus. Sie garantieren nicht, dass keine Daten verloren gehen und keine unerwünschten Aktionen stattfinden.
(3) Jede Aktion, die du freigibst oder die aufgrund deiner Einstellungen automatisch ausgeführt wird, gilt als von dir veranlasst. Für diese Aktionen und ihre Folgen bist du verantwortlich.
(4) Vor der Nutzung autonomer Funktionen sicherst du wichtige Daten selbst (Backup).
(5) Inhalte aus dem Internet, die die KI abruft, können manipulierte Anweisungen enthalten („Prompt Injection“). Die App kennzeichnet sie als nicht vertrauenswürdig. Ein vollständiger Schutz ist technisch nicht möglich.

5. Verbotene Nutzung
Du nutzt die App nur im Rahmen der geltenden Gesetze und nur auf Geräten und Konten, über die du verfügen darfst. Untersagt ist insbesondere: (a) Angriffe auf fremde Systeme, Netzwerke oder Daten (z. B. unbefugtes Eindringen, Scans ohne Erlaubnis, Überlastungsangriffe); (b) das Erstellen oder Verbreiten von Schadsoftware; (c) die Verletzung von Urheber-, Marken-, Persönlichkeits- oder Datenschutzrechten Dritter; (d) Betrug, Belästigung, Spam oder das Versenden von Nachrichten ohne Einwilligung der Empfänger; (e) das Erzeugen rechtswidriger Inhalte; (f) Verstöße gegen die Nutzungsrichtlinien des jeweiligen KI-Anbieters.

6. Kosten Dritter
Cloud-KI-Modelle sind bei den Anbietern in der Regel kostenpflichtig und werden über deinen eigenen API-Schlüssel abgerechnet. Autonome Abläufe können viele Anfragen und damit Kosten erzeugen. Diese Kosten trägst du, ebenso Kosten für Datenverbrauch, SMS oder Anrufe, die durch Aktionen entstehen.

7. Haftung
(1) Die App wird unentgeltlich überlassen. Der Anbieter haftet deshalb – entsprechend den gesetzlichen Regeln für unentgeltliche Leistungen (§§ 521, 599 BGB) – nur für Vorsatz und grobe Fahrlässigkeit.
(2) Unberührt bleibt die Haftung (a) für Schäden aus der Verletzung des Lebens, des Körpers oder der Gesundheit, (b) nach dem Produkthaftungsgesetz, (c) bei arglistig verschwiegenen Mängeln und (d) soweit eine Haftung sonst gesetzlich zwingend ist.
(3) Im Rahmen von Absatz 1 haftet der Anbieter insbesondere nicht für: Inhalte und Ausgaben der KI-Modelle; Aktionen, die du freigegeben oder durch deine Autonomie-Einstellung erlaubt hast; Datenverluste, die durch eine übliche Datensicherung vermeidbar gewesen wären; Leistungen, Ausfälle, Preise oder Datenverarbeitung der KI-Anbieter, von Termux oder anderer Drittsoftware; Kosten nach Ziffer 6.
(4) Diese Beschränkungen gelten auch zugunsten der gesetzlichen Vertreter, Mitarbeiter und Erfüllungsgehilfen des Anbieters.

8. Freistellung
Du stellst den Anbieter von Ansprüchen Dritter frei, die diese wegen einer von dir zu vertretenden rechtswidrigen Nutzung der App oder eines Verstoßes gegen Ziffer 5 geltend machen. Das umfasst die angemessenen Kosten der Rechtsverteidigung.

9. Mindestalter
Die Nutzung ist nur Personen ab 18 Jahren erlaubt.

10. Laufzeit und Änderungen
Du kannst die Nutzung jederzeit beenden, indem du die App deinstallierst oder deine Zustimmung in den Einstellungen widerrufst. Der Anbieter kann diese Bedingungen für die Zukunft ändern. Geänderte Bedingungen werden dir beim nächsten Start angezeigt und gelten erst, wenn du ihnen zustimmst.

11. Schlussbestimmungen
Es gilt das Recht der Bundesrepublik Deutschland unter Ausschluss des UN-Kaufrechts. Bist du Verbraucher, gilt diese Rechtswahl nur, soweit dir dadurch nicht der Schutz zwingender Vorschriften des Staates entzogen wird, in dem du deinen gewöhnlichen Aufenthalt hast. Ist eine Bestimmung unwirksam, bleiben die übrigen wirksam. An die Stelle der unwirksamen Bestimmung tritt die gesetzliche Regelung.
""".trim()

    private val TERMS_EN = """
Effective: $DATE · Version $VERSION

1. Provider and scope
These terms govern the use of the app "Aimovix" (the "App"), provided by $OPERATOR, $CONTACT (the "Provider"). Aimovix is a private, non-commercial hobby project of a private individual. By tapping "Accept" you enter into a free-of-charge usage agreement with the Provider on these terms.

2. What the App does
The App is a software interface. It forwards your requests to AI language models of third parties (e.g. Google, OpenAI, Anthropic) or to a model you run locally (Ollama). Depending on the selected security level the AI can run commands in the "Termux" app on your device, write files and fetch web pages. The Provider does not run any AI models or servers. You conclude the contract with an AI provider yourself (your own API key). No particular result, availability or freedom from errors is owed.

3. Artificial intelligence – hallucinations and errors
(1) You are communicating with an AI system, not with a human.
(2) AI models work with probabilities. They can "hallucinate", i.e. produce content that is wrong, incomplete, outdated, made up, contradictory or inappropriate, even if it sounds convincing. They can also propose or execute faulty, unsuitable or harmful commands.
(3) AI output is not legal, tax, medical, financial, security or other professional advice and does not replace it.
(4) You check output and proposed actions yourself before using or approving them. Decisions based on AI output are made at your own risk.

4. Autonomous execution and your responsibility
(1) With the security level you decide how much the AI may do without asking. In the levels "Autonomous" and "Full autonomy" the AI performs actions without individual approval.
(2) The App's safeguards (approval dialogs, block lists, whitelist, step limit, stop button, action log) reduce risks but cannot exclude them. They do not guarantee that no data is lost or that no unwanted actions happen.
(3) Every action you approve, or that runs automatically because of your settings, is deemed to be initiated by you. You are responsible for these actions and their consequences.
(4) Before using autonomous features you back up important data yourself.
(5) Content the AI fetches from the internet may contain manipulated instructions ("prompt injection"). The App marks it as untrusted; complete protection is technically impossible.

5. Prohibited use
You use the App only in accordance with applicable law and only on devices and accounts you are entitled to use. In particular it is prohibited to: (a) attack third-party systems, networks or data (e.g. unauthorized access, scans without permission, denial of service); (b) create or distribute malware; (c) infringe copyrights, trademarks, personality or data protection rights of others; (d) commit fraud, harassment, spam, or send messages without the recipients' consent; (e) generate unlawful content; (f) violate the usage policies of the respective AI provider.

6. Third-party costs
Cloud AI models are usually paid services billed via your own API key. Autonomous runs can create many requests and therefore costs. You bear these costs, as well as costs for data usage, SMS or calls caused by actions.

7. Liability
(1) The App is provided free of charge. The Provider is therefore liable – in line with the statutory rules for gratuitous services (Sections 521, 599 German Civil Code) – only for intent and gross negligence.
(2) Liability remains unaffected (a) for injury to life, body or health, (b) under product liability law, (c) for fraudulently concealed defects and (d) wherever liability is otherwise mandatory by law.
(3) Within paragraph 1 the Provider is in particular not liable for: content and output of the AI models; actions you approved or allowed through your autonomy setting; data loss that a customary backup would have prevented; services, outages, prices or data processing of AI providers, Termux or other third-party software; costs under section 6.
(4) These limitations also apply in favor of the Provider's legal representatives, employees and agents.

8. Indemnification
You indemnify the Provider against third-party claims arising from an unlawful use of the App for which you are responsible, or from a breach of section 5, including reasonable legal defense costs.

9. Minimum age
Use is only permitted for persons aged 18 or older.

10. Term and changes
You can stop using the App at any time by uninstalling it or withdrawing your consent in the settings. The Provider may change these terms for the future. Changed terms are shown on the next start and only apply once you accept them.

11. Final provisions
The laws of the Federal Republic of Germany apply, excluding the UN Convention on Contracts for the International Sale of Goods. If you are a consumer, this choice of law applies only insofar as it does not deprive you of the protection of mandatory provisions of the country of your habitual residence. If a provision is invalid, the remaining provisions stay valid and the statutory rules apply instead.
""".trim()

    private val PRIVACY_DE = """
Stand: $DATE

1. Verantwortlicher
$OPERATOR, $CONTACT

2. Grundsatz
Der Anbieter betreibt keine eigenen Server. Er erhält weder deine Chats, Befehle und Dateien noch deine API-Schlüssel. Es gibt kein Tracking, keine Analyse-Tools und keine Werbung.

3. Speicherung auf deinem Gerät
API-Schlüssel, Einstellungen, Chatverlauf, deine Zustimmungen (mit Zeitpunkt) und das Aktionsprotokoll werden nur auf deinem Gerät gespeichert, verschlüsselt mit dem Android Keystore (AES-256), und sind von Android-Backups ausgeschlossen. Du kannst alles jederzeit in den Einstellungen löschen („Alle Daten löschen“).

4. Übermittlung an KI-Anbieter
Wenn du einen Cloud-Anbieter nutzt, werden deine Nachrichten, der Gesprächsverlauf und die Ergebnisse ausgeführter Aktionen (diese können Dateiinhalte enthalten) direkt von deinem Gerät an den gewählten Anbieter übertragen: Google (Gemini), OpenAI oder Anthropic (Claude). Diese verarbeiten die Daten in eigener Verantwortung nach ihren Datenschutzbestimmungen, auch in den USA (Grundlage u. a. EU-US Data Privacy Framework bzw. Standardvertragsklauseln des jeweiligen Anbieters). Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO, weil die Übertragung für die von dir gewünschte Funktion nötig ist. Gib keine besonders sensiblen Daten, Passwörter oder Daten anderer Personen ein, wenn du das nicht möchtest. Beim lokalen Modell (Ollama) verlassen deine Daten das Gerät nicht.

5. Webseiten-Abrufe
Ruft die KI eine Webseite ab, erhält der Betreiber dieser Webseite deine IP-Adresse und die üblichen technischen Verbindungsdaten.

6. Termux
Befehle und Dateiinhalte werden an die App Termux auf deinem Gerät übergeben und dort ausgeführt bzw. gespeichert.

7. Deine Rechte
Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung, Datenübertragbarkeit und Widerspruch (Art. 15–21 DSGVO), auf Widerruf einer Einwilligung mit Wirkung für die Zukunft (Art. 7 Abs. 3 DSGVO) sowie auf Beschwerde bei einer Datenschutz-Aufsichtsbehörde (Art. 77 DSGVO). Da der Anbieter keine Daten über dich speichert, kannst du die meisten Rechte direkt in der App ausüben. Für Daten bei KI-Anbietern wende dich an den jeweiligen Anbieter.
""".trim()

    private val PRIVACY_EN = """
Effective: $DATE

1. Controller
$OPERATOR, $CONTACT

2. Principle
The Provider does not run any servers. It receives neither your chats, commands and files nor your API keys. There is no tracking, no analytics and no advertising.

3. Storage on your device
API keys, settings, chat history, your consents (with timestamp) and the action log are stored only on your device, encrypted with the Android Keystore (AES-256), and are excluded from Android backups. You can delete everything at any time in the settings ("Delete all data").

4. Transfer to AI providers
If you use a cloud provider, your messages, the conversation history and the results of executed actions (which may contain file contents) are sent directly from your device to the selected provider: Google (Gemini), OpenAI or Anthropic (Claude). They process the data under their own responsibility and privacy policies, also in the USA (based on, among others, the EU-US Data Privacy Framework or the provider's standard contractual clauses). The legal basis is Art. 6(1)(b) GDPR because the transfer is required for the function you requested. Do not enter particularly sensitive data, passwords or other people's data if you do not want this. With the local model (Ollama) your data never leaves the device.

5. Web requests
When the AI fetches a web page, the operator of that page receives your IP address and the usual technical connection data.

6. Termux
Commands and file contents are handed to the Termux app on your device and executed or stored there.

7. Your rights
You have the right of access, rectification, erasure, restriction of processing, data portability and objection (Art. 15–21 GDPR), to withdraw consent with effect for the future (Art. 7(3) GDPR) and to lodge a complaint with a supervisory authority (Art. 77 GDPR). As the Provider stores no data about you, you can exercise most rights directly in the App. For data held by AI providers, contact the respective provider.
""".trim()
}
