# Plan — F008 Restricted expressions and exclusive routing

**Owner correction — 2026-11-05:** expression arithmetic follows native SpEL numeric behavior, including floating-point approximation, promotion, rounding and integral overflow. Do not reject a result solely for precision loss or compare it with an exact reference calculation. Declared DECIMAL variables enter evaluation as BigDecimal; decimal literals retain native SpEL types. Native evaluation errors and resource limits still fail the command. Exact decoding, transport, persistence and accepted-submission rules remain unchanged. This supersedes earlier expression-arithmetic exactness wording and the misinterpreted numeric owner decision.

**Status:** IMPLEMENTED; T08 acceptance remains open. Applies to [spec revision 3](spec.md). G01–G03 contracts are resolved with the owner's native numeric correction. Follow [package structure](../../package-structure.md), [coding standards](../../coding-standards.md) and [development workflow](../../specification-driven-development.md).

## 1. Dependency and contract preparation

Use implemented F007 revision 3, the owner-corrected native numeric contract and the finalized G01–G03 contracts in the specification. Preserve scalar input/storage exactness, VARIABLES_WRITTEN batches, retained VARIABLE_WRITTEN history and strict legacy receipts. No numeric accuracy checker or literal rewriting is part of this plan.

The readiness investigation established the coherent Boot 3.4.5/Spring 6.2.6 baseline and a local pre-execution guard mechanism. Later work completes the production evaluator and integration checks. Planned checks are below; actual commands, results and historical failures are recorded only in implementation.md. The readiness execution clarification permits the evidence-backed READY transition and implementation without another approval.
## 2. Package assignments

### T01 selected approach

Keep a separate routing structural schema/validator context so older publication evidence remains stable. Use receipt schema 2, raw authorized history and traversal that returns the reached human/END node. No migration. Reconstruct only allowlisted stock AST nodes from immutable plans, with local guards, direct scalar-read leaves and fresh per-evaluation state. Compilation/auto-growth remain disabled. NumericWork bounds allocation/work and leaves numeric results/conversions to SpEL; an OperatorOverloader is not used as an accuracy hook.

Cache at most 128 immutable plans. Charge source/AST/value-dependent work, numeric/string allocation estimates and command evaluation/automatic-step allowances as specified. The verified evaluator version is fixed at 6.2.6; other versions require compatibility verification. Declare direct Spring Core API use in core without changing managed versions or global settings.
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

Implement revision 3's finalized AST/type/allocation/cache rules directly. Cache immutable plan descriptions, create fresh guarded stock nodes per evaluation and evaluate eager operator operands once, charge bounded operand work and use native execution. Leave short-circuit/conditional/Elvis control flow with stock delegates. Use a strict scalar converter and direct scalar-read leaves; never native reflective/index fallback. Preserve native numeric promotion, rounding, floating-point behavior and overflow; NumericWork does not calculate a reference result or reject accuracy loss. Static mixed-type conditional branches can require runtime result checks. No new arithmetic grammar or literal rewriting.

Use `exception.expression.ExpressionException` classifications INVALID, LIMIT, EVALUATION, UNINITIALIZED and UNSUPPORTED internally, translated to the selected definition/runtime errors. `service.expression.ExpressionAllowance` is the documented per-command 500-evaluation allowance, with no singleton state. Add golden isolated suites for access/safety/type/numeric limits and concurrent cache reuse; route/graph/legacy/evidence suites; guarded host integration fixtures for committed results, rollback and overlapping completions. Reuse the current SQL Server test harness and test-owned cleanup.

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
| AC08 | Native arithmetic/temporal/null matrix, promotions, overflow results and native errors. | Stored F007 typed values route consistently after SQL round trips. |
| AC09 | Structural and real operation/evaluation counters, shared limits, cache isolation/eviction. | Over-budget command rolls back earlier participating effects. |
| AC10/AC11 | Error propagation and zero evaluation/actions on replay. | Real rollback/overlapping completion and one committed result/effect set. |
| AC12 | Event schema/order/read privacy and envelope/cumulative bounds. | Durable history/outbox/inbox consistency and replay counts. |
| AC13 | Graph validity, mandatory-human dominance, cycles/reachability/default rules. | Publication accepts valid branches and rejects invalid graphs with located diagnostics. |
| AC14 | Frontend evidence/recovery/session and graph fallback. | Packaged browser navigation, roles, selected route/result and exact input. |

Inspect discovery and select isolated suites explicitly. Do not use a database Spring context for expression tests. After implementation, build with JDK 21/Java 17 and package the console through the existing profile. Database integration tests on `localhost:1433/apolo_workflow_test` have standing authorization under AGENTS.md and must use the guarded host harness; other targets require task-specific authorization. Manual runtime checks need authorized scope, independent persisted observations and genuinely overlapping requests. Stop identified temporary processes afterward unless the user requests otherwise.

## 5. Storage and handover

No schema change is currently planned: gateways live in pinned JSON and history event types are strings. G03 confirms sizes and actual F007 reader/receipt compatibility before implementation. Never rewrite applied changelogs or seed effects at startup.

The implementation record reconciles delivered scope, current checks and remaining acceptance. Build success alone proves neither expression safety nor transactional behavior. Record outstanding acceptance and process state; commit/push only when requested.
