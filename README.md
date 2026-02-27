# Finanztool

## Stand 2026-02-27

- Basis-Package wurde auf `de.kruemelnerd.finanzapp` konsolidiert (Main- und Test-Code).
- Maven-Koordinate wurde auf `de.kruemelnerd:finanzapp-mvp` angepasst.
- Cucumber-Glue nutzt jetzt das neue Package (`de.kruemelnerd.finanzapp.cucumber`).
- CSV-Import-Flashlogik wurde zentralisiert, damit `OverviewController` und `SettingsController` denselben Ablauf nutzen.
- Filter-Request-Parameter fuer Transaktionen wurden in ein gemeinsames Request-Objekt ausgelagert (`TransactionFilterRequest`).
- GitHub-Workflow fuer Qodana wurde hinzugefuegt (`.github/workflows/qodana_code_quality.yml`).

## Verifikation (Smoke)

- `mvn -DskipTests compile`
- `mvn -Dtest=CsvParserTest,CsvImportServiceTest,TransactionViewServiceTest test`
- `mvn -Dtest=CucumberTest -Dcucumber.filter.name="Logout ends the session" test`
- `mvn org.apache.maven.plugins:maven-pmd-plugin:3.22.0:cpd-check -DminimumTokens=100`
