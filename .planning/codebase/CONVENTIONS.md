# Coding Conventions

**Analysis Date:** 2026-02-22

## Naming Patterns

**Files:**
- Feature-oriented package structure under `src/main/java/de/kruemelnerd/finanzapp/` and mirrored tests under `src/test/java/de/kruemelnerd/finanzapp/`.
- Classes use suffix conventions: controllers in `*Controller` (for example `src/main/java/de/kruemelnerd/finanzapp/transactions/TransactionsController.java`), services in `*Service` (for example `src/main/java/de/kruemelnerd/finanzapp/importcsv/CsvImportService.java`), repositories in `*Repository` (for example `src/main/java/de/kruemelnerd/finanzapp/repository/TransactionRepository.java`).
- Test classes use `*Test`, `*IntegrationTest`, `*E2ETest` (for example `src/test/java/de/kruemelnerd/finanzapp/importcsv/CsvParserTest.java`, `src/test/java/de/kruemelnerd/finanzapp/repository/TransactionRepositoryIntegrationTest.java`, `src/test/java/de/kruemelnerd/finanzapp/e2e/PlaywrightE2ETest.java`).

**Functions:**
- Methods use lowerCamelCase and behavior-focused names (for example `loadTransactionsPage` in `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`, `parseHandlesUtf8BomAtFileStart` in `src/test/java/com/example/finanzapp/importcsv/CsvParserTest.java`).
- Boolean methods are predicate-like (`isBlankRecord`, `isTrailingMetaRecord` in `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`).

**Variables:**
- Fields and locals use lowerCamelCase (`categoryAssignmentService` in `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`).
- Constants use UPPER_SNAKE_CASE (`MAX_SIZE_BYTES`, `DATE_FORMAT_DE` in `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`).

**Types:**
- Domain entities use singular PascalCase (`Transaction`, `User`) in `src/main/java/com/example/finanzapp/domain/`.
- Immutable transfer/result types prefer Java records (for example `TransactionPage` in `src/main/java/com/example/finanzapp/transactions/TransactionPage.java`, `CsvImportResult` in `src/main/java/com/example/finanzapp/importcsv/CsvImportResult.java`).

## Code Style

**Formatting:**
- No dedicated formatter config detected (`.prettierrc*`, `.eslintrc*`, `eslint.config.*`, `biome.json` are not present at repository root).
- Use 2-space indentation and K&R braces, matching Java sources such as `src/main/java/com/example/finanzapp/auth/AuthController.java` and `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`.
- Prefer guard clauses for early exits (`src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`, `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`).

**Linting:**
- Dedicated linting toolchain is not configured in `pom.xml` (no Checkstyle/Spotless/PMD plugin entries).
- Use compile/tests as quality gate via Maven (`mvn -DskipTests compile`, `mvn test`) per `AGENTS.md`.

## Import Organization

**Order:**
1. Project imports (`de.kruemelnerd.finanzapp...`) first (see `src/main/java/de/kruemelnerd/finanzapp/transactions/TransactionViewService.java`).
2. JDK imports (`java...`) next.
3. Third-party/framework imports (`org.springframework...`, `org.apache...`) last.

**Path Aliases:**
- Not applicable in Java package imports; fully qualified package imports are used (for example `de.kruemelnerd.finanzapp.repository.TransactionRepository` in `src/main/java/de/kruemelnerd/finanzapp/importcsv/CsvImportService.java`).

## Error Handling

**Patterns:**
- Use domain-specific runtime exceptions for CSV flows (`CsvImportException` in `src/main/java/com/example/finanzapp/importcsv/CsvParser.java` and `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`).
- Catch low-level exceptions, add context, and rethrow domain/semantic exceptions (`IOException` wrapped in `CsvImportException` in `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`; JSON serialization failures wrapped in `IllegalStateException` in `src/main/java/com/example/finanzapp/rules/RuleTransferService.java`).
- Return safe defaults for optional/invalid state where user-facing resilience is preferred (`Optional.empty()` and `List.of()` fallbacks in `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`).
- Handle user-correctable domain errors in controllers and map to flash/model feedback (`src/main/java/com/example/finanzapp/settings/SettingsController.java`, `src/main/java/com/example/finanzapp/dashboard/OverviewController.java`).

## Logging

**Framework:** SLF4J (`org.slf4j.Logger`) in `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`

**Patterns:**
- Logging is targeted and contextual at service boundaries, not pervasive across all classes.
- Include operational context (user, filename, size) and exception details on failure (`log.warn`/`log.error` in `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`).
- Keep sensitive payload content out of logs; metadata-only logging pattern is used in `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`.

## Comments

**When to Comment:**
- Minimal inline comments; comments appear only where behavior is non-obvious (single robustness comment in `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`).
- Prefer self-descriptive method extraction over explanatory comments (`src/main/java/com/example/finanzapp/importcsv/CsvParser.java`).

**JSDoc/TSDoc:**
- Not applicable; JavaDoc usage is minimal/not enforced in current Java sources (`src/main/java/com/example/finanzapp/`).

## Function Design

**Size:**
- Service methods can be long for orchestration-heavy flows (`loadFilteredTransactions` in `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`), with private helper extraction used to segment logic.

**Parameters:**
- Controllers pass explicit request params rather than wrapper DTOs for filter-heavy endpoints (`src/main/java/com/example/finanzapp/transactions/TransactionsController.java`).
- Service constructors use explicit constructor injection only (`src/main/java/com/example/finanzapp/auth/AuthController.java`, `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`).

**Return Values:**
- Use records for aggregated responses (`src/main/java/com/example/finanzapp/transactions/TransactionPage.java`, `src/main/java/com/example/finanzapp/importcsv/CsvParsingResult.java`).
- Use booleans for command outcomes where controller decides UX messaging (`setManualCategory`, `softDeleteTransaction` in `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`).

## Module Design

**Exports:**
- Feature modules expose controllers/services publicly and keep helper records/methods private inside classes (`src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`).
- Repository layer defines query methods and JPQL in interfaces (`src/main/java/com/example/finanzapp/repository/TransactionRepository.java`).

**Barrel Files:**
- Not applicable in this Java codebase; no barrel-export pattern is used.

---

*Convention analysis: 2026-02-22*
