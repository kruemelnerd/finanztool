# SDD – Kategorien & Regelbasierte Zuordnung + Sankey (letztes Kalenderjahr)

**Version:** 1.0  
**Stand:** 2026-02-19  
**Erweiterung zu:** Finanzapp MVP (Spring Boot + Thymeleaf/HTMX + SQLite → PostgreSQL)  
**Ziel:** Jede Buchung hat eine Kategorie (mindestens Default-Kategorie), Regeln ordnen automatisch zu, User kann manuell korrigieren. Daraus wird ein Sankey für das **letzte Kalenderjahr** erzeugt.

---

## 1. Ziel

1) **Automatische Kategorisierung** von Transaktionen über vom User gepflegte Regeln (Text enthält …)  
2) **Manuelle Kategorisierung** in der Transaktionstabelle (UI)  
3) **Regelverwaltung** auf eigener Seite (Liste, Toggle aktiv/inaktiv, erstellen, hoch/runter, „Regel erneut ausführen“)  
4) **Sankey-Diagramm** für das letzte Kalenderjahr: **Income/Expense → Kategorie** (Option 2), inkl. Einnahmen und Ausgaben  
5) **Qualität:** Jede Buchung soll einer Kategorie zugeordnet sein — wenn keine Regel/Manuell, dann **Default-Kategorie**; zusätzlich UI-Warnung, wenn unzugeordnet/Default oder wenn mehrere Regeln matchen.

---

## 2. Scope

### 2.1 In Scope
- Kategorien (User-spezifisch) mit **2-stufiger Hierarchie**: Parent-Kategorie + Sub-Kategorie
- Default-Kategorie-Set beim User-Onboarding (kann angepasst werden)
- Regel-Engine: **contains** (substring), case-insensitive, mit **Normalisierung**
- Regeln prüfen: Buchungstext, Auftraggeber/Partnername oder beides
- Konfliktlogik: **Erste Regel gewinnt** (Regelreihenfolge)
- Zusätzliches Signal: Wenn **mehr als eine Regel matcht**, wird in der UI eine **Warnung** angezeigt (Regelnamen)
- Regel-Backfill: Button, um Regeln auf bestehende Buchungen anzuwenden
- Pro-Regel Button: „Regel erneut ausführen“ (nur diese Regel)
- Transaktionstabelle zeigt **Kategorie-Spalte**, inkl. Filter „nur Default/Unzugeordnet“
- Sankey-Seite mit Filter (optional): alle / nur Ausgaben / nur Einnahmen

### 2.2 Out of Scope (v1)
- Regex/Advanced Matching
- ML-Kategorisierung
- Mehrstufige Hierarchien (>2)
- Multi-Account-Flows im Sankey (kommt später)

---

## 3. Begriffe & Domänenregeln

### 3.1 Kategorie (2-stufig)
- Kategorie besteht aus:
  - **Parent** (z. B. „Essen & Trinken“)
  - **Sub** (z. B. „FastFood“)
- Buchung referenziert **eine Sub-Kategorie** (damit Parent indirekt bekannt ist).
- **Default-Kategorie**: `Sonstiges → Unkategorisiert` (wird zugewiesen, wenn keine bessere Zuordnung existiert).

### 3.2 Default-Kategorie-Set (Vorschlag)
Basierend auf gängigen Budget-/Banking-Kategorien (z. B. Bills/Utilities, Transport, Eating out, Groceries, Entertainment, Shopping, Insurance, Housing etc.). citeturn0search1turn0search3turn0search4turn0search8turn0search6

**Parent → Sub (Beispiele, erweiterbar durch User)**
- **Einnahmen**
  - Gehalt
  - Erstattung
  - Sonstige Einnahmen
- **Wohnen**
  - Miete/Hypothek
  - Nebenkosten
  - Internet/Telefon
- **Essen & Trinken**
  - Groceries
  - Restaurants
  - **FastFood**
  - Coffee/Snacks
- **Transport**
  - ÖPNV
  - Tanken
  - Bahn/Flug
  - Parkplatz/Maut
