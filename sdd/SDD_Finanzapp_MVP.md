# SDD – Finanzapp MVP (Spring Boot + Thymeleaf/HTMX + SQLite → PostgreSQL)

**Version:** 1.0  
**Stand:** 2026-02-05  
**Technik:** Backend **Java Spring Boot**, Frontend **Thymeleaf + HTMX**, DB **SQLite** (später Option auf **PostgreSQL**)  
**Währung:** EUR (fix)  
**Sprachen:** DE / EN (UI), Formatierung bei DE strikt **de-DE**  
**Teststrategie:** Gherkin-Userstories sind **Executable Specifications** (Cucumber)

---

## 1. Ziel

Eine Web-App, mit der ein User nach Login seine Finanzen im Blick behält:

- **Kontostand** als **Chart** (End-of-Day) für **letzte 30 Tage**
- **Transaktionen** als **Tabelle** (alle Buchungen), **neueste oben**
- **Filter** (mindestens: Betrag-Min; Textsuche als **Teiltreffer** auf Name/Verwendungszweck, case-insensitive + trim)
- **Settings**: Name ändern, Sprache (DE/EN), **Alle Daten löschen** (Softdelete), **Account löschen** (Hard-delete User)

**CSV-Upload-UI** ist im MVP nicht zwingend Bestandteil; die **CSV wird aber als Artefakt in der DB gespeichert** und dient als Datenquelle für Transaktionen/Saldo-Berechnung.

---

## 2. Scope

### 2.1 In Scope (MVP)
- Auth: Registrierung, Login, Logout, Zugriffsschutz
- Dashboard (/overview): Chart + (optional KPI-Karten) + Recent Transactions
- Transaktionsliste (/transactions): Tabelle + Filter + Pagination empfohlen
- Softdelete einzelner Transaktionen
- "Delete all data": Softdelete für Transaktionen + CSV-Artefakte, danach **leeres Dashboard**
- "Delete account": **Hard-delete User** und Entfernen aller Daten
- I18n: DE/EN, de-DE Formatierung bei deutscher UI

### 2.2 Out of Scope (MVP)
- Budgets/Investments/Reports Business-Logik
- Pending-Status (nur "Completed" im MVP)
- Erweiterte Suche (LIKE/Fulltext), Kategorisierung, Regeln/Automationen
- CSV-Mapping-UI (wird später spezifiziert)

---

## 3. Domänenregeln

### 3.1 Kontostand-Berechnung (Chart)
- CSV enthält **Startsaldo** (z. B. „Alter Kontostand“).
- Chart zeigt **End-of-Day**-Saldo pro Tag für die **letzten 30 Tage**.
- Pro Tag können mehrere Buchungen existieren.
- End-of-Day für Tag *D*:

```
EOD(D) = Startsaldo + Summe(Amount aller Buchungen ≤ Ende von Tag D)
```

- Tage ohne Buchungen: EOD übernimmt den Stand des Vortags.

### 3.2 Transaktionen
Pflichtfelder (MVP):
- Datum (Uhrzeit optional, falls in CSV vorhanden)
- Name (Partner/Empfänger/Auftraggeber)
- Verwendungszweck
- Betrag (EUR fix)

Standardansicht:
- Sortierung: **neueste zuerst** (booking_datetime desc)
- Status im MVP intern: **Completed** (wird in der Tabelle aktuell nicht angezeigt)

### 3.3 Filter (MVP)
Minimum:
- Filter nach **Mindestbetrag** (>=)

Optional (im MVP möglich, wenn leicht):
- Teiltreffer-Suche auf Name und Verwendungszweck (contains, case-insensitive + trim)

### 3.4 Löschen
- Einzelne Buchung: **Softdelete** (`deleted_at` setzen)
- Alle Daten löschen: Softdelete für alle Transaktionen + CSV-Artefakte des Users
- Account löschen: **Hard-delete User** + Entfernen aller Daten (Hard-delete der abhängigen Daten; Softdelete ist hierbei nicht mehr relevant)

