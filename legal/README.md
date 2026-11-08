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

## Was du noch tun musst (Hobby-App, keine Firma)

Du brauchst **keine Firma**. Als Privatperson bist du in den Texten einfach der „Anbieter“.

1. **Platzhalter ausfüllen:** In `app/src/main/java/com/example/LegalTexts.kt` deinen Vor- und
   Nachnamen (`OPERATOR`) und eine E-Mail-Adresse (`CONTACT`) eintragen, danach auch die
   Markdown-Dateien in diesem Ordner. Die Datenschutz-Grundverordnung verlangt, dass Nutzer den
   Verantwortlichen erreichen können; dafür reicht eine E-Mail-Adresse.
2. **Impressum:** Für ein rein privates, nicht kommerzielles Hobbyprojekt (keine Werbung, keine
   Einnahmen, keine In-App-Käufe) ist in der Regel kein Impressum nach § 5 DDG nötig. Sobald du
   Geld damit verdienst, brauchst du eines, mit ladungsfähiger Anschrift.
3. **Google Play:** Bei der Kontoeinrichtung als „Nicht-Händler“ (Privatperson ohne Einnahmen)
   angeben. Play verlangt trotzdem einen öffentlichen Link zur Datenschutzerklärung, z. B.
   `DATENSCHUTZ.md` über GitHub Pages, und das Formular „Datensicherheit“: Die App selbst sammelt
   keine Daten, sendet Eingaben aber an den gewählten KI-Anbieter.
4. **Bei Änderungen der Texte** `LegalTexts.VERSION` erhöhen, damit alle Nutzer erneut zustimmen.
5. **Wenn du später Geld verlangst oder Werbung einbaust:** Dann gilt die Haftungsprivilegierung
   für unentgeltliche Leistungen (§§ 521, 599 BGB) nicht mehr, Ziffer 7 der Nutzungsbedingungen muss
   angepasst werden, und Impressum, Widerrufsbelehrung und Gewährleistungsregeln kommen hinzu.

## Dateien

- `NUTZUNGSBEDINGUNGEN.md` / `TERMS_EN.md`: Nutzungsbedingungen & Haftungsausschluss
- `DATENSCHUTZ.md` / `PRIVACY_EN.md`: Datenschutzhinweise

Die Texte in der App stehen in `LegalTexts.kt`. Halte beide Stellen synchron.
