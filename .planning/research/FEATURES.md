# Feature Research

**Domain:** Personal Finance Web-App (Finanztool, Folge-Milestone)
**Researched:** 2026-02-22
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Multi-Account-Baseline (pro Nutzer mehrere Konten, klare Konto-Zuordnung je Transaktion) | Realistische Privatfinanzen laufen ueber Giro, Kreditkarte, Tagesgeld; Ein-Konto-Modell wirkt sofort unvollstaendig | HIGH | Erfordert DB-Migrationen (Account-Entity + FK), account-aware Queries, Scope in Dashboard/Transactions/Reports |
| Konto-spezifische Uebersicht und Filterung (Balance, Verlauf, Transaktionsliste) | Nutzer erwarten, dass Salden/Listen pro Konto und optional aggregiert korrekt sind | MEDIUM | UI-Filter + Service/Repository-Filter; vermeidet falsche Summen durch implizite Mischdaten |
| Bulk-Transaktions-Workflows (Multi-Select + Sammelaktionen) | Nach CSV-Importen sind Massenkorrekturen normal; Row-by-row ist fuer ernsthafte Nutzung zu langsam | HIGH | Passt zu bekanntem Pain Point im Bestand; braucht sichere Selektion, PRG-Feedback und Undo-freundliche Flows |
| Konsistente Aktionsbedienung fuer Regeln/Transaktionen (kontextuelles Aktionsmenue) | Uneinheitliche Interaktion zwischen Listen wird als inkonsistent wahrgenommen | MEDIUM | Schafft einheitliches Bedienmodell mit bestehender HTMX/Thymeleaf-Architektur |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Rule-from-Transaction Flow (direkt aus Selektion Regel erstellen/erweitern) | Verkuerzt den Weg von "Fehler gesehen" zu "Dauerhaft geloest" auf einen Flow | MEDIUM | Nutzt bestehende Rule-Engine; staerkt Core Value "zuverlaessig kategorisieren" |
| Regel-Portabilitaet mit Versionierung (Export/Import inkl. Validierung und Konflikthinweisen) | Macht Setup wiederverwendbar (Backup, Migration, geteilte Haushalts-Setups) | MEDIUM | Bestehende Transfer-Basis vorhanden; jetzt als produktreifer, sicherer Standard ausbauen |
| Account-scoped + globale Regeln (Prioritaetsmodell) | Ermoeglicht praezise Automatisierung ohne Duplikate bei mehreren Konten | HIGH | Braucht klares Auswertungsmodell (zuerst konto-spezifisch, dann global) und transparente Erklaerbarkeit |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Vollautomatische ML-Kategorisierung ohne explizite Regeln | Klingt "smart" und bequem | Widerspricht Nachvollziehbarkeit, erschwert Debugging und passt nicht zu aktuellem Produktfokus | Deterministische Regeln + bessere Rule-from-Transaction UX + Vorschau vor Anwenden |
| Globales Bulk-Editing ohne Konto-/Filter-Guardrails | Wirkt schneller fuer Power-User | Erhoeht Risiko von Massenfehlern ueber mehrere Konten, schwer rueckgaengig zu machen | Bulk nur im klaren Scope (Filter/Account sichtbar), mit Confirm + Undo-Strategie |
| Regel-Sync-Cloud/Marketplace in diesem Milestone | Klingt attraktiv fuer Wiederverwendung | Hoher Security/Privacy/Operations-Aufwand, entkoppelt von aktuellem Ziel "lokale Workflow-Verbesserung" | Datei-basierter Import/Export mit Versionsschema und Integritaetschecks |

## Feature Dependencies

```text
Multi-Account-Datenmodell
    -> Konto-spezifische Queries/Filter
        -> Konto-spezifische Dashboards/Reports
            -> Sicheres Bulk-Editing im Konto-Scope

Konsistentes Aktionsmenue
    -> Rule-from-Transaction Flow

Versionsschema fuer Rule-Export/Import
    -> Rueckwaertskompatible Regel-Portabilitaet

Account-scoped Regelprioritaet
    -> Korrekte Auto-Kategorisierung bei Multi-Account
```