### 3.5 CSV-Artefakte
- CSV wird in der DB gespeichert (BLOB)
- **Maximal 10 MB pro Datei** (Ablehnung bei Überschreitung)

---

## 4. UX / Seiten & HTMX-Interaktionen

### 4.1 Seiten
- `/login`, `/register`
- `/overview` (Dashboard)
- `/transactions` (volle Transaktionsliste)
- `/settings` (Name, Sprache, Delete all data, Delete account)

### 4.2 HTMX Partials (empfohlen)
- `/partials/recent-transactions` – Tabelle (Top N)
- `/partials/transactions-table` – Tabelle mit Filter/Pagination
- `/partials/balance-chart?range=30d` – Chart Daten (HTML/JSON)
- optional `/partials/kpis`

### 4.3 Interaktionsmuster
- Filter-Formular lädt Tabelle per `hx-get` (partial update)
- Delete-Aktionen per `hx-post` oder `hx-delete` + UI-Refresh (Partial neu laden)
- Settings speichern per `hx-post` + success-state (Toast/Inline)

### 4.4 UI-Style
- Die Oberfläche soll einen modernen, ansprechenden Stil haben, mit angenehmen runden Ecken und einem aktuellen Look.

---

## 5. Datenmodell (SQLite + Postgres-freundlich)

### 5.1 User
- `id` (UUID oder Long)
- `email` (unique, not null)
- `password_hash` (BCrypt)
- `display_name`
- `language` (`DE`/`EN`)
- `created_at`

> Beim Account-Löschen: User wird **physisch** entfernt.

### 5.2 Transaction
- `id`
- `user_id`
- `booking_datetime` (LocalDateTime)
- `value_date` (LocalDate, optional)
- `transaction_type` (optional; z. B. aus CSV „Vorgang“)
- `partner_name` (not null für UI, ggf. fallback aus Text)
- `purpose_text` (not null)
- `raw_booking_text` (optional, empfohlen)
- `amount_cents` (long, not null)
- `currency` (String, default `"EUR"`)
- `status` (String, default `"Completed"`)
- `deleted_at` (timestamp, null wenn aktiv)
- `created_at`

**Index-Empfehlungen**
- `(user_id, booking_datetime desc)`
- Filter: `(user_id, amount_cents)` optional

### 5.3 CsvArtifact
- `id`
- `user_id`
- `original_file_name`
- `content_type`
- `bytes` (BLOB)
- `size_bytes`
- `uploaded_at`
- `deleted_at` (timestamp, null wenn aktiv)

### 5.4 BalanceDaily (optional aber empfohlen)
- `user_id`
- `date` (LocalDate)
- `balance_cents_end_of_day`
- `currency` (EUR)
- `computed_at`
- Unique `(user_id, date)`

> BalanceDaily kann beim Import berechnet werden; alternativ on-demand, solange deterministic.

---

## 6. Import-/Mapping-Regeln (aus bereitgestellter CSV)

### 6.1 CSV-Beobachtungen (Beispieldatei)
- CSV ist semikolongetrennt `;`
- Es gibt Meta-Zeilen vor der Buchungstabelle, z. B.:
  - `Neuer Kontostand;334,78 EUR`
  - `Alter Kontostand;908,13 EUR`
- Buchungstabelle beginnt mit Header:

**Header-Spalten**
- `Buchungstag`
- `Wertstellung (Valuta)`
- `Vorgang`
- `Buchungstext`
- `Umsatz in EUR`

**Betragsformat**
- deutsches Dezimal-Komma (z. B. `-52,84`)
- teils ohne Dezimalteil (z. B. `422`, `-31`)

**Uhrzeit**
- In der Datei keine separate Uhrzeit-Spalte.
- Produktregel: Uhrzeit kommt „immer von der Bank“ → Spezifikation erwartet `booking_datetime`.
- Für MVP: Wenn keine Uhrzeit in CSV geliefert wird, ist ein definierter Fallback nötig (z. B. `00:00:00`) oder Extraktion aus Text. (Wird beim finalen Import-Detail festgezurrt.)

