# Stack Research

**Domain:** Personal finance web app (Spring Boot, server-rendered)
**Researched:** 2026-02-22
**Confidence:** MEDIUM-HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java + Spring Boot | Java 21 (runtime), Spring Boot 3.5.x now; plan Boot 4.x later | Main application platform | Safest path from current 3.4.2: move to the current 3.5 line first, keep Jakarta/Spring upgrade risk low, and only then schedule Boot 4 migration once multi-account + DB migration are stable. | 
| Spring MVC + Thymeleaf + HTMX | Spring MVC/Thymeleaf via Boot BOM; HTMX 2.0.x | Server-rendered UX with partial updates | Fits existing architecture and team velocity. For transaction UX improvements (bulk actions, inline edits, contextual menus), HTMX extends current pattern without SPA rewrite risk. |
| Spring Data JPA + Hibernate | Boot-managed (Hibernate 6.x line) | Persistence and transactional domain logic | Keeps existing repository/service model intact while enabling incremental schema evolution for multi-account and export/import metadata. |
| Flyway | 11.x line (current: 11.8.2) | Schema and data migrations | Critical for safe rollout: account scoping, rule portability tables, and SQLite->PostgreSQL migration all need deterministic, replayable migrations. |
| Database Strategy | Keep SQLite for local/small single-node; add PostgreSQL 18 as scale target | Durability and scale progression | SQLite remains fine for local and low write concurrency, but PostgreSQL is the right default for multi-user concurrency and growth beyond single-writer limits. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.postgresql:postgresql` | 42.7.x (current: 42.7.7) | PostgreSQL JDBC driver | Add once staging/prod move off SQLite begins; keep local dev profile on SQLite initially if desired. |
| `org.testcontainers:testcontainers-bom` + `postgresql` module | 2.0.x (current: 2.0.2) | Real DB integration tests | Mandatory before and during DB migration: run repository + migration tests against real PostgreSQL in CI. |
| `org.springframework.boot:spring-boot-starter-actuator` + Micrometer/Prometheus registry | Boot-managed (Micrometer via Boot) | Metrics, health, migration confidence | Enable before major data changes to observe query latency, error rates, and import throughput regressions. |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.x (current: 3.2.0) | Local read caching for hot lookup paths | Use selectively for account-scoped rule/category lookup hotspots after measuring bottlenecks; avoid broad cache-first design. |
| `com.networknt:json-schema-validator` | 1.5.x (current: 1.5.6) | Rule export/import contract validation | Use for versioned JSON export payload validation to prevent broken imports and support forward-compatible schema changes. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| OpenRewrite Maven Plugin (`org.openrewrite.maven:rewrite-maven-plugin`) | Controlled framework upgrades | Use recipes for Spring Boot minor/major upgrades to reduce manual refactor risk and catch deprecated API usage early. |
| ArchUnit (`com.tngtech.archunit:archunit-junit5`) | Enforce monolith boundaries | Add rules for feature boundaries and forbidden cross-package dependencies while adding multi-account complexity. |
| Playwright for Java | End-to-end UX regression coverage | Keep and upgrade from current 1.47.0 toward current 1.58.x line during UX-heavy milestones. |

## Installation

```bash
# Core platform upgrade path (safe baseline first)
mvn -DskipTests versions:set-property -Dproperty=spring-boot.version -DnewVersion=3.5.11

# DB scale target + observability + validation
mvn dependency:get -Dartifact=org.postgresql:postgresql:42.7.7
mvn dependency:get -Dartifact=org.flywaydb:flyway-core:11.8.2
mvn dependency:get -Dartifact=com.networknt:json-schema-validator:1.5.6

