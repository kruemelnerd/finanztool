---
name: code-review-release-guard
description: Automatischer Code-Review mit Fehler- und Duplikatpruefung, README/CHANGELOG-Sync und verpflichtendem Freigabe-Gate
---
# code-review-release-guard

Skill fuer einen kontrollierten Review- und Doku-Workflow mit klarer Genehmigung vor der Anwendung von Fixes.

## Ziel

- Code auf Fehler, Risiken und doppelten Code pruefen
- Konkrete Korrekturen vorschlagen und (nach Freigabe) anwenden
- `README.md` und `CHANGELOG.md` auf Stand bringen
- Alles in einem Review-Bundle dokumentieren
- Nur nach expliziter Freigabe finalisieren

## Wann dieser Skill verwendet wird

Nutze diesen Skill, wenn der User Dinge sagt wie:

- "Review meinen Code"
- "Suche Bugs oder doppelten Code"
- "Aktualisiere README/Changelog"
- "Lege mir alles zur Freigabe vor"

## Guardrails (verpflichtend)

1. **Zuerst lesen, dann handeln**: Erst Analyse + Report, dann Vorschlaege.
2. **Keine stillen Gross-Refactorings**: Nur minimale, sichere Fixes.
3. **Freigabe-Gate**: Ohne explizites "genehmigt" keine finalen Aenderungen/Commits.
4. **Diff-fokussiert**: Primaer geaenderte Bereiche + direkte Nachbarschaft bewerten.
5. **Transparenz**: Jeden Fix mit Grund und Risiko einstufen (low/medium/high).

## Workflow

### Phase 1 - Scope und Basis-Checks (read-first)

1. Arbeitsstand erfassen:

```bash
git status --short
git diff --stat
git diff --name-only
git diff --staged --name-only
```

2. Review-Bundle erzeugen (automatisiert):

```bash
python3 .opencode/skills/code-review-release-guard/scripts/review_bundle.py \
  --base-branch main \
  --output-dir .opencode/review/latest \
  --smoke "mvn -DskipTests compile" \
  --smoke "mvn -Dtest=CsvParserTest,CsvImportServiceTest,TransactionViewServiceTest test" \
  --draft-docs
```

3. Optional Duplikatcheck via PMD-CPD:

```bash
mvn org.apache.maven.plugins:maven-pmd-plugin:3.22.0:cpd-check -DminimumTokens=100
```

### Phase 2 - Befunde strukturieren

Erstelle eine priorisierte Liste:

- **Blocker**: Build/Test bricht, offensichtliche Defects
- **Wichtig**: Logikfehler, Null/Edge-Case-Risiken, Security-Risiken
- **Mittel**: Duplikate, technische Schulden, schwache Benennung
- **Niedrig**: Stil, Lesbarkeit, kleine Cleanups

Pro Befund dokumentieren:

- Datei + Stelle
- Risiko
- Korrekturidee
- Ob automatischer Fix sicher ist

### Phase 3 - Fix-Vorschlag und Doku-Sync

1. Fixes als Patch-Vorschlag vorbereiten.
2. `README.md` pruefen und neue/veraenderte Funktionalitaet aufnehmen.
3. `CHANGELOG.md` sicherstellen:
   - Falls nicht vorhanden: neu anlegen (Keep a Changelog Struktur)
   - Immer unter `## [Unreleased]` einordnen
4. Draft-Dateien aus `.opencode/review/latest/` nutzen und konkretisieren.

### Phase 4 - Freigabe-Gate

Dem User immer zuerst vorlegen:

- `.opencode/review/latest/review-report.md`
- `.opencode/review/latest/README_UPDATE_DRAFT.md`
- `.opencode/review/latest/CHANGELOG_DRAFT.md`

Freigabe-Formulierung (Beispiel):

`Genehmigt: Fixes + README + CHANGELOG wie vorgeschlagen umsetzen.`

### Phase 5 - Anwendung nach Freigabe

1. Erst jetzt Patches/Fixes anwenden.
2. Relevante Smoke-Tests erneut laufen lassen.
3. Ergebnis zusammenfassen (inkl. verbleibende Risiken).
4. Optional Commit erstellen (wenn gewuenscht).

## Erwartetes Ergebnisformat an den User

Kurz und pruefbar:

1. Wichtigste Befunde (max 5)
2. Geplante Fixes
3. README-Aenderungen
4. CHANGELOG-Eintrag
5. Offene Punkte + Freigabeaufforderung

## Sinnvolle Erweiterungen

- Security-Scan: `mvn org.owasp:dependency-check-maven:check`
- API-Kompatibilitaet (wenn oeffentliche API): semantische Diff-Pruefung
- Architekturregeln (optional): ArchUnit
- Duplicate-Hotspots als KPI ueber Zeit in JSON speichern

## Dateien in diesem Skill

- `scripts/review_bundle.py` - erzeugt Review-Bundle inkl. Duplikatindikatoren
- `templates/REVIEW_BUNDLE_TEMPLATE.md` - Vorlage fuer manuelle Ergaenzungen
- `templates/CHANGELOG_TEMPLATE.md` - Startvorlage fuer neues `CHANGELOG.md`
- `templates/APPROVAL_TEMPLATE.md` - Freigabe-Textbaustein