- **Rechnungen & Abos**
  - Streaming (Netflix/Spotify)
  - Mobilfunk
  - Strom/Gas/Wasser
- **Shopping**
  - Kleidung
  - Elektronik
  - Haushalt
  - **Sport** (als Sub unter Shopping oder Freizeit; du kannst das frei verschieben)
- **Gesundheit**
  - Apotheke
  - Arzt
  - Fitness
- **Freizeit**
  - Entertainment
  - Urlaub
  - Hobbys
- **Versicherung**
  - Haftpflicht
  - KFZ
  - Sonstige Versicherungen
- **Finanzen**
  - Gebühren
  - Steuern
  - Sparen/Investieren
- **Sonstiges**
  - **Unkategorisiert** (Default)

> Die Defaults sind initial vorhanden, aber **User-spezifisch** und frei editierbar (anlegen/umbenennen/löschen).

### 3.3 Regel-Matching
- Matching: **contains** (substring), **case-insensitive**
- Normalisierung vor Match:
  - trim, Mehrfachspaces → 1 Space
  - Unicode-Normalisierung (NFKC)
  - Normalisierung von Apostrophen/Diakritika (z. B. „McDonald’s“ → „mcdonalds“)
- Suchfelder:
  - `BOOKING_TEXT` (raw_booking_text / purpose_text)
  - `PARTNER_NAME` (partner_name)
  - `BOTH` (OR)
- Erweiterbarkeit: spätere zusätzliche Felder (z. B. `Vorgang`) sind vorgesehen.

### 3.4 Konflikte / Mehrfachtreffer
- Regeln werden in Reihenfolge ausgewertet.
- **Erste Regel gewinnt** und setzt die Kategorie.
- Wenn zusätzlich andere Regeln matchen, wird die Buchung mit einer **Warnung** markiert:
  - `rule_conflicts = [Regel A, Regel B]`
  - UI zeigt Warnicon + Tooltip „Weitere passende Regeln: …“

### 3.5 Anwendung / Reprocessing
- Beim Import neuer Buchungen werden Regeln angewendet.
- Button „Regeln auf bestehende Buchungen anwenden“ (Backfill).
- Pro Regel „Regel erneut ausführen“:
  - wendet diese Regel auf alle Buchungen im Account an
  - MVP-Default: nur auf Buchungen, die noch Default/Unkategorisiert sind (damit manuelle Zuweisungen nicht überschrieben werden)

### 3.6 Manuelle Kategoriezuweisung
- User kann pro Buchung Kategorie auswählen/ändern.
- Manuelle Zuweisung „fixiert“ die Buchung:
  - Regelanwendung überschreibt nicht, solange `category_locked=true` (oder `assigned_by=MANUAL`)

---

## 4. UX / Seiten

### 4.1 Transaktionen-Seite
- Tabelle bekommt neue Spalte: **Kategorie**
- Wenn Kategorie = Default (`Unkategorisiert`), dann roter Hinweis + Filter „nur unzugeordnet/Default“
- Warnsymbol, wenn mehrere Regeln matchen (Tooltip mit Regelliste)
- Inline-Edit Kategorie:
  - Dropdown (Parent/Sub) oder Sub-Liste mit Grouping
  - Save via HTMX

### 4.2 Regeln-Seite `/rules`
- Tabelle mit Spalten:
  - Name
  - Suchtext
  - Feld (Buchungstext/Auftraggeber/Beides)
  - Kategorie (Parent → Sub)
  - Aktiv (Toggle)
  - Letzte Anwendung
  - Trefferanzahl (z. B. letzte Ausführung)
  - Aktionen: „Ausführen“, „Bearbeiten“, „Löschen“, „Hoch“, „Runter“
- Button „Neue Regel“ (öffnet Form)
- Button „Regeln anwenden“ (Backfill)

