# Project Research Summary

**Project:** Finanztool
**Domain:** Server-rendered personal finance web application (brownfield Spring Boot monolith)
**Researched:** 2026-02-22
**Confidence:** HIGH

## Executive Summary

Finanztool is an established server-rendered finance app that now needs to evolve from a single-account workflow into a multi-account product without losing deterministic behavior and data trust. The research converges on a modular-monolith path: keep Spring MVC + Thymeleaf/HTMX + JPA, introduce explicit account scoping across data and services, and defer major platform churn (Boot 4, SPA rewrite, microservices) until the new account model and workflows are stable.

The recommended approach is to sequence delivery by dependency risk, not by UI visibility. First harden data invariants (account model, migration safety, ownership checks, sanitized error paths), then refactor query and service boundaries to be account-aware, then ship UX-heavy transaction bulk workflows and rule portability. This preserves the current working baseline while enabling the milestone goals (multi-account, faster transaction operations, reusable rules).

The key risks are cross-account leakage, unsafe migrations on financial history, and non-deterministic rule behavior after export/import changes. Mitigation is explicit and testable: enforce `(userId, accountId)` contracts end-to-end, run idempotent/reconcilable migrations against production-like snapshots, and lock rule transfer behind versioned JSON schema + compatibility tests before broad rollout.

## Key Findings

### Recommended Stack

Research strongly supports staying on the existing architectural style and modernizing incrementally: upgrade to Spring Boot 3.5.x first, keep Java 21, and prepare PostgreSQL as the scale target while retaining SQLite for local/small installs. The goal is delivery safety for milestone features, not technology novelty.

**Core technologies:**
- Java 21 + Spring Boot 3.5.x: stable near-term platform baseline with lower migration risk than immediate Boot 4.
- Spring MVC + Thymeleaf + HTMX 2.x: supports richer interactions (bulk actions/contextual menus) without SPA rewrite cost.
- Spring Data JPA + Flyway 11.x: keeps domain/repository model intact and enables deterministic schema evolution.
- SQLite now + PostgreSQL 18 target: pragmatic path from single-node simplicity to multi-user write concurrency.
- Testcontainers PostgreSQL + Actuator/Micrometer: mandatory confidence layer for migration correctness and performance regression detection.

### Expected Features

Feature research is clear: this milestone is primarily a correctness-and-velocity upgrade around multi-account semantics plus high-frequency transaction workflows.

**Must have (table stakes):**
- Multi-account baseline (account entity, transaction/account linkage, account-aware reads).
- Account-specific and aggregated overview/transaction filtering with correct balances.
- Bulk transaction workflows (multi-select + safe scoped actions such as recategorize/soft-delete).
- Consistent action model including rule creation/extension directly from transaction context.
- Versioned rule export/import with user-readable validation errors.

**Should have (competitive):**
- Account-scoped plus global rule priority model with transparent precedence.
- Extended bulk workflows (preview/undo-friendly behavior and stronger conflict handling).

**Defer (v2+):**
- Autonomous ML categorization (keep deterministic rule engine).
- Cloud sync/marketplace-style rule sharing (security/operations overhead too high for this cycle).

### Architecture Approach

Architecture research recommends preserving feature-first packaging and introducing an explicit `accounts/` boundary as the new system invariant. Controllers remain thin, services own account context resolution and orchestration, repositories enforce account-scoped queries with soft-delete filters, and templates consume DTO/record projections rather than entities. Rule portability belongs inside the `rules/` boundary as a versioned contract, not ad-hoc serialization.

**Major components:**
1. Presentation layer (Spring MVC + Thymeleaf/HTMX) - route handling, account selection propagation, partial/full rendering.
2. Application services - account-scoped orchestration for imports, transactions, rules, and categories.
3. Persistence/domain layer - account-aware repositories, migrations, and entity integrity constraints.
4. Cross-cutting security/validation - ownership checks, sanitized errors, and transfer schema validation.

### Critical Pitfalls

1. **Account scope leakage** - enforce `(userId, accountId)` in service/repository signatures and isolation tests before merge.
2. **Unsafe financial migrations** - use additive, idempotent backfill with reconciliation gates and staging replay.
3. **Rule portability ambiguity** - ship strict versioned JSON contracts with migration adapters and compatibility fixtures.
4. **Bulk workflow integrity failures** - implement atomic bulk commands with version/conflict checks and per-item reporting.
5. **Security regressions (data exposure + cross-account auth gaps)** - sanitize UI/log error paths and add negative ownership tests.

## Implications for Roadmap

Based on combined research, the roadmap should use dependency-first phasing.

