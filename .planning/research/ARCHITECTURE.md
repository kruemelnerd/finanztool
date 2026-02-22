# Architecture Research

**Domain:** Personal finance web app (server-rendered Spring MVC monolith)
**Researched:** 2026-02-22
**Confidence:** HIGH

## Standard Architecture

### System Overview

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ Presentation Layer (Spring MVC + Thymeleaf/HTMX)                            │
├──────────────────────────────────────────────────────────────────────────────┤
│  AuthController  OverviewController  TransactionsController  RulesController │
│  CategoriesController  SettingsController  PartialsController                │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ delegates use-cases
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ Application Services                                                         │
├──────────────────────────────────────────────────────────────────────────────┤
│  CsvImportService  TransactionViewService  RuleManagementService             │
│  CategoryAssignmentService  CategoryManagementService  SankeyReportService   │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ reads/writes entities
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ Persistence + Domain                                                         │
├──────────────────────────────────────────────────────────────────────────────┤
│  Repositories (Spring Data JPA)                                              │
│  Entities: User, Transaction, Category, Rule, CsvArtifact, BalanceDaily      │
│  SQLite + Flyway migrations                                                   │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Communicates With |
|-----------|----------------|-------------------|
| Presentation (controllers + templates) | Route handling, request binding, page/partial rendering, flash feedback | Application services, Message bundles, Spring Security session |
| Transaction application service boundary | Filter/paginate/projection logic, transaction actions, row-level UX orchestration | Transaction repository, category/rule services, DTO records |
| Import application service boundary | CSV upload/parsing/dedupe/assignment orchestration | Parser, assignment service, transaction/csv repositories |
| Rules and categories boundary | Rule lifecycle, ordering, assignment execution, category structure management | Rule/category repositories, assignment engine, transaction repository |
| Account scope boundary (new milestone) | Resolve selected account context for all account-bound reads/writes | Controllers, all feature services, account-aware repositories |
| Persistence boundary | Soft-delete-safe querying and updates, aggregate retrieval | SQLite via JPA, Flyway migration history |

## Recommended Project Structure

```text
src/main/java/com/example/finanzapp/
├── accounts/                      # NEW: account model, selection, account scope service
│   ├── AccountController.java
│   ├── AccountService.java
│   ├── AccountContextResolver.java
│   └── dto/
├── transactions/                  # existing list/filter/actions, expanded UX use-cases
│   ├── TransactionsController.java
│   ├── TransactionViewService.java
│   ├── TransactionBulkActionService.java   # NEW: multi-row actions/edit workflows
│   └── TransactionRow.java
├── rules/                         # existing deterministic engine + transfer workflows
│   ├── RulesController.java
│   ├── RuleManagementService.java
│   ├── RuleTransferService.java            # NEW: export/import package handling
│   └── RuleEngine.java
├── categories/                    # existing category tree and management
├── importcsv/                     # existing import pipeline, now account-aware
├── repository/                    # add AccountRepository + account-scoped query methods
├── domain/                        # add Account (+ accountId references)
└── common/                        # shared infra, security, web exception handling
```

### Structure Rationale

- **Feature-first packages stay intact:** Minimizes regression by extending current controller/service/repository seams rather than introducing a new architecture style.
- **`accounts/` as explicit boundary:** Prevents account-selection logic from leaking into unrelated features and gives one source of truth for account scoping.
- **Transaction UX expansion inside `transactions/`:** Keeps list rendering and bulk/multi-item actions cohesive and testable in one feature package.
- **Rule transfer in `rules/`:** Export/import is a rule lifecycle concern, so rule serialization/validation belongs near rule management.
- **Repository layer remains the only DB access path:** Reduces risk of ad-hoc SQL and keeps soft-delete + user/account constraints centralized.

## Architectural Patterns

### Pattern 1: Account-Scoped Service Facade

**What:** Every user-facing use-case resolves `userId + accountId` once, then calls account-aware repository/service methods.
**When to use:** Any read/write touching transactions, balances, rules, categories, imports.
**Trade-offs:** Slightly more method parameters, but much lower risk of data leakage across accounts.

**Example:**
```java
public TransactionPage listTransactions(User user, AccountSelection selection, Filters filters) {
  long accountId = accountContextResolver.resolveForUser(user.getId(), selection);
  return transactionViewService.listForAccount(user.getId(), accountId, filters);
}
```

### Pattern 2: DTO/Record Boundary Between Service and Template