### 6.2 Mapping (CSV → Entities)
- `Buchungstag` → Datum von `booking_datetime`
- Uhrzeit → aus Bankdaten (bevorzugt); fallback/Extraktion falls nicht vorhanden
- `Wertstellung (Valuta)` → `value_date` (optional)
- `Vorgang` → `transaction_type` (optional)
- `Buchungstext` → `raw_booking_text`
- `partner_name`:
  - falls `raw_booking_text` enthält `Auftraggeber: X` → `X`
  - sonst fallback (z. B. erster Token oder `Vorgang`)
- `purpose_text`:
  - falls enthält `Buchungstext: Y` → `Y`
  - sonst raw text
- `Umsatz in EUR` → `amount_cents`
  - `"-52,84"` → `-5284`
  - `"422"` → `42200`
- Startsaldo:
  - `Alter Kontostand` (aus Meta-Zeilen) → Startsaldo für Berechnung
- Optionaler Check:
  - `Neuer Kontostand` als Plausibilitätscheck

---

## 7. Endpoints (Server-rendered + HTMX)

### 7.1 Auth
- `GET/POST /register`
- `GET/POST /login`
- `POST /logout`

### 7.2 Dashboard
- `GET /overview`
- `GET /partials/balance-chart?range=30d`
- `GET /partials/recent-transactions?limit=...`

### 7.3 Transactions
- `GET /transactions`
- `GET /partials/transactions-table?minAmount=...&nameContains=...&purposeContains=...&page=...`
- `POST /transactions/{id}/delete` (Softdelete)

### 7.4 Settings
- `GET /settings`
- `POST /settings/profile` (Name)
- `POST /settings/language` (DE/EN)
- `POST /settings/delete-all-data` (Softdelete)
- `POST /settings/delete-account` (Hard-delete User + Daten)

---

## 8. Nicht-funktionale Anforderungen

### 8.1 Security
- Spring Security
- Passwort-Hash: BCrypt
- Passwortregel: minimal (z. B. Länge ≥ 8; genaue Policy im Code/Config)
- CSRF Schutz aktiv
- Session Timeout: Standard

### 8.2 Performance
- Tabelle paginiert (empfohlen)
- BalanceDaily (optional) für schnelle Chart-Darstellung

### 8.3 Internationalisierung
- UI in DE/EN (messages.properties)
- Bei Sprache DE: Formatierung strikt de-DE (Datum, Dezimaltrennzeichen etc.)

### 8.4 Datenbank & Migration
- SQLite (MVP)
- später PostgreSQL
- DB-Migrations: **Flyway** (einfach und gut lernbar)

---

## 9. Qualitätsanforderung: Executable Specifications (Cucumber)

### 9.1 Grundsatz
- **Jedes Gherkin-Szenario ist ein automatisierter Testfall.**
- Jeder Merge erfordert grüne Cucumber-Tests (CI).

