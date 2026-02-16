# SDD Traceability Matrix

This matrix links SDD user-story scenarios to executable Cucumber specs.

Legend:
- `[x]` automated and passing in this codebase
- `[~]` partially covered
- `[ ]` not automated yet

## Authentifizierung & Account

- `[x]` Registrierung mit gueltigen Daten -> `src/test/resources/features/authentication.feature` ("Registration with valid data creates a user and logs in")
- `[x]` Registrierung mit bereits verwendeter E-Mail -> `src/test/resources/features/authentication.feature` ("Registration with duplicate email is rejected")
- `[x]` Registrierung mit zu schwachem Passwort -> `src/test/resources/features/authentication.feature` ("Registration with weak password is rejected")
- `[x]` Login mit korrekten Zugangsdaten -> `src/test/resources/features/authentication.feature` ("Login with correct credentials redirects to overview")
- `[x]` Login mit falschem Passwort -> `src/test/resources/features/authentication.feature` ("Login with wrong password is rejected")
- `[x]` Logout beendet Session -> `src/test/resources/features/authentication.feature` ("Logout ends the session")
- `[x]` Geschuetzte Seiten redirecten ohne Login -> `src/test/resources/features/authentication.feature` ("Unauthenticated user is redirected to login")

## Dashboard

- `[x]` Overview zeigt Kernmodule -> `src/test/resources/features/dashboard.feature`
- `[x]` Empty state fuer Chart -> `src/test/resources/features/partials.feature`
- `[x]` Empty state fuer Recent Transactions -> `src/test/resources/features/partials.feature`
- `[~]` Sidebar-Hauptnavigation vollstaendig -> teilweise in E2E sichtbar, kein dediziertes Cucumber-Szenario
- `[~]` Begruessung mit Namen -> indirekt vorhanden, kein dediziertes Cucumber-Szenario

## Transaktionen

- `[x]` Tabelle zeigt Spalten Name/Date/Amount -> `src/test/resources/features/partials.feature`
- `[x]` Filter nach Mindestbetrag -> `src/test/resources/features/ui-transactions.feature`
- `[x]` Filter zuruecksetzen -> `src/test/resources/features/transactions.feature`, `src/test/resources/features/ui-transactions.feature`
- `[x]` Pagination -> `src/test/resources/features/transactions.feature` ("Pagination splits long transaction lists")
- `[x]` Softdelete redirectet auf Liste -> `src/test/resources/features/transactions.feature` ("Delete transaction redirects to list")

## Settings

- `[x]` Settings-Seite zeigt Data Actions -> `src/test/resources/features/settings.feature`
- `[x]` Profil aktualisieren -> `src/test/resources/features/settings.feature`
- `[x]` Sprache aktualisieren -> `src/test/resources/features/settings.feature`
- `[x]` Delete all data redirect -> `src/test/resources/features/settings.feature`
- `[x]` Delete account redirect -> `src/test/resources/features/settings.feature`

## CSV / Import

- `[x]` CSV-Import als UI-Flow -> `src/test/resources/features/ui-transactions.feature`
- `[x]` Partials verhalten sich mit leeren Daten korrekt -> `src/test/resources/features/partials.feature`
- `[~]` Volle SDD-Importspezifikation inkl. aller Mapping-Edge-Cases -> abgedeckt ueber JUnit (`CsvParserTest`, `CsvImportServiceTest`), nicht vollstaendig als Cucumber
