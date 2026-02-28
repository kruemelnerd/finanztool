# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added

- Qodana workflow for pull requests and pushes to `main` (`.github/workflows/qodana_code_quality.yml`).

### Changed

- Java and test package structure migrated from `com.example.finanzapp` to `de.kruemelnerd.finanzapp`.
- Maven `groupId` updated to `de.kruemelnerd`.
- Transaction filter parameters centralized in `TransactionFilterRequest` for controller endpoints.
- CSV import flash-message handling centralized in `CsvImportFlashService` and reused by overview/settings.
- GitHub upload guide extended with verification commands for default branch and workflow runs.

### Fixed

- Cucumber glue package updated to `de.kruemelnerd.finanzapp.cucumber` so `CucumberTest` can discover Spring context configuration again.
- Stabilized `PlaywrightE2ETest.realOverlapFixturesKeepExpectedLatestBalance` by removing brittle date-specific tooltip assertions that could fall outside the dynamic 30-day chart window.
