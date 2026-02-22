# Pitfalls Research

**Domain:** Personal finance web app extension (multi-account, advanced transaction workflows, rule portability)
**Researched:** 2026-02-22
**Confidence:** HIGH (codebase-specific concerns + active milestone context)

## Critical Pitfalls

### Pitfall 1: Account Scope Leakage Across Existing Single-Account Paths

**What goes wrong:**
New account support is added in some flows but legacy queries/controllers/services still resolve data by `user_id` only, so balances, transaction lists, or rule effects bleed across accounts.

**Why it happens:**
Brownfield services are already broad and coupled; incremental changes miss hidden call paths and helper methods that assume one implicit account.

**How to avoid:**
Add `account_id` as a first-class invariant in schema, repositories, DTOs, service APIs, and authorization checks; block merges until account-scoped integration tests pass for each read/write path.

**Warning signs:**
"Totals look wrong" bug reports after enabling second account, cross-account rows in paginated views, or repository methods without explicit account filter in modified areas.

**Phase to address:**
Phase 1 - Data model and access invariant hardening (before UX expansion).

---

### Pitfall 2: Unsafe Migration Sequence for Financial Data

**What goes wrong:**
Schema migrations for multi-account introduce nullable or backfilled columns without deterministic mapping, causing orphaned transactions, wrong opening balances, or irreversible audit confusion.

**Why it happens:**
Teams optimize for feature speed and under-specify migration/backfill logic for historical finance records.

**How to avoid:**
Ship additive migration plan first (new tables/columns + defaults), run idempotent backfill with reconciliation checks, add rollback-safe checkpoints, and verify with production-like snapshots before cutover.

**Warning signs:**
Backfill scripts need manual edits per environment, reconciliation delta is non-zero, or migration cannot be re-run safely in staging.

**Phase to address:**
Phase 1 - Migration strategy and data reconciliation gates.

---

### Pitfall 3: Rule Engine Ambiguity After Portability/Versioning Changes

**What goes wrong:**
Exported/imported rules lose deterministic behavior (priority, scope, category references, text normalization), producing inconsistent categorization across setups.

**Why it happens:**
Portable JSON formats evolve without explicit version contracts, migration adapters, or compatibility tests.

**How to avoid:**
Define versioned rule schema with strict contract tests, explicit migration adapters (`v1 -> v2`...), stable IDs for category references, and deterministic precedence semantics documented in code/tests.

**Warning signs:**
Imported rule count matches but assignment outcomes differ, fallback/uncategorized spikes after import, or ad-hoc format conditionals spread across services.

**Phase to address:**
Phase 2 - Rule portability contract and compatibility layer.

---

### Pitfall 4: Bulk Transaction UX Without Concurrency/Integrity Controls

**What goes wrong:**
Advanced multi-row workflows (bulk categorize, merge/split, rule creation from selection) overwrite newer edits, apply partial updates, or bypass soft-delete invariants.

**Why it happens:**
UI batching is added before domain command boundaries and optimistic locking/idempotency are defined.

**How to avoid:**
Implement explicit bulk command APIs with atomic transaction boundaries, version checks (or updated-at preconditions), dry-run preview, and per-item failure reporting.

**Warning signs:**
Intermittent "some rows changed, some not" support tickets, duplicate side effects on retry, or missing audit trail for who changed many transactions.

**Phase to address:**
Phase 3 - Transaction workflow engine and conflict-safe bulk operations.

---

### Pitfall 5: Performance Collapse from In-Memory Filtering at Multi-Account Scale

**What goes wrong:**
Per-account growth multiplies dataset size while filtering/pagination remains in application memory, causing slow pages, timeouts, and expensive report generation.

**Why it happens:**
Current query patterns load all active rows then filter in Java; this remains hidden while data volume is low.

**How to avoid:**
Move filters/sorting/pagination into repository queries (`Pageable`/criteria), add account-aware indexes, and define response-time SLO checks for list/report endpoints before rollout.

**Warning signs:**
Overview/transactions latency rises with each new account import, heap usage spikes during list/report views, or DB query count/time grows non-linearly.

**Phase to address:**
Phase 2 - Query refactor and performance baseline for account-scoped data.

---

### Pitfall 6: Financial Data Exposure Through Error and Audit Paths

**What goes wrong:**
Raw CSV rows, transaction descriptors, or parser internals leak into flash messages/logs while extending import/workflow logic.

**Why it happens:**
Existing error propagation uses direct exception messages; new paths reuse this pattern under delivery pressure.

