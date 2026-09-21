# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added

- Qodana workflow for pull requests and pushes to `main` (`.github/workflows/qodana_code_quality.yml`).
- JReleaser-based release job in `.github/workflows/build.yml` that publishes the packaged jar automatically.
- Renovate configuration for Maven and GitHub Actions updates (`.github/renovate.json`).
- Auto-merge workflow for Renovate pull requests and release-bump PRs after successful `Build` checks (`.github/workflows/auto-merge-renovate-prs.yml`).
- Automatic patch-bump release PRs for merged Renovate updates.

### Changed

- Java and test package structure migrated from `com.example.finanzapp` to `de.kruemelnerd.finanzapp`.
- Maven `groupId` updated to `de.kruemelnerd`.
- Transaction filter parameters centralized in `TransactionFilterRequest` for controller endpoints.
- CSV import flash-message handling centralized in `CsvImportFlashService` and reused by overview/settings.
- GitHub upload guide extended with verification commands for default branch and workflow runs.
- Release creation now happens only after a Renovate-driven patch-bump commit on `main`.
- Release tags use immutable semantic tags `v<version>` instead of commit-hash-based `release-*` tags.
- Build workflow now creates a patch-bump PR after automerged Renovate updates and skips releases for unrelated main pushes.
- Release job now publishes only from the generated patch-bump commit and no longer infers major/minor/patch levels from arbitrary commits.
- Release process now uses JReleaser end-to-end instead of `softprops/action-gh-release` and workflow artifact handoff.
- Build workflow permissions were scoped to job-level and JaCoCo XML coverage reporting was enabled for Sonar quality gate evaluation.
- Build workflow now waits for Sonar quality gate result before reporting success.
- Release job injects `JRELEASER_PROJECT_VERSION`, `JRELEASER_TAG_NAME`, and `JRELEASER_RELEASE_NAME`, while `jreleaser.yml` uses those injected values instead of unresolved hash templates.
- GitHub Actions pins were updated to Node24-ready revisions (`actions/checkout`, `actions/setup-java`, `actions/cache`, `actions/upload-artifact`) to remove Node20 deprecation warnings.
- Auto-merge workflow now handles Renovate pull requests and release-bump pull requests after successful `Build` checks.
- Dependency automation now uses Renovate App updates that age for 10 days before merge and only auto-merge patch/minor updates.

### Fixed

- Cucumber glue package updated to `de.kruemelnerd.finanzapp.cucumber` so `CucumberTest` can discover Spring context configuration again.
- Stabilized `PlaywrightE2ETest.realOverlapFixturesKeepExpectedLatestBalance` by removing brittle date-specific tooltip assertions that could fall outside the dynamic 30-day chart window.
