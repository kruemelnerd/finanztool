# Projekt-Status und TODOs

Zuletzt aktualisiert: 2026-02-20

## Sprintliste (Top 10)

1. [x] SDD-Traceability + Cucumber-Abdeckung vervollstaendigen
2. [x] Auth-Negativszenarien sauber testen
3. [x] Transaktionen: Pagination implementieren
4. [x] Filter-Reset als eigener UX-Flow
5. [x] Delete-Confirmations fuer riskante Aktionen
6. [x] Delete-Flows Ende-zu-Ende absichern
7. [x] i18n-Haertung: alle Controller-Messages externalisieren
8. [x] de-DE/EN-Formatierung strikt vereinheitlichen
9. [x] Dashboard-Chart-Szenarien vollstaendig spezifizieren
10. [x] Robustheit/UX: nutzerfreundliche Fehler + Retry

## Aktuell erledigt (letzte Aenderungen)

- Standardsprache auf DE gesetzt (App-Default + User-Default)
- Sprache aus Settings wird beim Login wiederhergestellt
- Chart auf Overview verbessert:
  - y-Achse mit Label und Tick-Werten
  - Marker fuer Abbuchungstage
  - Hover/Fokus-Tooltip mit Tages-Abbuchungen
- Delete-Confirmations fuer transaction delete, delete-all-data und delete-account umgesetzt
- SDD auf Semikolon-CSV korrigiert und Root-Struktur als final dokumentiert
- Delete-Flows jetzt als UI-Cucumber inkl. Confirm/Cancel abgedeckt
- Controller-Hardcodings auf i18n-Keys umgestellt (Flash-Messages)
- Partials-Error-State mit Retry fuer Chart/Recent/Transactions-Table umgesetzt
- Regeln-Feature auf kategoriezentrierte Verwaltung umgebaut (2-Schritt-Flow, mehrere Fragmente je Kategorie, Aktionen auf Kategorie-Ebene)
- Konfliktlogik im RuleEngine-Same-Category-Fall gehaertet (mehrere Fragmente derselben Kategorie erzeugen keinen Konflikt)
- UI-Smoketest fuer `/rules` als E2E-Abdeckung ergaenzt (2-Schritt-Create, Move/Toggle/Run/Delete)
- Ausgabenliste: Loeschen und Kategorie-Auswahl in ein 3-Punkte-Menue verschoben

## Aktuelle TODOs (naechste Schritte)

- [x] Delete-Confirmations implementieren (transaction delete, delete-all-data, delete-account)
- [x] Delete-Flows als Cucumber/E2E absichern (inkl. Cancel-Pfade)
- [x] i18n-Hardcodings in Controllern entfernen und in messages_* verschieben
- [x] Chart-Akzeptanzszenarien in Cucumber aufnehmen (Marker/Tooltip/y-axis)
- [x] Error-States mit Retry fuer Partials (insb. Overview-Chart) einfuehren
- [x] CSV-Encoding/Umlaute robust behandeln, damit Umlaute in Imports korrekt dargestellt werden
- [x] Gezielte Testfaelle fuer ReferenceText-Deduplizierung erweitern
- [x] Chart-Farbe dynamisch: bei Minus rot, bei Plus gruen

## Aktuelle TODOs (Kategorien, Regeln, Sankey)

- [x] Datenmodell finalisieren: `categories`, `rules`, `transactions`-Erweiterung (`category_id`, `category_assigned_by`, `category_locked`, `rule_conflicts` als JSON-Text)
- [x] Flyway-Migrationen anlegen inkl. Indizes/Constraints (Sub-Namen pro Parent eindeutig)
- [x] Kategorie-Bootstrap umsetzen (keine Bestandsmigration; Defaults beim Feature-Einstieg pro User anlegen)
- [x] Text-Normalisierung fuer Regel-Matching bauen (trim, spaces, case-insensitive, Unicode/Apostroph/Diakritik)
- [x] RuleEngine bauen (Reihenfolge: first match wins, Konflikte als Rule-IDs)
- [x] Backfill und Single-Rule-Run bauen (RULE ueberschreibbar, MANUAL nie ueberschreiben)
- [x] Transaktionen erweitert: Kategorie-Spalte, Warn-Icon, Filter nur Default, manuelle Kategorie + Lock (inkl. Finetuning der Status-/Lock-Anzeige)
- [x] Regeln-Seite `/rules` gebaut (Liste, Create/Edit, Toggle, Move up/down, Run one, Run all + Delete)
- [x] Sankey-API und Seite gebaut (`/api/reports/sankey`, `/reports/sankey`) mit Jahr-Dropdown
- [x] i18n fuer alle neuen Texte/Fehler/Warnungen DE/EN erweitert
- [x] Tests erweitern (Unit, Integration, Cucumber/UI) und danach `mvn test`

### Statusupdate (Start Umsetzung)

- Erste 3 Punkte umgesetzt (Schema, Migrationen, Bootstrap ohne Bestandsmigration)
- Danach umgesetzt: Normalizer + RuleEngine + AssignmentService (inkl. Import-Integration)
- Transaktions-Tabelle auf HTMX-Filterfluss angepasst; Playwright-Test auf asynchrones Reload-Waiting gehaertet
- Regeln-Seite umgesetzt inkl. Form-Flows, Reihenfolge, Aktiv-Toggle und Run-Buttons
- Regeln-Seite danach auf Kategoriegruppen refaktoriert (Create in 2 Schritten, Sammelbearbeitung pro Kategorie, Run/Toggle/Delete/Move pro Kategorie)
- Sankey-Report umgesetzt (Seite + API + Jahr-Dropdown + Basisdiagramm)
- Finetuning umgesetzt: Kategorie-Herkunft + Lock-Status in der Transaktions-Tabelle sichtbar
- de-DE/EN-Formatierung vereinheitlicht (u. a. Duplicate-Modal + Last-Import-Zeitstempel lokalisiert)
- Teststatus aktuell: `mvn test` erfolgreich (166 Tests, 0 Failures)

## Umsetzungsplan (durchgefuehrt)

1. Konsistenz korrigieren: SDD-CSV-Delimiter auf Semikolon, Root-Struktur als Source of Truth.
2. Qualitaetsluecken schliessen: Delete-Confirm/Cancel-Flows und Chart-Akzeptanz als Cucumber-Szenarien.
3. UX/Robustheit haerten: Partials mit nutzerfreundlichem Fehlerzustand und Retry.
4. Internationalisierung bereinigen: Controller-Flash-Messages komplett ueber `messages_*`.
5. Verifikation: gezielte Tests, danach kompletter `mvn test` Lauf.

## Hinweis fuer naechste Session

Diese Datei ist die zentrale Uebergabe fuer den naechsten Arbeitsstand.
Pfad: `docs/project-todo-status.md`

- TODO naechste Session: bei den Ausgaben mehrere Eintraege gleichzeitig anzeigen.
- TODO naechste Session: Kategorien in zwei Teile unterteilen.
- TODO naechste Session: Hoch/Runter in der bisherigen Form entfernen (nicht benoetigt).
- TODO naechste Session: auch bei Regeln ein 3-Punkte-Menue fuer Aktionen einfuehren.
- TODO naechste Session: aus den Ausgaben direkt eine Regel erweitern koennen.
- TODO naechste Session: 2 Konten sollen moeglich sein.
- TODO naechste Session: Export der Regeln.
