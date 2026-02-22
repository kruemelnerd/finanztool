# Architecture

**Analysis Date:** 2026-02-22

## Pattern Overview

**Overall:** Feature-oriented layered monolith (Spring MVC + Service + Repository + Thymeleaf)

**Key Characteristics:**
- HTTP entry points are Spring MVC controllers that stay thin and delegate to services (for example `src/main/java/com/example/finanzapp/transactions/TransactionsController.java`, `src/main/java/com/example/finanzapp/categories/CategoriesController.java`).
- Business logic is concentrated in feature services with transactional boundaries (for example `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`).
- Persistence is centralized in Spring Data JPA repositories over SQLite entities with soft-delete semantics (`deletedAt`) for core business tables (`src/main/java/com/example/finanzapp/repository/TransactionRepository.java`, `src/main/java/com/example/finanzapp/domain/Transaction.java`).

## Layers

**Web Layer (MVC Controllers + Views):**
- Purpose: Handle routing, bind request params, populate model, and choose views/redirects.
- Location: `src/main/java/com/example/finanzapp/*/*Controller.java`, `src/main/resources/templates/`.
- Contains: Controllers, flash-message wiring, HTMX-aware redirects, server-rendered Thymeleaf pages/partials.
- Depends on: Application services, `MessageSource`, Spring Security principal.
- Used by: Browser requests to routes like `/overview`, `/transactions`, `/categories`, `/settings`, `/reports/sankey`.

**Application Service Layer:**
- Purpose: Execute feature use-cases and enforce domain/business rules.
- Location: `src/main/java/com/example/finanzapp/**/**Service.java`.
- Contains: CSV import pipeline, category/rule management, assignment engine orchestration, balance/report computation.
- Depends on: Repositories, domain entities/enums, helper components (`RuleEngine`, `RuleTextNormalizer`).
- Used by: Controllers and other services.

**Domain Model Layer:**
- Purpose: Represent persisted business state.
- Location: `src/main/java/com/example/finanzapp/domain/`.
- Contains: JPA entities (`User`, `Transaction`, `Category`, `Rule`, `CsvArtifact`, `BalanceDaily`) and enums (`CategoryAssignedBy`, `RuleMatchField`).
- Depends on: JPA annotations and Java time/value types.
- Used by: Repositories and services.

**Persistence Layer:**
- Purpose: Query/update database with explicit active-record filtering rules.
- Location: `src/main/java/com/example/finanzapp/repository/`.
- Contains: Spring Data repository interfaces with derived queries and custom `@Query` methods.
- Depends on: Domain entities and Spring Data JPA.
- Used by: Services.

**Infrastructure/Config Layer:**
- Purpose: Runtime configuration for security, locale, and cross-cutting web error handling.
- Location: `src/main/java/com/example/finanzapp/common/config/`, `src/main/java/com/example/finanzapp/common/web/`.
- Contains: `SecurityConfig`, `LocaleConfig`, `UploadExceptionHandler`.
- Depends on: Spring Security/Web MVC infrastructure and repositories where needed.
- Used by: Entire application context.

**Migration Layer:**
- Purpose: Evolve schema/data across app versions.
- Location: `src/main/resources/db/migration/`, `src/main/java/db/migration/`.
- Contains: SQL migrations `V1..V9` and Java migrations `V6`, `V10`.
- Depends on: SQLite SQL + Flyway Java migration API.
- Used by: Flyway on startup.

## Data Flow

**CSV Import Flow:**

1. `POST /overview/import-csv` or `POST /settings/import-csv` enters controller (`src/main/java/com/example/finanzapp/dashboard/OverviewController.java`, `src/main/java/com/example/finanzapp/settings/SettingsController.java`).
2. Controller delegates to upload/import services (`src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`, `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`).
3. Parsing, dedupe, rule-based category assignment, and persistence execute via parser + repositories + assignment service (`src/main/java/com/example/finanzapp/importcsv/CsvParser.java`, `src/main/java/com/example/finanzapp/rules/CategoryAssignmentService.java`).

**Transaction List Rendering Flow:**

1. `GET /transactions` or HTMX `GET /partials/transactions-table` enters controller (`src/main/java/com/example/finanzapp/transactions/TransactionsController.java`, `src/main/java/com/example/finanzapp/partials/PartialsController.java`).
2. Filtering/pagination/formatting are computed in `TransactionViewService` (`src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`).
3. Thymeleaf page/partial renders rows and actions (`src/main/resources/templates/transactions.html`, `src/main/resources/templates/partials/transactions-table.html`).

**Rule Execution Flow:**