### 9.2 Test-Level Zuordnung
- **UI/Flow** (Login, Settings, Listen, Delete):
  - `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `WebTestClient` oder `MockMvc`
  - HTMX Responses: HTML-Fragmente validieren (Containment/Structure)
- **Domain-Logik** (Saldo EOD, Parsing):
  - Unit Tests (JUnit), optional zusätzlich Cucumber
- **Persistence**:
  - Integration Tests gegen SQLite
  - später identischer Satz gegen PostgreSQL (profile)

### 9.3 Definition of Done (DoD)
- Feature gilt als fertig, wenn:
  - alle zugehörigen Gherkin Szenarien automatisiert sind
  - Migration (Flyway) vorhanden
  - Softdelete überall korrekt berücksichtigt wird
  - i18n-Strings vorhanden (DE/EN) für betroffene UI-Texte

---

# 10. User Stories (Gherkin, Deutsch)

> Hinweis: Die folgenden Features sind die „Source of Truth“ für Akzeptanz & automatisierte Tests.

---

## EPIC: Authentifizierung & Account

### Funktionalität: Registrierung mit E-Mail und Passwort
```gherkin
Funktionalität: Registrierung mit E-Mail und Passwort
  Als Besucher
  möchte ich mich mit E-Mail und Passwort registrieren
  damit ich meine Finanzdaten sicher speichern und ansehen kann

  Szenario: Registrierung mit gültigen Daten
    Angenommen ich bin auf der Registrierungsseite
    Wenn ich eine gültige E-Mail-Adresse eingebe
    Und ich ein Passwort eingebe, das den Passwortregeln entspricht
    Und ich das Registrierungsformular absende
    Dann wird ein neuer Benutzeraccount erstellt
    Und ich bin eingeloggt
    Und ich werde auf das Dashboard weitergeleitet

  Szenario: Registrierung mit bereits verwendeter E-Mail ablehnen
    Angenommen es existiert bereits ein Account für "user@example.com"
    Und ich bin auf der Registrierungsseite
    Wenn ich mich mit der E-Mail "user@example.com" registriere
    Und ich das Registrierungsformular absende
    Dann sehe ich eine Fehlermeldung, dass die E-Mail bereits verwendet wird
    Und es wird kein neuer Account erstellt

  Szenario: Registrierung mit zu schwachem Passwort ablehnen
    Angenommen ich bin auf der Registrierungsseite
    Wenn ich eine gültige E-Mail-Adresse eingebe
    Und ich ein Passwort eingebe, das den Passwortregeln nicht entspricht
    Und ich das Registrierungsformular absende
    Dann sehe ich eine Fehlermeldung zu den Passwortanforderungen
    Und es wird kein neuer Account erstellt
```

### Funktionalität: Login und Logout
```gherkin
Funktionalität: Login und Logout
  Als Benutzer
  möchte ich mich ein- und ausloggen können
  damit nur ich Zugriff auf meine Finanzdaten habe

  Szenario: Login mit korrekten Zugangsdaten
    Angenommen ich habe einen Benutzeraccount mit E-Mail "user@example.com" und gültigem Passwort
    Wenn ich mich mit E-Mail "user@example.com" und dem korrekten Passwort anmelde
    Dann bin ich erfolgreich eingeloggt
    Und ich werde auf das Dashboard weitergeleitet

  Szenario: Login mit falschem Passwort ablehnen
    Angenommen ich habe einen Benutzeraccount mit E-Mail "user@example.com"
    Wenn ich mich mit E-Mail "user@example.com" und einem falschen Passwort anmelde
    Dann sehe ich eine Fehlermeldung zur Authentifizierung
    Und ich bleibe ausgeloggt

  Szenario: Logout beendet die Session
    Angenommen ich bin eingeloggt
    Wenn ich auf "Log out" klicke
    Dann bin ich ausgeloggt
    Und ich werde auf die Login-Seite weitergeleitet
```

### Funktionalität: Geschützte Seiten
```gherkin
Funktionalität: Geschützte Seiten
  Als Product Owner
  möchte ich, dass geschützte Seiten eine Authentifizierung erfordern
  damit Finanzdaten nicht unberechtigt sichtbar sind

  Szenario: Unauthenticated User wird zum Login umgeleitet
    Angenommen ich bin nicht eingeloggt
    Wenn ich die Dashboard-URL aufrufe
    Dann werde ich auf die Login-Seite umgeleitet

  Szenario: Authenticated User darf das Dashboard öffnen
    Angenommen ich bin eingeloggt
    Wenn ich die Dashboard-URL aufrufe
    Dann sehe ich das Dashboard
