# Codebase Concerns

**Analysis Date:** 2026-02-22

## Tech Debt

**Overloaded service/controller classes:**
- Issue: High-complexity classes combine querying, validation, formatting, mapping, and orchestration in single files, increasing change risk and review cost.
- Files: `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`, `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`, `src/main/java/com/example/finanzapp/partials/PartialsController.java`
- Impact: Small feature changes require touching large methods with many side effects; regression probability is high.
- Fix approach: Split into focused collaborators (query services, mappers, validators, command handlers), keep controllers thin, and add per-component tests before extracting behavior.

**Mixed read/write behavior in user resolution:**
- Issue: Methods annotated with `@Transactional(readOnly = true)` call `categoryBootstrapService.ensureDefaultUncategorized(user)` through `resolveUser`, which can perform writes.
- Files: `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`, `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`, `src/main/java/com/example/finanzapp/categories/CategoryTransferService.java`, `src/main/java/com/example/finanzapp/rules/RuleTransferService.java`, `src/main/java/com/example/finanzapp/categories/CategoryBootstrapService.java`
- Impact: Transaction semantics are unclear, and read paths can trigger side effects unexpectedly under load.
- Fix approach: Move bootstrap to explicit onboarding/login flow or dedicated write use case; keep read methods side-effect free.

**Duplicated formatting and normalization logic:**
- Issue: Date/amount formatting and booking-text sanitization logic is duplicated across multiple services/controllers.
- Files: `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`, `src/main/java/com/example/finanzapp/partials/PartialsController.java`, `src/main/java/com/example/finanzapp/importcsv/CsvImportService.java`, `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`
- Impact: Bug fixes require synchronized edits in multiple places and can diverge by locale or parsing edge cases.
- Fix approach: Introduce shared formatter/parser utility components and centralize domain text normalization rules.

## Known Bugs

**Sensitive parser details surfaced in UI on import errors:**
- Symptoms: Import failures can expose raw CSV row content in user-facing error text.
- Files: `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`, `src/main/java/com/example/finanzapp/dashboard/OverviewController.java`, `src/main/java/com/example/finanzapp/settings/SettingsController.java`
- Trigger: Upload a malformed CSV row that throws `CsvImportException` with row payload (for example invalid date/column format); controller forwards `ex.getMessage()` to flash message.
- Workaround: None in application flow; requires code change to map parser errors to safe message keys.

**CSV amount parsing can throw unhandled runtime for >2 decimal inputs:**
- Symptoms: Import can fail with server error path when amount has more than 2 fractional digits.
- Files: `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`
- Trigger: `parseAmountToCents` uses `BigDecimal#setScale(0)` without rounding mode after `movePointRight(2)`.
- Workaround: Pre-clean source files to strict 2-decimal amounts before upload.

## Security Considerations

**Financial row data may leak into application logs:**
- Risk: Exception messages include raw CSV records and are logged with stack traces.
- Files: `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`, `src/main/java/com/example/finanzapp/importcsv/CsvUploadService.java`
- Current mitigation: Logging includes metadata (user, filename, content type, size), but also logs exception message/stack.
- Recommendations: Replace raw parser exception text with sanitized error codes; log correlation IDs and metadata only.

**No explicit brute-force/abuse protection on authentication endpoints:**
- Risk: `/login` and `/register` rely on default Spring Security behavior without rate limiting or lockout policy.
- Files: `src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`, `src/main/java/com/example/finanzapp/auth/AuthController.java`
- Current mitigation: Password hashing via BCrypt (`BCryptPasswordEncoder`) and authenticated-by-default routes.
- Recommendations: Add IP/account throttling, temporary lockouts, and audit logging for repeated failed authentication.

**Third-party script loaded from CDN without local fallback:**
- Risk: Runtime dependency on external script availability/integrity for reporting UI.
- Files: `src/main/resources/templates/reports-sankey.html`
- Current mitigation: Basic client-side error fallback to empty-state rendering.
- Recommendations: Add SRI/CSP strategy and local fallback bundle for `https://www.gstatic.com/charts/loader.js`.

## Performance Bottlenecks

**In-memory filtering and pagination for transactions:**
- Problem: Full transaction list is loaded, then filters and pagination are applied in Java streams.
- Files: `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`, `src/main/java/com/example/finanzapp/repository/TransactionRepository.java`
- Cause: `findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc` returns all active rows; no query-level filtering/paging.
- Improvement path: Move filters and pagination to repository queries (`Pageable` + criteria/specification); project only needed columns for list views.

