# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added

- Qodana workflow for pull requests and pushes to `main` (`.github/workflows/qodana_code_quality.yml`).
- GitHub Release workflow (`.github/workflows/release.yml`) that publishes the packaged jar as a release asset.
- SBOM generation via Syft in CycloneDX-JSON format and upload as additional release asset.
- Dependabot configuration for Maven and GitHub Actions updates (`.github/dependabot.yml`).
- `Dependabot Version Bump` job in `.github/workflows/build.yml` that increases the patch version after successful Dependabot update builds.

### Changed

- Java and test package structure migrated from `com.example.finanzapp` to `de.kruemelnerd.finanzapp`.
- Maven `groupId` updated to `de.kruemelnerd`.
- Transaction filter parameters centralized in `TransactionFilterRequest` for controller endpoints.
- CSV import flash-message handling centralized in `CsvImportFlashService` and reused by overview/settings.
- GitHub upload guide extended with verification commands for default branch and workflow runs.
- Release creation is now gated by successful completion of the `Build` workflow on `main`.
- Release tags now use `release-<version>-<sha7>` to keep reruns idempotent per commit.
- Release workflow now skips direct release creation for merged Dependabot commits and waits for the generated version-bump commit.
- Release action references are pinned to full commit SHAs.

### Fixed

- Cucumber glue package updated to `de.kruemelnerd.finanzapp.cucumber` so `CucumberTest` can discover Spring context configuration again.
- Stabilized `PlaywrightE2ETest.realOverlapFixturesKeepExpectedLatestBalance` by removing brittle date-specific tooltip assertions that could fall outside the dynamic 30-day chart window.
