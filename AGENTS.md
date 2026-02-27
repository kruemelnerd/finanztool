# AGENTS.md
Guidance for coding agents working in this repository.

## Scope and project root
- Repository root: `/home/philipp/Dokumente/code/finanztool`
- Main application module: repository root (`.`)
- Unless explicitly stated otherwise, run Java/Maven commands in repository root.

## Rule sources checked
- Checked `.cursor/rules/`: not present.
- Checked `.cursorrules`: not present.
- Checked `.github/copilot-instructions.md`: not present.
- This file is therefore the primary agent instruction source in this repo.
- If those files are added later, treat them as additional constraints.

## Build, lint, and test commands
All commands below assume current directory is repository root.

### Build and run
- Build jar (skip tests): `mvn -DskipTests package`
- Full build (with tests): `mvn package`
- Run app in dev mode: `mvn spring-boot:run`
- Run packaged jar: `java -jar target/finanzapp-mvp-0.0.1-SNAPSHOT.jar`

### Lint/format
- No dedicated linter/formatter plugin is configured (no Checkstyle/Spotless in repo).
- Use compile + tests as quality gate.
- Compile-only check: `mvn -DskipTests compile`
- Full verification: `mvn test`

### Test suites
- Run all tests: `mvn test`
- Run selected classes:
- `mvn -Dtest=CsvParserTest,CsvImportServiceTest,TransactionViewServiceTest test`
- Run repository integration test set:
- `mvn -Dtest=UserRepositoryIntegrationTest,TransactionRepositoryIntegrationTest,CsvArtifactRepositoryIntegrationTest,BalanceDailyRepositoryIntegrationTest test`

### Run a single test (important)
- Single test class: `mvn -Dtest=CsvParserTest test`
- Single test method: `mvn -Dtest=CsvParserTest#parseFailsWhenHeaderMissing test`
- Single Playwright class: `mvn -Dtest=PlaywrightE2ETest test`
- Single Playwright method:
- `mvn -Dtest=PlaywrightE2ETest#loginFlowRendersOverview test`

### Cucumber execution
- Run full Cucumber suite via JUnit platform runner:
- `mvn -Dtest=CucumberTest test`
- Run one scenario by name:
- `mvn -Dtest=CucumberTest -Dcucumber.filter.name="Logout ends the session" test`
- Run by tag (UI flows):
- `mvn -Dtest=CucumberTest -Dcucumber.filter.tags="@ui" test`

### Running from repository root
- From `/home/philipp/Dokumente/code/finanztool`, run Maven commands directly:
- `mvn test`
- `mvn -Dtest=CsvParserTest#parseFailsWhenHeaderMissing test`

## Code organization conventions
- Package base: `de.kruemelnerd.finanzapp`.
- Organize by feature (`auth`, `dashboard`, `transactions`, `importcsv`, `settings`, etc.).
- Keep controllers thin; put business logic in services.
- Keep persistence logic in repositories.
- Prefer small immutable DTO/result carriers via `record`.

## Java style guidelines

### Formatting
- Use 2-space indentation (matches current Java files).
- Use K&R braces (`if (...) {` on the same line).
- Keep methods focused; extract helpers when logic grows.
- Prefer guard clauses for invalid/null/blank input.
- Avoid comments unless logic is genuinely non-obvious.

### Imports
- Do not use wildcard imports.
- Prefer explicit imports.
- In tests, static imports are common for assertions and MockMvc builders.
- Remove unused imports and keep import blocks consistent.

### Types and data modeling
- Store monetary values as integer cents (`long`), never floating point.
- Use `BigDecimal` only at boundaries (request params/parsing), then convert to cents.
- Use `LocalDateTime` for booking timestamps, `LocalDate` for value dates.
- Use `Instant` for audit/deletion timestamps.
- Use `Optional<T>` for repository lookups where absence is expected.
- Prefer immutable empty values (`List.of()`) over `null` collections.

### Naming
- Classes/interfaces: PascalCase.
- Methods/fields/parameters: lowerCamelCase.
- Constants: UPPER_SNAKE_CASE.
- Controllers end with `Controller`.
- Services end with `Service`.
- Repositories end with `Repository`.
- Test classes end with `Test` or `IntegrationTest` / `E2ETest`.
- Test methods should be descriptive behavior phrases (e.g. `parseHandlesUtf8BomAtFileStart`).

## Spring and web conventions
- Use constructor injection only.
- Put `@Transactional` on state-changing service methods.
- Set `pageTitle` model attributes for rendered pages.
- Use flash attributes for post-redirect-get user feedback.
- Keep security strict by default: authenticated routes unless explicitly public.

## Error handling and logging
- Use domain-specific runtime exceptions for domain failures (e.g. `CsvImportException`).
- Validate early and fail with clear messages.
- Catch low-level exceptions where context can be added, then rethrow domain exceptions.
- Do not swallow exceptions unless a deliberate fallback is required.
- Log actionable context (user/file/size), but do not log sensitive payload content.

## Persistence and migration rules
- Database is SQLite; schema changes are managed by Flyway.
- SQL migrations: `src/main/resources/db/migration/` named `V{N}__description.sql`.
- Java migrations: `src/main/java/db/migration/` named `V{N}__Description.java`.
- Never edit old applied migrations; always add a new migration.
- Transactions and CSV artifacts use soft delete (`deletedAt`), so active queries must filter deleted rows.

## CSV import domain conventions (critical)
- CSV delimiter is semicolon (`;`), including fixtures.
- CSV headers/metadata are bank-export style German labels (e.g. `Buchungstag`, `Umsatz in EUR`).
- Parser entrypoint: `src/main/java/de/kruemelnerd/finanzapp/importcsv/CsvParser.java`.
- Decode strategy: strict UTF-8 first, fallback to Windows-1252.
- Deduplication prioritizes extracted reference tokens (`Ref.` / `REF:` variants).
- Different reference values must not be collapsed as duplicates.
- CSV fixtures belong in `src/test/resources/fixtures/` and remain semicolon-separated.

## Frontend and i18n conventions
- Thymeleaf templates live in `src/main/resources/templates/`.
- Shared layout is `templates/layouts/base.html`.
- Styles live in `src/main/resources/static/css/app.css`.
- Keep UI text in message bundles: `messages.properties`, `messages_de.properties`, `messages_en.properties`.
- Prefer message keys over hardcoded user-facing strings in templates/controllers.

## UI/UX review workflow
- For UI/UX changes, run the `ui-ux-pro-max` skill (`--design-system` plus at least one focused UX query).
- Apply the generated checklist to validate contrast, focus visibility, hover feedback, and responsive behavior.
- Avoid emoji icons; use SVG-based icons consistently.

## Testing conventions
- Default stack: JUnit 5 + AssertJ.
- Web integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc`.
- Security-aware endpoint tests should use `spring-security-test` helpers (`user(...)`, `csrf()`).
- Cucumber features live in `src/test/resources/features/` with glue under `de.kruemelnerd.finanzapp.cucumber`.
- Playwright tests run headless and are used for end-to-end UI verification.

## Communication preferences
- Always communicate with the user in German.

## Agent workflow expectations
- Make the smallest safe change that satisfies the task.
- Preserve package structure and naming patterns already used.
- Add or update tests for behavior changes.
- Run targeted tests first, then broader suites for higher-risk changes.
- Update docs and message keys when behavior or UI text changes.
- If the user asks for "next steps", scan project `.md` files for open TODOs first and use those as the primary basis for the response (especially `docs/project-todo-status.md` when present).