### 4.3 Sankey-Seite `/reports/sankey`
- Zeitraum: **letztes Kalenderjahr**
- Darstellung: **Income/Expense → Kategorie** (Option 2)
  - Knoten links: `Income`, `Expense`
  - Knoten rechts: Kategorien (Parent oder Sub; siehe offene Punkte)
- Toggle/Filter:
  - Include Income (ja)
  - Include Expense (ja)

---

## 5. Datenmodell & Migration

### 5.1 Neue Entities

**Category**
- `id`
- `user_id` (nicht null; Defaults werden pro User kopiert)
- `parent_id` (nullable, für Parent-Kategorien)
- `name`
- `sort_order`
- `is_default` (bool)
- `deleted_at` (optional; Softdelete empfohlen)
- Constraints:
  - Parent: `parent_id IS NULL`
  - Sub: `parent_id IS NOT NULL`

**Rule**
- `id`
- `user_id`
- `name`
- `match_text` (normalized stored, original optional)
- `match_field` enum: `BOOKING_TEXT | PARTNER_NAME | BOTH`
- `category_id` (FK auf SubCategory)
- `is_active`
- `sort_order`
- `last_run_at`
- `last_match_count`
- `deleted_at` (optional)

**Transaction (Erweiterung)**
- `category_id` (FK auf SubCategory)
- `category_assigned_by` enum: `DEFAULT | RULE | MANUAL`
- `category_locked` bool (true, wenn MANUAL)
- `rule_conflicts` (optional; JSON/Text)

### 5.2 Default-Kategorien
- Beim User-Register (oder First Login) werden Default-Kategorien **kopiert** (einfachste MVP-Variante).

### 5.3 Flyway-Migrations (Vorschlag)
- `V__create_categories.sql`
- `V__create_rules.sql`
- `V__alter_transactions_add_category.sql`

---

## 6. Services / Logik

### 6.1 Normalizer
- `normalize(text) -> normalizedText`
  - lower-case
  - trim
  - collapse whitespace
  - normalize apostrophes/diacritics (konfigurierbar)

### 6.2 RuleEngine
- Input: Transaction (partner_name, raw_booking_text/purpose_text)
- Load active rules ordered by `sort_order`
- For each rule:
  - Determine target string(s) based on match_field
  - If normalized(transactionField).contains(normalized(rule.match_text)):
    - firstMatch = rule if not set
    - conflicts add rule if firstMatch already set
- Apply:
  - If transaction.category_locked: do nothing
  - Else if firstMatch exists:
    - set category_id, assigned_by=RULE
    - set conflicts (if any)
  - Else:
    - set default category_id, assigned_by=DEFAULT
    - clear conflicts

### 6.3 Backfill / Single Rule Run
- Backfill: apply all rules
- Single Rule Run: apply only that rule; update `last_run_at` + `last_match_count`

---

## 7. Endpoints (Thymeleaf + HTMX)

### 7.1 Regeln
- `GET /rules`
- `GET /rules/new`
- `POST /rules` (create)
- `GET /rules/{id}/edit`
- `POST /rules/{id}` (update)
- `POST /rules/{id}/toggle`
- `POST /rules/{id}/move-up`
- `POST /rules/{id}/move-down`
- `POST /rules/{id}/run`
- `POST /rules/run-all`

### 7.2 Transaktionen
- `GET /transactions`
- `GET /partials/transactions-table?...&onlyUncategorized=true`
- `POST /transactions/{id}/set-category` (manual assign; locks)

### 7.3 Sankey
- `GET /reports/sankey`
- `GET /api/reports/sankey?year=YYYY` (nodes+links JSON)

---

## 8. Sankey – Daten-Spezifikation

### 8.1 Zeitraum
- Letztes Kalenderjahr = `01.01.(Jahr-1)` bis `31.12.(Jahr-1)` bezogen auf „heute“.

### 8.2 Knoten/Links
- Nodes:
  - `Income`
  - `Expense`
  - Category nodes: Parent oder Sub
- Links:
  - Income → Kategorie: Summe aller positiven Beträge
  - Expense → Kategorie: Summe aller negativen Beträge als `abs(value)`

---