**How to avoid:**
Standardize sanitized domain error codes for UI, structured logging with correlation IDs only, and test assertions that sensitive payload fragments never appear in responses/logs.

**Warning signs:**
Support screenshots showing raw bank row content in UI, logs containing full booking text/reference strings, or exception-to-user-message passthrough in controllers.

**Phase to address:**
Phase 0 - Security hardening pre-work (must precede feature expansion).

---

### Pitfall 7: Cross-Account Authorization Gaps in New Endpoints

**What goes wrong:**
Users can operate on account-scoped resources they do not own via guessed IDs or missing ownership checks in new action endpoints.

**Why it happens:**
Authenticated-by-default is mistaken for resource-level authorization; account ownership validation is not consistently centralized.

**How to avoid:**
Enforce ownership checks in service layer for every account/transaction/rule command, use opaque identifiers where practical, and add negative integration tests for cross-account access.

**Warning signs:**
Controller methods accept raw IDs without ownership guard, only happy-path auth tests exist, or 200 responses appear for unauthorized cross-account IDs.

**Phase to address:**
Phase 0 - Security contract and authorization test matrix.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Keep mixed read/write `resolveUser` side effects | Faster reuse of existing helpers | Hidden writes on read paths, harder scaling/debugging | Never for multi-account rollout |
| Add account branching inside monolithic services | Minimal refactor today | Compounding complexity and regression risk | Only as temporary bridge behind tests and extraction tickets |
| Extend transfer JSON without schema version migration | Quick export/import demo | Breaks backward compatibility and deterministic rule behavior | Never for user-facing portability |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| CSV import to multi-account mapping | Infer account from file name/heuristics only | Require explicit target account plus validated fallback policy |
| Rule/category transfer | Assume identical category trees across instances | Use stable IDs + mapping UI + unresolved-reference handling |
| HTMX partial updates for bulk actions | Return optimistic UI state before commit outcome | Return server-confirmed state plus conflict/error fragments |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| In-memory list filtering/paging | Slow transaction page, high heap | DB-level filtering with account/date predicates and paging | Usually after multiple imports per account (>~50k rows total) |
| Per-row category/rule lookup loops (N+1) | Many small queries, unstable latency | Batch queries and precomputed maps per request | Visible with deep category trees and bulk edits |
| Full-history report recomputation each request | Report timeout under normal usage | Date-bounded aggregation queries and cached/materialized summaries | Annual reports with multi-year history |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Logging raw financial payloads from parser/workflow exceptions | PII/financial confidentiality breach | Sanitize exceptions, structured logs, redaction tests |
| Missing brute-force controls on auth endpoints during growth | Account takeover attempts scale with visibility | Add rate limiting/lockouts/audit alerts before broader release |
| Treating auth as sufficient without account ownership checks | Unauthorized cross-account read/write | Central ownership guard in services + negative integration tests |

## "Looks Done But Isn't" Checklist

- [ ] **Multi-account launch:** every transaction/rule/category query is account-scoped and tested for isolation.
- [ ] **Migration complete:** opening balances and transaction counts reconcile exactly before and after backfill.
- [ ] **Bulk workflows:** retries are idempotent and conflict handling is explicit to users.
- [ ] **Rule portability:** importing older versions yields identical categorization outcomes on reference fixtures.
- [ ] **Security hardening:** no sensitive values in UI/logs and cross-account negative tests are green.

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Account scope leakage | Phase 1 - Data model and access invariant hardening | Isolation integration tests across at least two accounts per user |
| Unsafe migration sequence | Phase 1 - Migration strategy and reconciliation | Backfill dry-run + reconciliation report equals zero delta |
| Rule portability ambiguity | Phase 2 - Versioned transfer contract | Cross-version import contract tests on fixed fixtures |
| Bulk workflow integrity failures | Phase 3 - Conflict-safe bulk command layer | Concurrency/idempotency integration tests pass |
| Performance collapse | Phase 2 - Query/paging refactor | Latency budget checks on seeded large dataset |
| Data exposure in errors/logs | Phase 0 - Security hardening pre-work | Automated redaction assertions in controller/service tests |
| Cross-account authorization gaps | Phase 0 - Authorization contract | Negative authorization integration matrix passes |

## Sources

- `.planning/PROJECT.md` (active milestone scope, constraints, multi-account/rule portability priorities)
- `.planning/codebase/CONCERNS.md` (known fragilities, security risks, performance bottlenecks, missing feature gaps)
- `.planning/codebase/TESTING.md` (current testing patterns and coverage gaps relevant to prevention)

---
*Pitfalls research for: finanztool milestone (subsequent)*
*Researched: 2026-02-22*