### Phase 0: Security and Guardrails Foundation
**Rationale:** Prevents irreversible trust and compliance regressions before feature velocity increases.
**Delivers:** Ownership-guard service contract, negative auth matrix, sanitized error/logging standard, baseline observability.
**Addresses:** Security constraints required by all milestone features.
**Avoids:** Data exposure and cross-account authorization pitfalls.

### Phase 1: Multi-Account Data Model and Access Invariants
**Rationale:** Every user-visible feature depends on account-correct storage and query semantics.
**Delivers:** Account entity/table, account links/backfill migrations, account-scoped repository/service APIs, backward-compatible default account behavior.
**Addresses:** Multi-account baseline, account-specific balances/filters.
**Avoids:** Scope leakage and migration-sequence failures.

### Phase 2: Account-Aware Performance and Rule Portability Contract
**Rationale:** Stabilize correctness at scale before adding high-interaction workflows.
**Delivers:** DB-level filtering/pagination/indexing, latency baselines, versioned rule export/import schema + validators + compatibility tests.
**Addresses:** Rule portability P1 feature and multi-account query scalability.
**Avoids:** In-memory performance collapse and non-deterministic rule behavior.

### Phase 3: Transaction Workflow UX Expansion
**Rationale:** UX acceleration is safest after account and contract primitives are hardened.
**Delivers:** Multi-select bulk actions, contextual action menus, rule-from-transaction flows, conflict-safe server-confirmed HTMX updates.
**Addresses:** Bulk workflows, consistent action UX, direct rule creation/extension.
**Avoids:** Partial/unsafe bulk updates and hidden cross-account side effects.

### Phase 4: Priority Extensions and Hardening
**Rationale:** Add high-value complexity only after production behavior is stable.
**Delivers:** Account-vs-global rule precedence UX, advanced preview/undo bulk operations, expanded regression/performance/security suite.
**Addresses:** P2 differentiators and long-term operability.
**Avoids:** Premature complexity without observability and test coverage.

### Phase Ordering Rationale

- Multi-account invariants are foundational dependencies for transactions, reports, bulk actions, and import/rule features.
- Security and migration safety must precede high-volume workflows because financial data errors are costly and hard to reverse.
- Rule portability should be introduced only after category/account semantics are stable, otherwise transfer contracts churn.
- UX-heavy phases come later to minimize rework and regression risk from shifting domain boundaries.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** Migration rehearsal strategy and rollback checkpoints for SQLite-to-PostgreSQL targetability.
- **Phase 2:** Rule transfer conflict-resolution UX and schema evolution policy (v1->v2 adapters).
- **Phase 3:** Bulk concurrency semantics (optimistic locking/preconditions) and HTMX conflict presentation patterns.

Phases with standard patterns (can likely skip `/gsd-research-phase`):
- **Phase 0:** Service-layer ownership checks, log redaction, and auth negative tests are well-established practices.
- **Phase 4:** Test/observability hardening patterns are mature and already aligned with stack recommendations.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Backed by official Spring/SQLite/PostgreSQL/Testcontainers/Playwright sources and pragmatic migration path. |
| Features | HIGH | Derived from active milestone scope and codebase concerns with clear dependency mapping. |
| Architecture | HIGH | Strong alignment with existing monolith seams and explicit boundary recommendations. |
| Pitfalls | HIGH | Directly tied to known brownfield risks with concrete prevention and verification criteria. |

**Overall confidence:** HIGH

### Gaps to Address

- Migration execution detail: define exact reconciliation metrics (balances, counts, category assignments) and go/no-go thresholds per environment.
- Authorization centralization: decide single enforcement point (service guard vs resolver) and make it non-bypassable.
- Rule transfer semantics: finalize conflict policy (merge/skip/replace) and user-facing explanations before implementation.
- Bulk operation auditability: confirm required audit granularity (who/when/which rows) for support and recovery workflows.

## Sources

### Primary (HIGH confidence)
- `.planning/research/STACK.md` - stack strategy, versions, and external official-source validation.
- `.planning/research/FEATURES.md` - table stakes, differentiators, anti-features, and dependency graph.
- `.planning/research/ARCHITECTURE.md` - boundaries, build order, and architecture patterns.
- `.planning/research/PITFALLS.md` - risk catalog, phase mapping, and prevention checks.

### Secondary (MEDIUM confidence)
- `.planning/PROJECT.md` - active milestone scope and constraints.
- `.planning/codebase/ARCHITECTURE.md` - current structural baseline.
- `.planning/codebase/CONCERNS.md` - existing fragilities and risk context.
- `.planning/codebase/STRUCTURE.md` - package and boundary reference points.
- `.planning/codebase/TESTING.md` - testing baseline and known gaps.

### Tertiary (LOW confidence)
- None identified beyond the above documented sources.

---
*Research completed: 2026-02-22*
*Ready for roadmap: yes*
