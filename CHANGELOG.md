# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added

- Qodana workflow for pull requests and pushes to `main` (`.github/workflows/qodana_code_quality.yml`).
- JReleaser-based release job in `.github/workflows/build.yml` that publishes the packaged jar automatically.
- Dependabot configuration for Maven and GitHub Actions updates (`.github/dependabot.yml`).
- `Dependabot Version Bump` job in `.github/workflows/build.yml` that increases the patch version after successful Dependabot update builds.
- Auto-merge workflow for Dependabot pull requests after successful `Build` checks (`.github/workflows/auto-merge-dependabot-prs.yml`).

### Changed

- Java and test package structure migrated from `com.example.finanzapp` to `de.kruemelnerd.finanzapp`.
- Maven `groupId` updated to `de.kruemelnerd`.
- Transaction filter parameters centralized in `TransactionFilterRequest` for controller endpoints.
- CSV import flash-message handling centralized in `CsvImportFlashService` and reused by overview/settings.
- GitHub upload guide extended with verification commands for default branch and workflow runs.
- Release creation is now gated by successful completion of the `Build and analyze` job on `main`.
- Release tags now use `release-<version>-<sha7>` to keep reruns idempotent per commit.
- Release job now skips direct release creation for merged Dependabot commits and waits for the generated version-bump commit.
- Release process now uses JReleaser end-to-end instead of `softprops/action-gh-release` and workflow artifact handoff.
- Build workflow no longer uploads release assets; it remains focused on CI and Dependabot version bumping.
- Build workflow permissions were scoped to job-level and JaCoCo XML coverage reporting was enabled for Sonar quality gate evaluation.
- Build workflow now waits for Sonar quality gate result before reporting success.
- Release job now injects `JRELEASER_PROJECT_VERSION`, `JRELEASER_TAG_NAME`, and `JRELEASER_RELEASE_NAME`, while `jreleaser.yml` uses those injected values instead of unresolved hash templates.
- Auto-merge workflow now supports an optional `AUTOMERGE_TOKEN` secret for Dependabot PRs that modify files under `.github/workflows/`.

### Fixed

- Cucumber glue package updated to `de.kruemelnerd.finanzapp.cucumber` so `CucumberTest` can discover Spring context configuration again.
- Stabilized `PlaywrightE2ETest.realOverlapFixturesKeepExpectedLatestBalance` by removing brittle date-specific tooltip assertions that could fall outside the dynamic 30-day chart window.