## 9. Nicht-funktionale Anforderungen
- Performance: ~3000 Buchungen pro User, Regeln i. d. R. < 200 → Batch ok.
- DB: SQLite (MVP), später PostgreSQL.
- Security: alles user-scoped.
- Delete: Regeln/Kategorien dürfen „das einfachere“ sein → Empfehlung: Softdelete (einheitlich).

---

## 10. Executable Specifications (Cucumber)

**Regel:** Jedes Gherkin-Szenario ist ein automatisierter Testfall (Cucumber).

- Feature Files: `src/test/resources/features/...`
- Step Definitions: `src/test/java/...`
- Testarten:
  - Controller/HTMX: MockMvc/WebTestClient (HTML fragment assertions)
  - Services (RuleEngine): Unit + Integration
  - Repository: Scoping, deleted_at-Filter

**DoD**
- Scenario grün + Migration vorhanden + UI zeigt Kategorie/Warnungen korrekt.

---

# 11. User Stories (Gherkin, Deutsch)

## EPIC: Kategorien

### Funktionalität: Default-Kategorien beim User anlegen
```gherkin
Funktionalität: Default-Kategorien beim User anlegen
  Als Benutzer
  möchte ich beim ersten Start ein Default-Set an Kategorien haben
  damit ich schnell kategorisieren kann

  Szenario: Default-Kategorien werden beim ersten Login erstellt
    Angenommen ich habe einen neuen Account erstellt
    Wenn ich mich zum ersten Mal einlogge
    Dann werden Default-Kategorien (Parent und Sub) für meinen Account angelegt
    Und es existiert eine Default-Kategorie "Sonstiges -> Unkategorisiert"
```

### Funktionalität: Eigene Kategorien anlegen (2-stufig)
```gherkin
Funktionalität: Eigene Kategorien anlegen (2-stufig)
  Als Benutzer
  möchte ich eigene Kategorien anlegen können
  damit ich meine Ausgaben passend strukturieren kann

  Szenario: Sub-Kategorie unter Parent anlegen
    Angenommen ich bin eingeloggt
    Und es existiert die Parent-Kategorie "Essen & Trinken"
    Wenn ich eine neue Sub-Kategorie "Bäckerei" unter "Essen & Trinken" anlege
    Dann ist die Sub-Kategorie "Bäckerei" verfügbar
    Und ich kann sie einer Buchung zuweisen
```

---

## EPIC: Regeln

### Funktionalität: Regeln anzeigen
```gherkin
Funktionalität: Regeln anzeigen
  Als Benutzer
  möchte ich meine Regeln in einer Liste sehen
  damit ich nachvollziehen kann, wie Buchungen kategorisiert werden

  Szenario: Regeln-Seite zeigt Spalten
    Angenommen ich bin eingeloggt
    Wenn ich die Regeln-Seite öffne
    Dann sehe ich eine Tabelle mit Spalten "Name", "Suchtext", "Feld", "Kategorie", "Aktiv", "Letzte Anwendung" und "Trefferanzahl"
```

### Funktionalität: Neue Regel erstellen
```gherkin
Funktionalität: Neue Regel erstellen
  Als Benutzer
  möchte ich eine neue Regel erstellen können
  damit passende Buchungen automatisch kategorisiert werden

  Szenario: Regel matcht im Auftraggebernamen
    Angenommen ich bin eingeloggt
    Und es existiert eine Sub-Kategorie "FastFood"
    Wenn ich eine Regel "McDonalds -> FastFood" mit Suchtext "mcdonalds" erstelle
    Und ich als Suchfeld "Auftraggeber" auswähle
    Dann wird die Regel gespeichert
    Und die Regel ist in der Regeln-Liste sichtbar

  Szenario: Regel matcht in Buchungstext und Auftraggeber
    Angenommen ich bin eingeloggt
    Und es existiert eine Sub-Kategorie "Sport"
    Wenn ich eine Regel "InterSport -> Sport" mit Suchtext "intersport" erstelle
    Und ich als Suchfeld "Beides" auswähle
    Dann wird die Regel gespeichert
```

