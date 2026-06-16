# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added

- Qodana workflow for pull requests and pushes to `main` (`.github/workflows/qodana_code_quality.yml`).
- JReleaser-based release job in `.github/workflows/build.yml` that publishes the packaged jar automatically.
- Dependabot configuration for Maven and GitHub Actions updates (`.github/dependabot.yml`).
- `Semantic Version Bump` job in `.github/workflows/build.yml` that bumps `major` / `minor` / `patch` automatically on every push to `main`.
- Auto-merge workflow for Dependabot pull requests after successful `Build` checks (`.github/workflows/auto-merge-dependabot-prs.yml`).

### Changed

- Java and test package structure migrated from `com.example.finanzapp` to `de.kruemelnerd.finanzapp`.
- Maven `groupId` updated to `de.kruemelnerd`.
- Transaction filter parameters centralized in `TransactionFilterRequest` for controller endpoints.
- CSV import flash-message handling centralized in `CsvImportFlashService` and reused by overview/settings.
- GitHub upload guide extended with verification commands for default branch and workflow runs.
- Release creation is now gated by successful completion of the `Build and analyze` job on `main`.
- Release tags now use `release-<version>-<sha7>` to keep reruns idempotent per commit.
- Release job now skips direct release creation for non-bump pushes and waits for the generated version-bump commit.
- Release process now uses JReleaser end-to-end instead of `softprops/action-gh-release` and workflow artifact handoff.
- Build workflow no longer uploads release assets; it remains focused on CI and semantic version bumping.
- Build workflow permissions were scoped to job-level and JaCoCo XML coverage reporting was enabled for Sonar quality gate evaluation.
- Build workflow now waits for Sonar quality gate result before reporting success.
- Version bump level is now inferred from pushed commit messages (`major` for `BREAKING CHANGE` / `!`, `minor` for `feat`, otherwise `patch`).
- Release job now injects `JRELEASER_PROJECT_VERSION`, `JRELEASER_TAG_NAME`, and `JRELEASER_RELEASE_NAME`, while `jreleaser.yml` uses those injected values instead of unresolved hash templates.
- GitHub Actions pins were updated to Node24-ready revisions (`actions/checkout`, `actions/setup-java`, `actions/cache`, `actions/upload-artifact`) to remove Node20 deprecation warnings.
- Auto-merge workflow now supports an optional `AUTOMERGE_TOKEN` secret for Dependabot PRs that modify files under `.github/workflows/`.
- GitHub releases now use immutable JReleaser publishing with semantic tags `v<version>` instead of commit-hash-based `release-*` tags.

### Fixed

- Cucumber glue package updated to `de.kruemelnerd.finanzapp.cucumber` so `CucumberTest` can discover Spring context configuration again.
- Stabilized `PlaywrightE2ETest.realOverlapFixturesKeepExpectedLatestBalance` by removing brittle date-specific tooltip assertions that could fall outside the dynamic 30-day chart window.
