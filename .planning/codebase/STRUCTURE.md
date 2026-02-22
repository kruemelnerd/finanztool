# Codebase Structure

**Analysis Date:** 2026-02-22

## Directory Layout

```
finanztool/
├── src/main/java/com/example/finanzapp/   # Application code (feature packages, config, domain, repositories)
├── src/main/resources/                    # Runtime resources (templates, CSS, messages, DB migrations)
├── src/test/java/com/example/finanzapp/   # Unit/integration/e2e/cucumber Java tests by feature
├── src/test/resources/                    # Test config, Gherkin features, CSV fixtures
├── src/main/java/db/migration/            # Flyway Java-based migrations
├── docs/                                  # Project docs and planning notes
├── .planning/codebase/                    # Generated mapper documents for planning/execution
├── pom.xml                                # Maven build, dependencies, test plugins
└── target/                                # Maven build output (generated artifacts)
```

## Directory Purposes

**`src/main/java/com/example/finanzapp/auth`:**
- Purpose: Authentication and registration flow.
- Contains: Login/register controller, registration service, Spring Security user-details service.
- Key files: `src/main/java/com/example/finanzapp/auth/AuthController.java`, `src/main/java/com/example/finanzapp/auth/RegistrationService.java`.

**`src/main/java/com/example/finanzapp/importcsv`:**
- Purpose: CSV ingestion pipeline and import-specific errors/results.
- Contains: Parser, upload/import services, exception and result records.
- Key files: `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`, `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`.

**`src/main/java/com/example/finanzapp/transactions`:**
- Purpose: Transaction listing, filtering, pagination, row/category projection.
- Contains: Controller, view service, record DTOs for templates.
- Key files: `src/main/java/com/example/finanzapp/transactions/TransactionsController.java`, `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`.

**`src/main/java/com/example/finanzapp/categories` and `src/main/java/com/example/finanzapp/rules`:**
- Purpose: Category tree management and category-rule lifecycle/execution.
- Contains: CRUD/reorder/import/export services and controllers, rule engine orchestration.
- Key files: `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`.

**`src/main/java/com/example/finanzapp/repository`:**
- Purpose: Persistence contracts over JPA entities.
- Contains: Spring Data repository interfaces with query methods and soft-delete updates.
- Key files: `src/main/java/com/example/finanzapp/repository/TransactionRepository.java`, `src/main/java/com/example/finanzapp/repository/RuleRepository.java`.

**`src/main/java/com/example/finanzapp/domain`:**
- Purpose: Entity and enum model of persisted business data.
- Contains: JPA `@Entity` classes and supporting enums.
- Key files: `src/main/java/com/example/finanzapp/domain/Transaction.java`, `src/main/java/com/example/finanzapp/domain/Category.java`, `src/main/java/com/example/finanzapp/domain/Rule.java`.

**`src/main/resources/templates` and `src/main/resources/static/css`:**
- Purpose: Server-rendered UI and styles.
- Contains: Thymeleaf pages, partial templates, global stylesheet.
- Key files: `src/main/resources/templates/layouts/base.html`, `src/main/resources/templates/partials/transactions-table.html`, `src/main/resources/static/css/app.css`.

**`src/main/resources/db/migration` and `src/main/java/db/migration`:**
- Purpose: Flyway migration source of truth.
- Contains: SQL schema migrations and Java data/backfill migrations.
- Key files: `src/main/resources/db/migration/V9__alter_transactions_add_categories.sql`, `src/main/java/db/migration/V10__NormalizeCategoryBooleanColumns.java`.

## Key File Locations

**Entry Points:**
- `src/main/java/com/example/finanzapp/FinanzappApplication.java`: Spring Boot application bootstrap.
- `src/main/java/com/example/finanzapp/auth/AuthController.java`: Root/login/register route entry.
- `src/main/java/com/example/finanzapp/dashboard/OverviewController.java`: Overview page + CSV import endpoint.

**Configuration:**
- `pom.xml`: Dependencies, Java version, surefire test setup.
- `src/main/resources/application.properties`: Data source, Flyway, JPA, i18n, upload size.
- `src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`: Auth rules and login behavior.
- `src/main/java/com/example/finanzapp/common/config/LocaleConfig.java`: Locale resolver/interceptor.

**Core Logic:**
- `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`: CSV import orchestration.
- `src/main/java/com/example/finanzapp/rules/CategoryAssignmentService.java`: Rule execution and category assignment.
- `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`: Query/filter/format for transaction UI.
- `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`: Category tree lifecycle operations.

**Testing:**
- `src/test/java/com/example/finanzapp/`: Java tests grouped by feature package.
- `src/test/resources/features/`: Cucumber feature files.
- `src/test/resources/fixtures/`: CSV fixture files.

## Naming Conventions

**Files:**
- `*Controller.java`: MVC route handlers (example: `src/main/java/com/example/finanzapp/settings/SettingsController.java`).
- `*Service.java`: Business/use-case services (example: `src/main/java/com/example/finanzapp/reports/SankeyReportService.java`).
- `*Repository.java`: Spring Data persistence interfaces (example: `src/main/java/com/example/finanzapp/repository/CsvArtifactRepository.java`).
- `V{N}__*.sql` / `V{N}__*.java`: Flyway migration naming (example: `src/main/resources/db/migration/V8__create_rules.sql`, `src/main/java/db/migration/V6__BackfillTransactionBookingComponents.java`).
- `*.html`: Thymeleaf templates with page and partial naming (example: `src/main/resources/templates/overview.html`, `src/main/resources/templates/partials/balance-chart.html`).

**Directories:**
- Feature-first package directories under `src/main/java/com/example/finanzapp/` (example: `transactions/`, `importcsv/`, `categories/`).
- Shared cross-cutting concerns under `src/main/java/com/example/finanzapp/common/` (example: `config/`, `web/`).
- Tests mirror production feature grouping under `src/test/java/com/example/finanzapp/`.

## Where to Add New Code

**New Feature:**
- Primary code: Add a new feature package under `src/main/java/com/example/finanzapp/` and follow controller/service/repository split as needed.
- Tests: Add corresponding tests in mirrored package under `src/test/java/com/example/finanzapp/`.

**New Component/Module:**
- Implementation: Place HTTP-facing parts in a `*Controller` in feature package and use-case logic in a `*Service` in the same feature package.

**Utilities:**
- Shared helpers: Place cross-feature helpers under `src/main/java/com/example/finanzapp/common/` only when truly shared (for example matching `src/main/java/com/example/finanzapp/common/web/UploadExceptionHandler.java`).

## Special Directories

**`target/`:**
- Purpose: Maven build output (`.class`, test artifacts, packaged jar).
- Generated: Yes.
- Committed: No.

**`.planning/codebase/`:**
- Purpose: Generated codebase-mapping docs used by GSD planning/execution commands.
- Generated: Yes.
- Committed: Yes.

**`src/main/resources/db/migration/` + `src/main/java/db/migration/`:**
- Purpose: Ordered Flyway migration chain (schema and data evolution).
- Generated: No.
- Committed: Yes.

---

*Structure analysis: 2026-02-22*
