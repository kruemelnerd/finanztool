# Finanztool – Kurz-Zusammenfassung (Stand: 2026-02-06)

## Was heute umgesetzt wurde

- CSV-Import robuster gemacht (u. a. Betragsformate mit Quotes), damit kein `NumberFormatException` mehr bei Folge-Uploads auftritt.
- Duplikaterkennung beim CSV-Import verbessert:
  - Duplikate werden nicht gespeichert.
  - Duplikate werden im Modal/Popup angezeigt.
  - Anzeige jetzt mit allen Duplikaten (nicht nur Samples).
- Current Balance aus CSV-Metazeilen eingebunden (`Alter Kontostand` / `Neuer Kontostand`) und auf der Transaktionsseite angezeigt.
- Filterverhalten angepasst:
  - Name/Verwendungszweck als Teiltreffer (`contains`, case-insensitive) statt exakter Treffer.
  - Parameter umbenannt auf `nameContains` und `purposeContains`.
- Transaktionstabelle angepasst:
  - Spalten `Time` und `Status` entfernt (werden derzeit nicht sinnvoll befuellt).
  - Softdelete pro Transaktion umgesetzt (Delete-Button + Backend-Softdelete).
- Overview-Seite visuell umgebaut (an `2.png` angelehnt):
  - oben links Current Balance,
  - oben rechts Dummy-Bildbereich,
  - mittig Linienchart fuer letzte 30 Tage,
  - unten letzte Buchungen.
- Chart von Balken auf Linienchart umgestellt (30-Tage-Verlauf als SVG).
- UI-Texte auf DE/EN i18n umgestellt (inkl. Fallback `messages.properties`).

## SDD-Abgleich / Dokumentation

- SDD wurde fuer die Filterlogik aktualisiert (Teiltreffer statt exakt).
- SDD wurde fuer die sichtbaren Tabellenspalten aktualisiert (Name, Date, Amount).

## Tests (heute relevant)

- Neue/angepasste Unit- und E2E-Tests fuer:
  - Duplikat-Modal + alle Duplikate,
  - Current Balance Anzeige,
  - Teiltreffer-Filter (`spac` findet `Space ...`),
  - Softdelete-Flow,
  - Overview-Rendering nach Redesign.
- Letzter Lauf (fokussiert) war gruen:
  - `mvn -Dtest=PagesControllerTest,PartialsControllerTest,PlaywrightE2ETest#loginFlowRendersOverview test`

## Offene naechste Schritte

- Weitere Executable Specs (Cucumber/E2E) fuer restliche SDD-Szenarien vervollstaendigen.
- Optional: Sidebar/Topbar visuell weiter an Referenz annaehern.
- Optional: Restliche serverseitige Meldungstexte (Flash-Messages) ebenfalls komplett i18n-faeig machen.