### Dependency Notes

- **Multi-Account-Datenmodell vor UI-Features:** Ohne persistente Konto-Zuordnung sind Balance-, Filter- und Bulk-Workflows fachlich nicht korrekt.
- **Bulk-UX braucht Query-seitige Filterung:** Wegen bekannter In-Memory-Engpaesse sollten grosse Listenoperationen nicht nur im Web-Layer entstehen.
- **Rule-Portabilitaet braucht stabiles Versionsschema:** Sonst brechen spaetere Schema-Aenderungen Importfaehigkeit und Vertrauen.

## MVP Definition

### Launch With (v1)

Minimum viable product for this milestone.

- [ ] Multi-Account-Basis inkl. Kontoanlage und Konto-Zuordnung fuer Transaktionen
- [ ] Konto-spezifische Anzeige in Uebersicht + Transaktionen (inkl. aggregierter Gesamtansicht)
- [ ] Multi-Select + 2-3 Sammelaktionen mit Sicherheitsleitplanken (z. B. Kategorie setzen, soft-delete)
- [ ] Direktfluss "Regel aus Transaktion" (neu erstellen + bestehende Regel erweitern)
- [ ] Regel-Export/Import als versioniertes JSON mit Validierungsfehlern fuer Nutzer verstaendlich

### Add After Validation (v1.x)

- [ ] Account-scoped vs globale Regelprioritaet mit UI-Erklaerung pro Treffer
- [ ] Erweiterte Bulk-Aktionen (Massen-Rekategorisierung mit Vorschau, kontoweises Rueckgaengig)

### Future Consideration (v2+)

- [ ] Teilautomatische Regelvorschlaege (ohne autonome ML-Entscheidung)
- [ ] Optionaler Sync zwischen Instanzen (nur nach Security- und Betriebskonzept)

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Multi-Account-Datenmodell + Konto-Scope im Lesen | HIGH | HIGH | P1 |
| Bulk-Transaktions-Workflows (sicher, scoped) | HIGH | HIGH | P1 |
| Rule-from-Transaction Flow | HIGH | MEDIUM | P1 |
| Regel-Export/Import robust versioniert | MEDIUM-HIGH | MEDIUM | P1 |
| Account-scoped + globale Regelprioritaet | HIGH | HIGH | P2 |
| Erweiterte Bulk-Operationen mit Vorschau/Undo | MEDIUM | MEDIUM-HIGH | P2 |

**Priority key:**
- P1: Muss in diesem Milestone geliefert werden
- P2: Sollte nach Stabilisierung folgen
- P3: Spaeter / optional

## Competitor Feature Analysis

| Feature | Competitor A | Competitor B | Our Approach |
|---------|--------------|--------------|--------------|
| Multi-Account-Trennung | Standard bei etablierten Budget/Finance-Tools | Standard bei Self-Hosted Finance-Tools | Fokus auf korrekte Konto-Semantik im bestehenden monolithischen Datenmodell |
| Bulk-Transaktionsbearbeitung | Oft als Batch-Edit vorhanden | Teilweise eingeschraenkt, aber vorhanden | HTMX-kompatible Sammelaktionen mit klaren Scope-Grenzen und Soft-Delete-Sicherheit |
| Regel-Portabilitaet | Teilweise CSV/JSON Export, oft proprietaer | Haeufig importierbare Konfigurationen | Offenes, versioniertes JSON-Format fuer langfristige Lesbarkeit und Migration |

## Sources

- `.planning/PROJECT.md`
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/CONCERNS.md`
- Produktkontext aus aktuellem Milestone-Scope (Multi-Account, Workflow-UX, Rule-Portabilitaet)

---
*Feature research for: Finanztool (subsequent milestone)*
*Researched: 2026-02-22*