### Funktionalität: Regel aktiv/inaktiv schalten
```gherkin
Funktionalität: Regel aktiv/inaktiv schalten
  Als Benutzer
  möchte ich eine Regel aktivieren oder deaktivieren können
  damit ich die automatische Kategorisierung steuern kann

  Szenario: Regel deaktivieren
    Angenommen ich bin eingeloggt
    Und eine Regel "McDonalds -> FastFood" ist aktiv
    Wenn ich den Aktiv-Toggle für diese Regel ausschalte
    Dann ist die Regel deaktiviert
    Und sie wird bei der Kategorisierung nicht berücksichtigt
```

### Funktionalität: Regelreihenfolge ändern (erste Regel gewinnt)
```gherkin
Funktionalität: Regelreihenfolge ändern (erste Regel gewinnt)
  Als Benutzer
  möchte ich Regeln nach oben/unten sortieren können
  damit bei Konflikten die richtige Regel gewinnt

  Szenario: Regel nach oben verschieben
    Angenommen ich bin eingeloggt
    Und es existieren mindestens zwei aktive Regeln
    Wenn ich bei einer Regel auf "Hoch" klicke
    Dann wird die Regel in der Reihenfolge nach oben verschoben
```

### Funktionalität: Regel erneut ausführen (nur diese Regel)
```gherkin
Funktionalität: Regel erneut ausführen (nur diese Regel)
  Als Benutzer
  möchte ich eine einzelne Regel erneut ausführen können
  damit ich Änderungen sofort auf bestehende Buchungen anwenden kann

  Szenario: Regel ausführen aktualisiert Trefferanzahl und letzte Anwendung
    Angenommen ich bin eingeloggt
    Und es existiert eine aktive Regel
    Wenn ich in der Regeln-Liste auf "Ausführen" klicke
    Dann wird die Regel auf bestehende Buchungen angewendet
    Und die Trefferanzahl der Regel wird aktualisiert
    Und das Feld "Letzte Anwendung" wird aktualisiert
```

### Funktionalität: Alle Regeln auf bestehende Buchungen anwenden (Backfill)
```gherkin
Funktionalität: Alle Regeln auf bestehende Buchungen anwenden (Backfill)
  Als Benutzer
  möchte ich alle Regeln auf bestehende Buchungen anwenden können
  damit ich nachträglich kategorisieren kann

  Szenario: Backfill wendet Regeln an
    Angenommen ich bin eingeloggt
    Und es existieren Buchungen ohne Kategorie oder mit Default-Kategorie
    Wenn ich auf der Regeln-Seite "Regeln anwenden" auslöse
    Dann werden Regeln auf bestehende Buchungen angewendet
    Und passende Buchungen erhalten Kategorien
```

---

## EPIC: Kategorisierung an Buchungen

### Funktionalität: Automatische Kategorisierung beim Import
```gherkin
Funktionalität: Automatische Kategorisierung beim Import
  Als Benutzer
  möchte ich, dass beim Import Regeln automatisch angewendet werden
  damit Buchungen sofort kategorisiert sind

  Szenario: Regel matcht und setzt Kategorie
    Angenommen ich bin eingeloggt
    Und eine aktive Regel matcht auf eine importierte Buchung
    Wenn neue Buchungen importiert werden
    Dann erhält die Buchung die Kategorie der ersten passenden Regel
    Und die Zuordnung ist als "RULE" markiert

  Szenario: Keine Regel matcht setzt Default-Kategorie
    Angenommen ich bin eingeloggt
    Und keine aktive Regel matcht auf eine importierte Buchung
    Wenn neue Buchungen importiert werden
    Dann erhält die Buchung die Default-Kategorie "Sonstiges -> Unkategorisiert"
    Und die Zuordnung ist als "DEFAULT" markiert
```