```

---

## EPIC: Dashboard (UI-orientiert)

### Funktionalität: Navigation und Dashboard-Layout
```gherkin
Funktionalität: Navigation und Dashboard-Layout
  Als Benutzer
  möchte ich eine klare Sidebar-Navigation und ein übersichtliches Dashboard
  damit ich schnell zwischen Bereichen wechseln kann

  Szenario: Sidebar zeigt Hauptnavigation
    Angenommen ich bin eingeloggt
    Wenn ich das Dashboard öffne
    Dann sehe ich Navigationseinträge für "Overview", "Budgets", "Expenses", "Investments", "Reports" und "Settings"

  Szenario: Begrüßung zeigt den Benutzernamen
    Angenommen ich bin eingeloggt als Benutzer mit dem Namen "Ashley"
    Wenn ich das Dashboard öffne
    Dann sehe ich eine Begrüßung, die "Ashley" enthält
```

### Funktionalität: Kontostand-Chart der letzten 30 Tage (Startsaldo + End-of-Day)
```gherkin
Funktionalität: Kontostand-Chart der letzten 30 Tage (Startsaldo + End-of-Day)
  Als Benutzer
  möchte ich meinen Kontostand der letzten 30 Tage als End-of-Day Chart sehen
  damit ich den kurzfristigen Trend meiner Finanzen verstehe

  Szenario: Chart zeigt Kontostand der letzten 30 Tage
    Angenommen ich bin eingeloggt
    Und es existieren Kontostandsdaten für die letzten 30 Tage
    Wenn ich die Overview-Seite im Dashboard öffne
    Dann sehe ich ein Chart mit dem Titel "Account Balance"
    Und das Chart deckt die letzten 30 Tage ab

  Szenario: End-of-Day Kontostände werden pro Tag berechnet
    Angenommen ich bin eingeloggt
    Und es existiert ein Startsaldo für den betrachteten Zeitraum
    Und es existieren mehrere Buchungen an einem Tag
    Wenn ich das Dashboard öffne
    Dann sehe ich für jeden der letzten 30 Tage genau einen Kontostandwert
    Und der Wert entspricht dem Startsaldo plus der Summe aller Buchungen bis zum Ende dieses Tages

  Szenario: Tage ohne Buchungen übernehmen den letzten bekannten Kontostand
    Angenommen ich bin eingeloggt
    Und es existiert ein Startsaldo
    Und an einem bestimmten Tag gibt es keine Buchungen
    Wenn ich das Dashboard öffne
    Dann zeigt das Chart für diesen Tag den gleichen End-of-Day Kontostand wie am Vortag

  Szenario: Fehlende Kontostandsdaten werden als Empty State angezeigt
    Angenommen ich bin eingeloggt
    Und es existieren keine Kontostandsdaten für die letzten 30 Tage
    Wenn ich die Overview-Seite im Dashboard öffne
    Dann sehe ich einen Empty-State-Hinweis für das Kontostand-Chart
    Und ich sehe kein kaputtes oder unlesbares Chart
```

### Funktionalität: Übersichts-Karten im Dashboard (optional)
```gherkin
Funktionalität: Übersichts-Karten im Dashboard (optional)
  Als Benutzer
  möchte ich wichtige Summen in Karten sehen (z.B. Total Balance)
  damit ich einen schnellen Überblick bekomme

  Szenario: Total Balance wird als Karte angezeigt
    Angenommen ich bin eingeloggt
    Und es existieren Summenwerte zu meinen Konten
    Wenn ich die Overview-Seite im Dashboard öffne
    Dann sehe ich eine Karte "Total Balance"
    Und die Karte zeigt einen Betrag in Währung an
```

---

## EPIC: Transaktionen

### Funktionalität: Transaktionen als Tabelle
```gherkin
Funktionalität: Transaktionen als Tabelle
  Als Benutzer
  möchte ich meine Transaktionen in einer Tabelle sehen
  damit ich jede Überweisung nachvollziehen kann

  Szenario: Transaktionen werden absteigend nach Datum sortiert (neueste zuerst)
    Angenommen ich bin eingeloggt
    Und es existieren Transaktionen
    Wenn ich den Bereich "Recent Transactions" öffne
    Dann sehe ich eine Transaktionstabelle
    Und die Tabelle ist nach Datum absteigend sortiert

  Szenario: Tabelle zeigt die notwendigen Spalten
    Angenommen ich bin eingeloggt
    Und es existieren Transaktionen
    Wenn ich die Transaktionstabelle sehe
    Dann sehe ich die Spalten "Name", "Date" und "Amount"
