# Plan — F008 Restricted expressions and exclusive routing

**Status:** DRAFT. Applies to [spec revision 1](spec.md). G01–G03 gate dependent implementation. Follow [package structure](../../package-structure.md), [coding standards](../../coding-standards.md) and [development workflow](../../specification-driven-development.md).

## 1. Dependency and contract preparation

F007 was READY at the initial inspection; concurrent implementation edits appeared during preparation, without verified completion in this task. Compare its eventual code/handover to the selected contracts before modifying core. Reuse its variable store, service registry, command codec and host sample. Preserve unrelated changes and historical evidence.

Resolve G01 with an explicit AST/operator allowlist and per-type semantic matrix. Identify every scalar entering SpEL and every accessor/resolver/conversion path. Include adversarial syntax, short-circuit/uninitialized access and arithmetic boundaries. Reject unknown AST nodes after dependency upgrades. Retain standard semantics for supported forms; reconcile necessary restrictions with the language contract before dependent code.

Resolve G02 by inspecting the effective dependency graph and actual parser/evaluator implementation. The POM declares spring-expression under Boot-managed versions. Do not assume a newer reference API exists in this runtime. If enforcement needs an upgrade, specify a coherent Spring baseline, Java 17 target/JDK 21 runtime and compatibility obligations. This draft performs no build or dependency download. Any later evaluator spike uses isolated tests without database contexts.

Resolve G03 with immutable DTOs, event/error schemas, validation-context identity and golden fixtures. Preserve the sole-human result shape and strict legacy receipt reader. Do not enable runtime evaluation before readiness contracts are complete.

## 2. Package assignments

Core root: `io.github.leontecristian.workflow.core`.

| Exact package | Responsibility |
| --- | --- |
| `service.expression` | Concrete restricted compiler/evaluator, AST inspector, read-only accessors, local budgets and bounded plan cache; no mirrored service interface/global context. |
| `dto.expression` | Immutable condition/evaluation/read-evidence contracts finalized at G03; keep Spring AST/context types internal. |
| `exception.expression` | Internal evaluation classifications mapped into documented runtime/definition failures. |
| `dto.definition.document` / `service.definition` | Gateway/condition/default model, structural schema, graph/profile validation and pinned loading; preserve order/source. |
| `service.runtime` / `dto.runtime` / `exception.runtime` | Gateway selection, shared command budgets, history/handoff, result integrity and public errors using F007 codecs. |

Host root: `io.github.leontecristian.workflow.testhost`. Extend existing `service.runtime`, `dto.runtime`, `controller.runtime` and `service.console` only as needed for authorized evidence/error presentation. Reuse F007 `service.sandbox`; add no expression execution endpoint or client fact provider. Frontend changes stay under `src/app/data` and `pages/runtime`. Existing raw JSON editing can author conditions; there is no graph editor or frontend evaluator in this feature.

## 3. Validation and runtime design

Validate graph/route combinations separately from expression compilation. Conditional priority comes from filtering the original transition array; exclude default from evaluation order. Validate all source without invoking host actions or evaluating business state. Preserve immutable JSON and validation-context hashes; cache plans without rewriting source.

After receipt resolution and the existing aggregate-claim protocol, charge one automatic step before entering a gateway. Build a detached typed view with explicit declared absence. Evaluate sequentially against one immutable snapshot and shared command allowance; record only actual read accesses/results. Stop on true. All false selects default or raises no-route; evaluation error aborts without fallback. No catch-to-false conversion.

Emit routing evidence then TRANSITION_TAKEN in the same bounded history/event batch. Capture the final managed version and preserve handoff privacy. Receipt replay never enters the evaluator. Extend the F007 result verifier for gateway topologies without loosening old fixed-sequence readers.

Use structural denials plus restrictive context/accessors; disable auto-growth and compiler bypasses. Cache only immutable validated plans, never per-command values/counters. Cache isolation must hold under parallel calls. Operation counters must constrain actual work, not merely report it afterward.

## 4. Planned verification

| Scenarios | Isolated verification | Runtime/browser evidence when authorized |
| --- | --- | --- |
| AC01/AC15 | Old/new receipt and profile golden cases across the selected dependency baseline. | Old/F007 workflows and receipts after deployment, unchanged migrations/content. |
| AC02/AC03 | First-match, overlap, default position, immutable order. | Three fixture quantities and separately published reordered version. |
| AC04/AC05 | No-route/non-Boolean/error, absence/null/short-circuit tracing. | Failed completion leaves open task/variables and no successful receipt. |
| AC06/AC07 | Unsafe syntax/access rejection, including later/skipped routes; no reflection/resolver fallback. | Publication diagnostics and denied reads, with no validation bypass. |
| AC08 | Exact arithmetic/temporal/null matrix, overflow/conversion failures. | Stored F007 typed values route consistently after SQL round trips. |
| AC09 | Structural and real operation/evaluation counters, shared limits, cache isolation/eviction. | Over-budget command rolls back earlier participating effects. |
| AC10/AC11 | Error propagation and zero evaluation/actions on replay. | Real rollback/overlapping completion and one committed result/effect set. |
| AC12 | Event schema/order/read privacy and envelope/cumulative bounds. | Durable history/outbox/inbox consistency and replay counts. |
| AC13 | Graph validity, mandatory-human dominance, cycles/reachability/default rules. | Publication accepts valid branches and rejects invalid graphs with located diagnostics. |
| AC14 | Frontend evidence/recovery/session and graph fallback. | Packaged browser navigation, roles, selected route/result and exact input. |

Inspect discovery and select isolated suites explicitly. Do not use a database Spring context for expression tests. After implementation, build with JDK 21/Java 17 and package the console through the existing profile. Database integration tests require explicit authorization; scenarios alone do not grant it. Manual runtime checks need authorized scope, independent persisted observations and genuinely overlapping requests. Stop identified temporary processes afterward unless the user requests otherwise.

## 5. Storage and handover

No schema change is currently planned: gateways live in pinned JSON and history event types are strings. G03 confirms sizes and actual F007 reader/receipt compatibility before implementation. Never rewrite applied changelogs or seed effects at startup.

This task creates the four documents and navigation links, not F007/F008 code. Future implementation reconciles spec/plan/tasks/handover with actual results. Build success alone proves neither expression safety nor transactional behavior. Record outstanding acceptance and process state; commit/push only when requested.
