# External Integrations

**Analysis Date:** 2026-02-22

## APIs & External Services

**Frontend CDN Assets:**
- Google Fonts - Webfont delivery for UI typography loaded in `src/main/resources/templates/layouts/base.html` and `src/main/resources/templates/login.html`.
  - SDK/Client: Browser `<link>` to `https://fonts.googleapis.com` and `https://fonts.gstatic.com`.
  - Auth: Not applicable.
- HTMX CDN - Client-side progressive enhancement script loaded in `src/main/resources/templates/layouts/base.html`.
  - SDK/Client: Browser `<script src="https://unpkg.com/htmx.org@1.9.12">`.
  - Auth: Not applicable.
- Google Charts Loader - Sankey chart runtime loaded in `src/main/resources/templates/reports-sankey.html`.
  - SDK/Client: Browser `<script src="https://www.gstatic.com/charts/loader.js">`.
  - Auth: Not applicable.

**Backend External APIs:**
- Not detected in server code (`src/main/java/` contains no HTTP client SDK imports such as RestTemplate/WebClient/OkHttp).
  - SDK/Client: Not applicable.
  - Auth: Not applicable.

## Data Storage

**Databases:**
- SQLite (local file-backed)
  - Connection: Spring property `spring.datasource.url` in `src/main/resources/application.properties` (test override in `src/test/resources/application.properties`).
  - Client: Spring Data JPA repositories in `src/main/java/com/example/finanzapp/repository/` with SQLite JDBC dependency in `pom.xml`.

**File Storage:**
- Local filesystem only (uploaded CSV payloads are processed in-memory by `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`; no cloud object store SDK detected).

**Caching:**
- None (no Redis/Caffeine/Hazelcast integration detected in `pom.xml` or `src/main/java/`).

## Authentication & Identity

**Auth Provider:**
- Custom, database-backed authentication with Spring Security.
  - Implementation: Form login and access rules in `src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`; user lookup via `src/main/java/com/example/finanzapp/auth/DatabaseUserDetailsService.java`; credentials persisted through `src/main/java/com/example/finanzapp/repository/UserRepository.java`.

## Monitoring & Observability

**Error Tracking:**
- None (no Sentry/New Relic/Datadog SDK dependencies detected in `pom.xml`).

**Logs:**
- Application logging via SLF4J (example: `LoggerFactory` usage in `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`).

## CI/CD & Deployment

**Hosting:**
- Not explicitly configured in repository files (no Dockerfile, Procfile, or platform manifests detected at project root).

**CI Pipeline:**
- None detected (`.github/workflows/` not present).

## Environment Configuration

**Required env vars:**
- Not required by current implementation; configuration is property-file based in `src/main/resources/application.properties` and `src/test/resources/application.properties`.

**Secrets location:**
- No dedicated secrets file integration detected; `.env` files are not present in repository root.

## Webhooks & Callbacks

**Incoming:**
- None (no webhook/callback endpoints detected in `src/main/java/`; controllers expose app routes such as `src/main/java/com/example/finanzapp/reports/SankeyController.java` and `src/main/java/com/example/finanzapp/settings/SettingsController.java`).

**Outgoing:**
- Browser-only outbound requests to third-party CDNs from templates in `src/main/resources/templates/layouts/base.html` and `src/main/resources/templates/reports-sankey.html`.
- Browser fetch to internal API endpoint `/api/reports/sankey` in `src/main/resources/templates/reports-sankey.html` (internal callback, not external service).

---

*Integration audit: 2026-02-22*