1. Rule CRUD/toggle/run operations enter `CategoriesController`/`RulesController` (`src/main/java/com/example/finanzapp/categories/CategoriesController.java`, `src/main/java/com/example/finanzapp/rules/RulesController.java`).
2. Group management and ordering run in `RuleManagementService` (`src/main/java/com/example/finanzapp/rules/RuleManagementService.java`).
3. Matching and category application run in `CategoryAssignmentService` + `RuleEngine`, then persist through repositories (`src/main/java/com/example/finanzapp/rules/CategoryAssignmentService.java`, `src/main/java/com/example/finanzapp/rules/RuleEngine.java`).

**State Management:**
- Authentication state is server-side Spring Security session/form login (`src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`).
- Application state is persisted in SQLite via JPA entities/repositories (`src/main/resources/application.properties`, `src/main/java/com/example/finanzapp/repository/`).
- UI interaction state uses query parameters + flash attributes + HTMX partial refreshes (`src/main/resources/templates/transactions.html`, `src/main/resources/templates/partials/transactions-table.html`).

## Key Abstractions

**Service-Oriented Use Cases:**
- Purpose: Keep controllers orchestration-only and centralize feature logic.
- Examples: `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`, `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/reports/SankeyReportService.java`.
- Pattern: Stateless Spring `@Service` classes with constructor injection and transactional methods.

**View DTO/Record Models:**
- Purpose: Isolate template-facing projection from heavy entities.
- Examples: `src/main/java/com/example/finanzapp/transactions/TransactionRow.java`, `src/main/java/com/example/finanzapp/transactions/TransactionPage.java`, `src/main/java/com/example/finanzapp/balance/BalancePoint.java`.
- Pattern: Java `record` types generated by services and rendered by controllers/templates.

**Rule Evaluation Abstraction:**
- Purpose: Encapsulate deterministic rule matching and conflict detection.
- Examples: `src/main/java/com/example/finanzapp/rules/RuleEngine.java`, `src/main/java/com/example/finanzapp/rules/RuleTextNormalizer.java`.
- Pattern: Pure computation component returning evaluation result records.

**Soft-Delete Contract:**
- Purpose: Keep historical records while hiding inactive data in normal flows.
- Examples: `src/main/java/com/example/finanzapp/domain/Transaction.java`, `src/main/java/com/example/finanzapp/domain/CsvArtifact.java`, `src/main/java/com/example/finanzapp/repository/TransactionRepository.java`.
- Pattern: `deletedAt` timestamp + repository methods consistently filtering `DeletedAtIsNull`.

## Entry Points

**Application Bootstrap:**
- Location: `src/main/java/com/example/finanzapp/FinanzappApplication.java`
- Triggers: JVM startup (`mvn spring-boot:run` or packaged jar).
- Responsibilities: Boot Spring application context.

**Web Route Entry Points:**
- Location: `src/main/java/com/example/finanzapp/auth/AuthController.java`, `src/main/java/com/example/finanzapp/dashboard/OverviewController.java`, `src/main/java/com/example/finanzapp/transactions/TransactionsController.java`, `src/main/java/com/example/finanzapp/categories/CategoriesController.java`, `src/main/java/com/example/finanzapp/settings/SettingsController.java`, `src/main/java/com/example/finanzapp/reports/SankeyController.java`, `src/main/java/com/example/finanzapp/partials/PartialsController.java`.
- Triggers: HTTP GET/POST requests.
- Responsibilities: Request binding, principal handoff, flash/model population, redirect/view resolution.

**API-Style JSON Endpoint:**
- Location: `src/main/java/com/example/finanzapp/reports/SankeyController.java` (`/api/reports/sankey`).
- Triggers: Frontend chart data request.
- Responsibilities: Return report DTO payload from service.

**Database Migration Entry Points:**
- Location: `src/main/resources/db/migration/*.sql`, `src/main/java/db/migration/*.java`.
- Triggers: Flyway on application startup.
- Responsibilities: Schema/data migration before app runtime logic.

## Error Handling

**Strategy:** Mixed domain-exception + status/boolean returns with controller-level flash messaging.

**Patterns:**
- CSV path throws domain runtime exceptions (`CsvImportException`) and logs actionable context in upload service (`src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`).
- Many management services return typed status enums/booleans; controllers map them to user-visible i18n flash messages (`src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/categories/CategoriesController.java`).
- Global multipart-size failures are mapped centrally via `@ControllerAdvice` and redirected to originating pages (`src/main/java/com/example/finanzapp/common/web/UploadExceptionHandler.java`).

## Cross-Cutting Concerns

**Logging:** SLF4J logging is focused on CSV upload/import failure context (`src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`).
**Validation:** Input validation combines Bean Validation for form DTOs and explicit guard clauses in services/controllers (`src/main/java/com/example/finanzapp/auth/RegistrationForm.java`, `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`).
**Authentication:** Spring Security form login with authenticated-by-default routes and custom login success locale setup (`src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`).

---

*Architecture analysis: 2026-02-22*