**N+1 query patterns in category/rule display assembly:**
- Problem: Per-parent and per-subcategory repository calls (children lookup, usage counts) create many small queries.
- Files: `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`, `src/main/java/com/example/finanzapp/transactions/TransactionViewService.java`
- Cause: Iterative loading model instead of batched aggregation queries.
- Improvement path: Add batched repository queries (joins/grouped counts), prefetch child collections, and build maps from single query results.

**Report generation scans complete transaction history per request:**
- Problem: Sankey and balance-related paths iterate over full transaction lists before narrowing to date/year.
- Files: `src/main/java/com/example/finanzapp/reports/SankeyReportService.java`, `src/main/java/com/example/finanzapp/balance/AccountBalanceService.java`
- Cause: Year/range filtering is done in application memory rather than database query.
- Improvement path: Add date-bounded repository methods and incremental/materialized aggregates for reporting endpoints.

## Fragile Areas

**Category/rule lifecycle coupling across multiple services:**
- Files: `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`, `src/main/java/com/example/finanzapp/rules/CategoryAssignmentService.java`
- Why fragile: Reorder, move, delete, and upsert flows depend on synchronized sort-order and soft-delete behavior across entities.
- Safe modification: Change one workflow at a time, keep DB invariants explicit, and run focused integration tests for reorder/delete/run flows.
- Test coverage: No dedicated tests detected for `CategoryManagementService` and `RuleManagementService` in `src/test/java/`.

**Monolithic E2E scenario file with broad responsibilities:**
- Files: `src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`
- Why fragile: A single large class (>600 lines) mixes auth, settings, transactions, categories, and import scenarios; failures are hard to triage.
- Safe modification: Split by feature area (`auth`, `transactions`, `categories`, `imports`) and keep reusable UI helpers in dedicated support classes.
- Test coverage: Broad happy-path coverage exists, but change isolation and root-cause diagnosis are weak.

## Scaling Limits

**Soft-delete growth without archival strategy:**
- Current capacity: Active queries always filter `deleted_at IS NULL`; soft-deleted transactions and CSV blobs remain in primary tables.
- Limit: Table size and index efficiency degrade over time, especially for `transactions` and `csv_artifacts`.
- Scaling path: Add retention/archival jobs, partial indexes optimized for active rows, and periodic vacuum/maintenance for SQLite.

**Single-node SQLite architecture:**
- Current capacity: `spring.datasource.url=jdbc:sqlite:finanzapp.db` with local file DB in `src/main/resources/application.properties`.
- Limit: Write concurrency and horizontal scaling are constrained by SQLite locking model.
- Scaling path: Plan migration path to server DB (for example Postgres) with Flyway continuity and repository query compatibility checks.

## Dependencies at Risk

**Google Charts loader CDN dependency:**
- Risk: External CDN outage, policy restrictions, or script changes can break Sankey visualization rendering.
- Impact: `reports-sankey` page loses primary chart functionality.
- Migration plan: Replace or wrap with locally served chart library bundle and stable version pinning.

## Missing Critical Features

**Multi-account support in finance model:**
- Problem: Current model and flows assume a single implicit account per user.
- Blocks: Cannot separate balances/transactions/rules by account, which limits realistic personal-finance usage.

**Bulk transaction operations in UI:**
- Problem: No multi-select operations for transaction actions; workflow remains row-by-row.
- Blocks: Efficient cleanup/reclassification for large imports is not possible.

**Rule/category portability maturity gap:**
- Problem: JSON import/export supports transfer, but no version migration strategy beyond current format token (`finanztool-categories-v1`, `finanztool-rules-v1`).
- Blocks: Safe long-term backward compatibility for evolving rule/category schemas.

## Test Coverage Gaps

**Category and rule management core logic lacks direct automated tests:**
- What's not tested: Create/update/delete/reorder/move workflows and cross-entity invariants for categories and grouped rules.
- Files: `src/main/java/com/example/finanzapp/categories/CategoryManagementService.java`, `src/main/java/com/example/finanzapp/rules/RuleManagementService.java`
- Risk: Regression in ordering, protection rules, and soft-delete behavior can ship unnoticed.
- Priority: High

**Security configuration behavior lacks focused tests:**
- What's not tested: Access policy details, login success handler locale behavior, and route protection changes in `SecurityFilterChain`.
- Files: `src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`
- Risk: Silent auth/access regressions when security rules are adjusted.
- Priority: Medium

**Report services lack dedicated unit/integration coverage:**
- What's not tested: Sankey aggregation correctness per year/category and performance-sensitive edge cases with large datasets.
- Files: `src/main/java/com/example/finanzapp/reports/SankeyReportService.java`
- Risk: Incorrect totals or category labeling errors in financial reports.
- Priority: Medium

---

*Concerns audit: 2026-02-22*