### Funktionalität: Warnhinweis bei Mehrfachtreffern
```gherkin
Funktionalität: Warnhinweis bei Mehrfachtreffern
  Als Benutzer
  möchte ich sehen, wenn mehrere Regeln auf eine Buchung passen
  damit ich Regelkonflikte verbessern kann

  Szenario: Mehrfachtreffer erzeugen Warnhinweis
    Angenommen ich bin eingeloggt
    Und mindestens zwei aktive Regeln matchen auf dieselbe Buchung
    Wenn ich die Transaktionstabelle öffne
    Dann sehe ich bei dieser Buchung einen Warnhinweis
    Und der Warnhinweis zeigt die Namen der weiteren passenden Regeln
```

### Funktionalität: Kategorie in Transaktionstabelle anzeigen
```gherkin
Funktionalität: Kategorie in Transaktionstabelle anzeigen
  Als Benutzer
  möchte ich in der Transaktionstabelle die Kategorie sehen
  damit ich Buchungen schnell prüfen kann

  Szenario: Kategorie-Spalte wird angezeigt
    Angenommen ich bin eingeloggt
    Wenn ich die Transaktionen-Seite öffne
    Dann sehe ich eine zusätzliche Spalte "Kategorie"
```

### Funktionalität: Roter Hinweis bei Default/Unkategorisiert + Filter
```gherkin
Funktionalität: Roter Hinweis bei Default/Unkategorisiert + Filter
  Als Benutzer
  möchte ich unzugeordnete Buchungen schnell finden
  damit ich Kategorien vervollständigen kann

  Szenario: Default-Kategorie wird rot markiert
    Angenommen ich bin eingeloggt
    Und eine Buchung hat die Default-Kategorie "Sonstiges -> Unkategorisiert"
    Wenn ich die Transaktionstabelle öffne
    Dann wird diese Kategorie rot markiert

  Szenario: Filter zeigt nur Default/Unkategorisiert
    Angenommen ich bin eingeloggt
    Und es existieren Buchungen mit und ohne Default-Kategorie
    Wenn ich den Filter "nur unzugeordnet" aktiviere
    Dann sehe ich nur Buchungen mit Default-Kategorie
```

### Funktionalität: Manuelle Kategorie setzen (Lock)
```gherkin
Funktionalität: Manuelle Kategorie setzen (Lock)
  Als Benutzer
  möchte ich eine Kategorie manuell setzen können
  damit ich Ausnahmen korrekt zuordne

  Szenario: Manuelle Kategorie wird gespeichert und gelockt
    Angenommen ich bin eingeloggt
    Und eine Buchung ist sichtbar
    Wenn ich für diese Buchung manuell eine Kategorie auswähle und speichere
    Dann wird die Kategorie gespeichert
    Und die Zuordnung ist als "MANUAL" markiert
    Und die Buchung ist gegen Regel-Überschreibung geschützt
```

---

## EPIC: Sankey Report

### Funktionalität: Sankey für letztes Kalenderjahr (Income/Expense -> Kategorie)
```gherkin
Funktionalität: Sankey für letztes Kalenderjahr (Income/Expense -> Kategorie)
  Als Benutzer
  möchte ich ein Sankey-Diagramm für das letzte Kalenderjahr sehen
  damit ich die Geldflüsse nach Kategorien verstehe

  Szenario: Sankey zeigt Flüsse für Einnahmen und Ausgaben
    Angenommen ich bin eingeloggt
    Und es existieren Buchungen im letzten Kalenderjahr
    Wenn ich die Sankey-Seite öffne
    Dann sehe ich Knoten "Income" und "Expense"
    Und ich sehe Flüsse von "Income" zu Kategorien für positive Beträge
    Und ich sehe Flüsse von "Expense" zu Kategorien für negative Beträge
```

---

## 12. Offene Punkte (bewusst klein gehalten)
1) Sankey-Knoten: Sollen Flüsse auf **Sub-Kategorien** (detailliert) oder nur auf **Parent-Kategorien** (übersichtlich) aggregiert werden?  
2) Löschen von Regeln/Kategorien: MVP-Empfehlung = Softdelete (einheitlich, auditierbar).