```

### Funktionalität: Transaktionen enthalten Pflichtfelder und werden korrekt angezeigt
```gherkin
Funktionalität: Transaktionen enthalten Pflichtfelder und werden korrekt angezeigt
  Als Benutzer
  möchte ich meine Buchungen mit Datum, Name, Verwendungszweck und Betrag sehen
  damit ich jede Buchung eindeutig prüfen kann

  Szenario: Tabelle zeigt Buchungsdatum
    Angenommen ich bin eingeloggt
    Und es existiert eine Buchung mit Datum von der Bank
    Wenn ich die Transaktionstabelle sehe
    Dann wird das Datum der Buchung angezeigt

  Szenario: Währung ist immer Euro
    Angenommen ich bin eingeloggt
    Und es existieren Buchungen
    Wenn ich Beträge sehe
    Dann werden Beträge in Euro dargestellt
```

### Funktionalität: Transaktionen filtern und suchen (MVP: Mindestbetrag)
```gherkin
Funktionalität: Transaktionen filtern und suchen (MVP: Mindestbetrag)
  Als Benutzer
  möchte ich Transaktionen filtern
  damit ich bestimmte Buchungen schnell finde

  Szenario: Filter nach Mindestbetrag zeigt nur passende Transaktionen
    Angenommen ich bin eingeloggt
    Und es existieren Transaktionen mit unterschiedlichen Beträgen
    Wenn ich den Mindestbetrag-Filter auf "100.00" setze
    Dann sehe ich nur Transaktionen mit Betrag größer oder gleich "100.00"

  Szenario: Filter zurücksetzen stellt Standardansicht wieder her
    Angenommen ich bin eingeloggt
    Und ich habe Filter gesetzt
    Wenn ich alle Filter zurücksetze
    Dann sehe ich wieder die Standardansicht der Transaktionstabelle
```

---

## EPIC: Datenverwaltung & Löschfunktionen

### Funktionalität: Einzelne Transaktion löschen (Softdelete)
```gherkin
Funktionalität: Einzelne Transaktion löschen (Softdelete)
  Als Benutzer
  möchte ich eine einzelne Transaktion löschen können
  damit ich falsche oder unerwünschte Buchungen entfernen kann

  Szenario: Softdelete setzt deleted_at und entfernt die Buchung aus der Ansicht
    Angenommen ich bin eingeloggt
    Und es existiert eine Buchung in der Tabelle
    Wenn ich die Buchung lösche und bestätige
    Dann ist die Buchung in der Tabelle nicht mehr sichtbar
    Und die Buchung ist in der Datenbank als gelöscht markiert

  Szenario: Gelöschte Buchungen werden in Standardansichten nicht angezeigt
    Angenommen ich bin eingeloggt
    Und es existiert eine gelöschte Buchung
    Wenn ich das Dashboard oder die Transaktionsliste öffne
    Dann sehe ich die gelöschte Buchung nicht
```

### Funktionalität: Alle Daten löschen (Softdelete, leeres Dashboard)
```gherkin
Funktionalität: Alle Daten löschen (Softdelete, leeres Dashboard)
  Als Benutzer
  möchte ich alle meine gespeicherten Daten löschen können
  damit ich meine Finanzhistorie vollständig entfernen oder neu starten kann

  Szenario: Alle Transaktionen und CSV-Artefakte werden gelöscht
    Angenommen ich bin eingeloggt
    Und es existieren Transaktionen in meinem Account
    Und es existieren gespeicherte CSV-Artefakte in meinem Account
    Wenn ich in den Settings "Delete all data" auslöse und bestätige
    Dann sind alle Transaktionen als gelöscht markiert
    Und alle CSV-Artefakte sind als gelöscht markiert
    Und das Dashboard zeigt leere Zustände an
