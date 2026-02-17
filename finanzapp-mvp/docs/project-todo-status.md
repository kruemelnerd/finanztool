# Projekt-Status und TODOs

Zuletzt aktualisiert: 2026-02-17

## Sprintliste (Top 10)

1. [x] SDD-Traceability + Cucumber-Abdeckung vervollstaendigen
2. [x] Auth-Negativszenarien sauber testen
3. [x] Transaktionen: Pagination implementieren
4. [x] Filter-Reset als eigener UX-Flow
5. [x] Delete-Confirmations fuer riskante Aktionen
6. [ ] Delete-Flows Ende-zu-Ende absichern
7. [ ] i18n-Haertung: alle Controller-Messages externalisieren
8. [~] de-DE/EN-Formatierung strikt vereinheitlichen
9. [ ] Dashboard-Chart-Szenarien vollstaendig spezifizieren
10. [ ] Robustheit/UX: nutzerfreundliche Fehler + Retry

## Aktuell erledigt (letzte Aenderungen)

- Standardsprache auf DE gesetzt (App-Default + User-Default)
- Sprache aus Settings wird beim Login wiederhergestellt
- Chart auf Overview verbessert:
  - y-Achse mit Label und Tick-Werten
  - Marker fuer Abbuchungstage
  - Hover/Fokus-Tooltip mit Tages-Abbuchungen
- Delete-Confirmations fuer transaction delete, delete-all-data und delete-account umgesetzt

## Aktuelle TODOs (naechste Schritte)

- [x] Delete-Confirmations implementieren (transaction delete, delete-all-data, delete-account)
- [ ] Delete-Flows als Cucumber/E2E absichern (inkl. Cancel-Pfade)
- [ ] i18n-Hardcodings in Controllern entfernen und in messages_* verschieben
- [ ] Chart-Akzeptanzszenarien in Cucumber aufnehmen (Marker/Tooltip/y-axis)
- [ ] Error-States mit Retry fuer Partials (insb. Overview-Chart) einfuehren
- [x] CSV-Encoding/Umlaute robust behandeln, damit Umlaute in Imports korrekt dargestellt werden
- [x] Gezielte Testfaelle fuer ReferenceText-Deduplizierung erweitern
- [x] Chart-Farbe dynamisch: bei Minus rot, bei Plus gruen

## Hinweis fuer naechste Session

Diese Datei ist die zentrale Uebergabe fuer den naechsten Arbeitsstand.
Pfad: `finanzapp-mvp/docs/project-todo-status.md`
