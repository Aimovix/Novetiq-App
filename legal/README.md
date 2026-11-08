# Rechtliches – Checkliste für die Veröffentlichung

> **Wichtig:** Diese Texte sind eine sorgfältig formulierte Vorlage, aber **keine Rechtsberatung**.
> Kein Haftungsausschluss schützt vollständig vor Klagen. Nach deutschem Recht kann die Haftung für
> Vorsatz, grobe Fahrlässigkeit, Verletzung von Leben/Körper/Gesundheit und nach dem
> Produkthaftungsgesetz **nicht** ausgeschlossen werden (§ 309 Nr. 7 BGB, § 14 ProdHaftG). Die Texte
> beschränken die Haftung so weit, wie es gesetzlich zulässig ist. Lass sie vor einer
> Veröffentlichung, vor allem einer kommerziellen, von einer Anwältin oder einem Anwalt für IT-Recht
> prüfen.

## Was die App bereits umsetzt

| Maßnahme | Wo |
|---|---|
| Zustimmung vor der ersten Nutzung, jeder Punkt mit eigener Checkbox (18+, KI-Halluzinationen, Eigenverantwortung, Nutzungsbedingungen, Datenschutz) | `LegalScreens.kt` → `ConsentScreen` |
| Zustimmung wird mit Version und Zeitpunkt gespeichert und bei neuer Version erneut abgefragt | `SecurityHelper.acceptLegal()`, `LegalTexts.VERSION` |
| Eigene, informierte Zustimmung zur vollen Autonomie (Checkbox + Bestätigungswort) | `Components.kt` → `FullAutonomyConsentDialog` |
| Dauerhafter KI-Hinweis im Chat (Transparenzpflicht nach Art. 50 KI-Verordnung) | `ChatScreen.kt` |
| Gefährliche Aktionen brauchen immer eine Freigabe, katastrophale sind immer gesperrt | `AgentTools.kt` → `ShellGuard`, `AutonomyPolicy` |
| Aktionsprotokoll (Nachweis, wer was freigegeben hat) | Einstellungen → Aktionsprotokoll |
| Widerruf der Zustimmung und „Alle Daten löschen“ (DSGVO) | Einstellungen → Rechtliches |
| Nutzungsbedingungen und Datenschutzhinweise jederzeit einsehbar | Einstellungen → Rechtliches |

## Was du noch tun musst

1. **Platzhalter ausfüllen:** In `app/src/main/java/com/example/LegalTexts.kt` stehen `OPERATOR` und
   `CONTACT`. Trag deinen Namen (oder deine Firma) und eine ladungsfähige Anschrift plus E-Mail ein.
   Ändere danach auch die Markdown-Dateien in diesem Ordner.
2. **Impressum:** Wenn du die App geschäftsmäßig anbietest (auch kostenlos, z. B. mit Werbung oder
   als Marketing), brauchst du ein Impressum nach § 5 DDG, z. B. auf deiner Website und im Store-Eintrag.
3. **Datenschutzerklärung als URL:** Google Play verlangt einen öffentlichen Link. Du kannst
   `DATENSCHUTZ.md` und `PRIVACY_EN.md` z. B. über GitHub Pages veröffentlichen.
4. **Play-Store-Formular „Datensicherheit“** wahrheitsgemäß ausfüllen: Die App selbst sammelt keine
   Daten, sendet Eingaben aber an den gewählten KI-Anbieter.
5. **Bei Änderungen der Texte** `LegalTexts.VERSION` erhöhen, damit alle Nutzer erneut zustimmen.
6. **Kostenpflichtige Version?** Dann gilt die Haftungsprivilegierung für unentgeltliche Leistungen
   (§§ 521, 599 BGB) nicht mehr, und Ziffer 7 der Nutzungsbedingungen muss angepasst werden.
   Außerdem kommen Widerrufsbelehrung und Gewährleistungsregeln hinzu.

## Dateien

- `NUTZUNGSBEDINGUNGEN.md` / `TERMS_EN.md`: Nutzungsbedingungen & Haftungsausschluss
- `DATENSCHUTZ.md` / `PRIVACY_EN.md`: Datenschutzhinweise

Die Texte in der App stehen in `LegalTexts.kt`. Halte beide Stellen synchron.