```

### Funktionalität: Account löschen (Hard-delete)
```gherkin
Funktionalität: Account löschen (Hard-delete)
  Als Benutzer
  möchte ich meinen Account vollständig löschen
  damit alle meine Daten entfernt werden

  Szenario: Account löschen entfernt User und alle zugehörigen Daten
    Angenommen ich bin eingeloggt
    Und es existieren Transaktionen und CSV-Artefakte in meinem Account
    Wenn ich in den Settings "Delete account" auslöse und bestätige
    Dann wird mein Benutzerkonto physisch gelöscht
    Und alle zugehörigen Transaktionen werden entfernt
    Und alle zugehörigen CSV-Artefakte werden entfernt
    Und ich werde ausgeloggt
    Und ein Login mit meinen alten Zugangsdaten ist nicht mehr möglich
```

---

## EPIC: Settings (Name & Sprache)

### Funktionalität: Anzeigename ändern
```gherkin
Funktionalität: Anzeigename ändern
  Als Benutzer
  möchte ich meinen Anzeigenamen ändern können
  damit die App mich korrekt anspricht

  Szenario: Name erfolgreich ändern
    Angenommen ich bin eingeloggt als Benutzer mit dem Namen "Ashley"
    Wenn ich die Settings öffne
    Und ich meinen Namen auf "Alex" ändere
    Und ich die Settings speichere
    Dann ist mein Name auf "Alex" aktualisiert
    Und ich sehe "Welcome back, Alex" im Dashboard
```

### Funktionalität: Sprache der Oberfläche ändern (DE/EN, de-DE Formatierung)
```gherkin
Funktionalität: Sprache der Oberfläche ändern (DE/EN, de-DE Formatierung)
  Als Benutzer
  möchte ich die Sprache der App ändern können
  damit ich die Oberfläche in meiner bevorzugten Sprache nutze

  Szenario: Sprache auf Deutsch stellt UI-Texte und Formatierung um
    Angenommen ich bin eingeloggt
    Wenn ich die Sprache in den Settings auf "DE" setze und speichere
    Dann werden UI-Texte auf Deutsch angezeigt
    Und Datums- und Zahlenformatierung entspricht de-DE

  Szenario: Sprache auf Englisch stellt UI-Texte um
    Angenommen ich bin eingeloggt
    Wenn ich die Sprache in den Settings auf "EN" setze und speichere
    Dann werden UI-Texte auf Englisch angezeigt
```

---

## EPIC: Robustheit & UX (MVP)

### Funktionalität: Empty States und Fehlerfälle
```gherkin
Funktionalität: Empty States und Fehlerfälle
  Als Benutzer
  möchte ich verständliche Hinweise bei fehlenden Daten oder Fehlern sehen
  damit ich nicht verwirrt werde oder stecken bleibe

  Szenario: Neuer Benutzer sieht Empty States
    Angenommen ich bin eingeloggt
    Und es existieren keine Transaktionen in meinem Account
    Wenn ich das Dashboard öffne
    Dann sehe ich einen Empty-State-Hinweis für "Recent Transactions"
    Und ich sehe einen Empty-State-Hinweis für das Kontostand-Chart

  Szenario: Backend-Fehler zeigt nutzerfreundliche Meldung
    Angenommen ich bin eingeloggt
    Und das System kann die Transaktionen wegen eines Serverfehlers nicht laden
    Wenn ich das Dashboard öffne
    Dann sehe ich eine nutzerfreundliche Fehlermeldung
    Und ich sehe eine Möglichkeit, den Ladevorgang erneut zu versuchen