**What:** Services return page/row records; templates never bind directly to mutable entities.
**When to use:** Transaction list UX expansion, new per-row action menus, multi-row panels.
**Trade-offs:** Extra mapping code, but safer UI evolution and fewer persistence side effects.

### Pattern 3: Versioned Rule Transfer Contract

**What:** Rule export/import uses a versioned JSON payload with strict validation and deterministic mapping.
**When to use:** Rule portability between setups/environments.
**Trade-offs:** Requires schema/version maintenance, but avoids fragile ad-hoc imports.

**Example:**
```java
record RuleTransferPackage(
  int schemaVersion,
  String exportedAt,
  List<RuleTransferItem> rules
) {}
```

## Data Flow

### Request Flow (Multi-Account Transaction UX)

```text
User selects account + opens /transactions
    ↓
TransactionsController
    ↓ resolves account context (userId, accountId)
TransactionViewService.listForAccount(...)
    ↓
TransactionRepository.findActiveByUserIdAndAccountId(...)
    ↓
SQLite (transactions filtered by user + account + deletedAt IS NULL)
    ↓
Service maps to TransactionRow/TransactionPage
    ↓
Thymeleaf full page or HTMX partial response
```

### Request Flow (Rule Export/Import)

```text
User triggers rules export/import in UI
    ↓
RulesController
    ↓
RuleTransferService
    ├─ export: read active rules (scoped), map to versioned package, stream file
    └─ import: parse package, validate schema, map references, persist in transaction
    ↓
RuleRepository + CategoryRepository (+ account-aware checks where applicable)
    ↓
Flash/i18n result to UI
```

### Key Data Flows

1. **Account-scoped reads/writes:** every query path includes `(userId, accountId)` plus soft-delete predicate.
2. **CSV import to transaction persistence:** selected account is attached at import time and propagated through dedupe + assignment.
3. **Rule transfer:** package -> validation -> domain mapping -> atomic persistence, with conflict reporting.

## Build Order and Dependency Implications

1. **Foundation: Account domain + migrations + repository constraints**
   - Add `Account` entity/table and `account_id` links to account-bound data.
   - Introduce default-account backfill migration to preserve current behavior for existing users.
   - Dependency implication: all higher layers depend on stable account scoping primitives.

2. **Service-layer account scoping (no UX change yet)**
   - Add account-aware service method variants; keep old controller routes behavior via implicit default account.
   - Dependency implication: reduces regression risk before UI changes by keeping behavior-compatible adapter paths.

3. **Controller + template integration for account selection**
   - Add account switcher, propagate selected account through query params/session.
   - Dependency implication: transaction/import/rule/category controllers now require account context resolver.

4. **Transaction UX expansion (multi-row workflows, contextual actions)**
   - Build on already account-scoped transaction service methods.
   - Dependency implication: avoid implementing bulk actions before account scoping, otherwise rework is likely.

5. **Rule export/import contract + workflow**
   - Implement versioned transfer format after account/category IDs are stable.
   - Dependency implication: transfer mapping depends on final account/category/rule scoping rules.

6. **Hardening pass (tests + regression checks)**
   - Prioritize targeted tests: repository scoping, service scoping, controller flows for default-account backward compatibility.
   - Dependency implication: catches cross-account leakage and preserves current single-account user experience.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Hidden Account Scope in Controllers Only

**What people do:** Resolve account in controller but call non-scoped service/repository methods.
**Why it's wrong:** Easy cross-account data leakage and inconsistent behavior between routes.
**Do this instead:** Enforce account scoping at service and repository signatures.

### Anti-Pattern 2: Reusing DB IDs in Rule Transfer Files

**What people do:** Export internal IDs and expect them to be valid on import.
**Why it's wrong:** IDs are environment-local and break portability.
**Do this instead:** Transfer by stable business fields + explicit conflict strategy.

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Controllers ↔ Services | Direct method calls with user/account context | Keep controllers thin; orchestration only |
| Services ↔ Repositories | Account-scoped query methods | Mandatory for data isolation |
| Import ↔ Rules assignment | Service-to-service call (`CategoryAssignmentService`) | Preserve deterministic assignment behavior |
| Rules transfer ↔ Categories | Service/repository lookup by stable identifiers | Needed for portable imports |

## Sources

- `.planning/PROJECT.md`
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/STRUCTURE.md`

---
*Architecture research for: Finanztool (next milestone integration)*
*Researched: 2026-02-22*