# Test infrastructure for migration safety
mvn dependency:get -Dartifact=org.testcontainers:testcontainers:2.0.2
mvn dependency:get -Dartifact=org.testcontainers:postgresql:2.0.2
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Spring Boot 3.5.x now, 4.x later | Immediate jump to Boot 4.0.x | Use only if you explicitly prioritize platform modernization over delivery risk in upcoming milestones. |
| PostgreSQL as scale target | MariaDB/MySQL | Reasonable if ops standardizes on MySQL, but PostgreSQL is generally stronger for advanced indexing and long-term analytics flexibility. |
| Server-rendered Thymeleaf + HTMX | SPA rewrite (React/Vue) | Use only if product direction changes to highly interactive offline-first client app; current roadmap does not require this cost. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Immediate microservice split | Adds operational complexity before domain boundaries are mature; slows delivery of current milestone goals | Keep modular monolith, enforce boundaries with ArchUnit, extract later only when scaling evidence exists |
| SQLite as long-term shared production DB for growth phase | Single-writer model and file-locking/network limitations become risk at higher write concurrency | PostgreSQL 18 for staging/prod scale |
| R2DBC migration for this codebase now | Current stack is JPA + transactional service logic; reactive rewrite adds complexity without roadmap payoff | Keep JDBC/JPA and optimize queries/indexes first |
| Big-bang SPA frontend rewrite | High regression risk for existing flows and i18n; delays UX improvements that are already possible with HTMX | Incremental HTMX/Thymeleaf UX refactors |

## Stack Patterns by Variant

**If deployment remains single-user or low-write self-hosted:**
- Keep SQLite + current MVC stack
- Because simplicity and low ops burden outweigh scale features

**If moving to team/shared usage or higher import concurrency:**
- Move runtime DB to PostgreSQL, keep app monolith/server-rendered
- Because this removes core DB bottlenecks without architectural churn

**If export/import becomes cross-instance product surface:**
- Define versioned JSON schema + migration adapters + signature/hash metadata
- Because compatibility guarantees matter more than raw feature speed

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `spring-boot:3.5.x` | Java 17-25 (run on Java 21 recommended) | Lowest-risk near-term upgrade from current 3.4.x |
| `spring-boot:4.0.x` | Java 17-25, Spring Framework 7.x | Plan as explicit milestone after core feature work stabilizes |
| `flyway-core:11.x` | SQLite + PostgreSQL | Keep SQL migrations portable; isolate DB-specific SQL when needed |
| `testcontainers:2.0.x` | JUnit 5 + Docker runtime | Use in CI for migration and repository confidence |
| `playwright:1.58.x` | Java 8+ | Upgrade from 1.47.0 to reduce browser drift in E2E tests |

## Recommendation Confidence

| Recommendation | Confidence | Why |
|----------------|------------|-----|
| Upgrade to Spring Boot 3.5.x first, defer 4.x | HIGH | Backed by current official Spring docs showing both 3.5.x and 4.0.x stable lines; phased upgrade minimizes risk in brownfield app |
| Adopt PostgreSQL as scale target while keeping SQLite for local/small installs | HIGH | SQLite official guidance highlights single-writer/network constraints; PostgreSQL current major release and ecosystem maturity support growth |
| Keep server-rendered + HTMX approach for upcoming UX scope | MEDIUM-HIGH | Strong fit with existing architecture and milestone goals; avoids unnecessary SPA rewrite risk |
| Introduce JSON schema validation for rule export/import contracts | MEDIUM | Industry-standard approach for compatibility, with mature validator options; exact payload evolution details still project-specific |
| Add ArchUnit/OpenRewrite for safer evolution | MEDIUM | Widely used modernization guardrails; benefit depends on disciplined adoption in CI |

## Sources

- Spring Boot project page (shows stable 4.0.3) — https://spring.io/projects/spring-boot
- Spring Boot 3.5 system requirements (shows 3.5.11 line and compatibility notes) — https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Spring Boot 4.0 system requirements (Java/Spring Framework baseline) — https://docs.spring.io/spring-boot/system-requirements.html
- Spring Initializr metadata (available Boot and Java lines) — https://start.spring.io/metadata/client
- SQLite "Appropriate Uses" (single-writer and client/server guidance) — https://www.sqlite.org/whentouse.html
- PostgreSQL current release docs (current major line 18) — https://www.postgresql.org/docs/current/release.html
- Testcontainers for Java docs (current dependency line 2.0.2) — https://java.testcontainers.org/
- Playwright for Java installation docs (example dependency 1.58.0) — https://playwright.dev/java/docs/intro
- HTMX home/docs (2.0.8 snippet and 2.x guidance) — https://htmx.org/
- Maven Central API (latest artifact lines: Flyway, PostgreSQL JDBC, Caffeine, ArchUnit, json-schema-validator, Spring Modulith, SQLite JDBC, Commons CSV) — https://search.maven.org/

---
*Stack research for: personal finance web app (Finanztool)*
*Researched: 2026-02-22*