```

---

## EPIC: CSV-Artefakte & Import (ohne Upload-UI)

### Funktionalität: CSV-Artefakt in Datenbank speichern (10MB)
```gherkin
Funktionalität: CSV-Artefakt in Datenbank speichern (10MB)
  Als Benutzer
  möchte ich, dass meine CSV-Datei in der Datenbank gespeichert wird
  damit die importierten Daten nachvollziehbar bleiben

  Szenario: CSV bis 10MB wird gespeichert
    Angenommen ich bin eingeloggt
    Wenn eine CSV-Datei mit Größe kleiner oder gleich 10MB gespeichert werden soll
    Dann wird die CSV als Artefakt meinem Account zugeordnet gespeichert

  Szenario: CSV größer als 10MB wird abgelehnt
    Angenommen ich bin eingeloggt
    Wenn eine CSV-Datei mit Größe größer als 10MB gespeichert werden soll
    Dann wird das Speichern abgelehnt
    Und ich sehe eine Fehlermeldung zum Größenlimit
```

### Funktionalität: Transaktionen aus deutscher Bank-CSV importieren
```gherkin
Funktionalität: Transaktionen aus deutscher Bank-CSV importieren
  Als Benutzer
  möchte ich, dass das System Transaktionen aus einer Bank-CSV erzeugt
  damit ich meine Buchungen in der App sehen kann

  Szenario: CSV-Zeilen werden zu Transaktionen gemappt
    Angenommen ich bin eingeloggt
    Und eine CSV enthält Spalten "Buchungstag", "Wertstellung (Valuta)", "Vorgang", "Buchungstext", "Umsatz in EUR"
    Wenn der Import ausgeführt wird
    Dann wird pro Buchungszeile eine Transaktion erzeugt
    Und die Transaktion enthält Datum, Name, Verwendungszweck und Betrag

  Szenario: Betrag wird aus deutschem Zahlenformat korrekt gelesen
    Angenommen ich bin eingeloggt
    Und eine CSV-Zeile enthält den Betrag "-52,84" in "Umsatz in EUR"
    Wenn der Import ausgeführt wird
    Dann wird der Betrag als -5284 Cent gespeichert

  Szenario: Betrag ohne Dezimalteil wird als volle Euro interpretiert
    Angenommen ich bin eingeloggt
    Und eine CSV-Zeile enthält den Betrag "422" in "Umsatz in EUR"
    Wenn der Import ausgeführt wird
    Dann wird der Betrag als 42200 Cent gespeichert
```

### Funktionalität: Startsaldo und End-of-Day Werte aus CSV ableiten
```gherkin
Funktionalität: Startsaldo und End-of-Day Werte aus CSV ableiten
  Als Benutzer
  möchte ich, dass das System End-of-Day Kontostände berechnet
  damit das 30-Tage-Chart korrekt ist

  Szenario: Startsaldo wird aus "Alter Kontostand" übernommen
    Angenommen ich bin eingeloggt
    Und eine CSV enthält "Alter Kontostand"
    Wenn der Import ausgeführt wird
    Dann wird der Startsaldo für die Berechnung aus "Alter Kontostand" übernommen

  Szenario: End-of-Day pro Tag berücksichtigt mehrere Buchungen
    Angenommen ich bin eingeloggt
    Und es existieren mehrere Buchungen am selben Buchungstag
    Wenn die End-of-Day Werte berechnet werden
    Dann enthält jeder Tag genau einen End-of-Day Wert
    Und dieser Wert ist der Startsaldo plus die Summe aller Buchungen bis Tagesende

  Szenario: Tage ohne Buchungen übernehmen den letzten Stand
    Angenommen ich bin eingeloggt
    Und es gibt einen Tag ohne Buchungen zwischen zwei Tagen mit Buchungen
    Wenn die End-of-Day Werte berechnet werden
    Dann ist der End-of-Day Wert am buchungsfreien Tag gleich dem Vortag
```

---

## 11. Anhänge

### 11.1 Offene Punkte (klein)
- Uhrzeit: Falls CSV-Export keine separate Uhrzeit-Spalte liefert, ist eine Extraktions-/Fallback-Regel nötig (z. B. `00:00:00`).
- BalanceDaily: Materialisierung vs on-demand Berechnung (empfohlen: materialisiert).
