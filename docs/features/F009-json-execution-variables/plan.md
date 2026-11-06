# Plan — F009

Targets [specification revision 3](spec.md). Implementation was authorized by the owner on 2026-11-06. Specification gates were closed before T03–T06. This file records the intended approach; actual results are in implementation.md.

## P01 — Finalized contracts

Completed by [C01–C07](contracts.md): unified typed values and raw input literals, private scale-preserving Jackson codec, bounded AST reads/equality, inline evidence, existing read endpoints and additive history constraint setup. These contracts supersede the former open engineering questions in D01–D17 and this plan. No compatibility readers or data conversion are required. T02 supplied the implementation gate; the subsequent owner request authorized T03–T06.

## P02 — Extend existing layers

D05 selects explicit per-access null-safe property traversal (`?.`); ordinary property access on a null receiver fails. Preserve D04 missing-property projection on non-null objects and reject uninitialized variable reads. Cover multi-step chains and nullable variable roots; D06 makes out-of-range integer array reads return null without reverse indexing or mutation. D07 allows computed numeric whole-number indexes without coercion/truncation; include their work and actual reads in the enclosing evaluation. C03 fixes range-before-conversion behavior; D09 selects standard null-safe indexing and requires integration verification.

D03 selects BigDecimal for every JSON numeric leaf, with exact parsing and no coercion of numeric strings. Preserve existing scalar projections and native SpEL literal/arithmetic semantics. D12 requires scale preservation across write, reload, fingerprint, reads/evidence and evaluation. C02 specifies exact parser/tree behavior and serialization, including exponent forms; capacity checks must not strip scale.

Use the root `io.github.leontecristian.workflow.core` and exact packages `dto.runtime`, `service.runtime`, `exception.runtime`, `dto.definition`, `service.definition`, `dto.expression`, `service.expression`, `entity.runtime` and `repository.runtime`. Replace scalar-only value/binding/read APIs as needed with one coherent scalar/JSON model, updating callers together; no compatibility shims are required (D17). Use focused concrete helpers only when needed, without mirrored interfaces or new modules. Keep Spring parser/AST implementation details inside service.expression. Keep WorkflowConfiguration as the scanning marker.

Use WorkflowVariableEntity/Repository and RuntimeVariables/RuntimeJson responsibilities, revising their contracts as needed. Persist complete replacements explicitly. D15 needs a trusted ISJSON/history DATALENGTH ceiling of 4,194,304 bytes, not a new json_value column. C05 selects an additive 003-json-execution.xml changeset without old-data conversion tooling. Core still owns workflow schema and hosts run setup; host fixtures stay outside the core master. New evidence and receipts must remain readable/replayable for the new baseline, with no pre-F009 readers.

D15 fixes capacities for the new D17 baseline; do not implement separate old scalar limit profiles. Keep selected receipt/handoff capacities and metadata-only delivery. Expand history detail envelopes and retain byte/item pagination for list reads. Align core codecs and host parsers/start-route limits without changing global host configuration. The preparation phase changed documentation only; the subsequent implementation is recorded separately.

## P03 — Wire execution and demonstration

Integrate declared start/service JSON with existing mapping batches, instance claims, host transactions and routing snapshots. Add only allowlisted detached JSON traversal to the evaluator. Extend authorized projections/history and metadata-only handoff. Preserve exact scalars and native SpEL arithmetic.

Under `io.github.leontecristian.workflow.testhost`, extend existing `service.sandbox`, `service.runtime`, `dto.runtime`, `dto.sandbox`, `controller.sandbox` and `controller.runtime` as applicable, plus the existing console frontend. C01/C06 define the affected contracts; existing endpoints are reused. A sample DTO-derived JSON input and transactional service replacement demonstrate the feature without creating an APOLO dependency or expression provider.

## P04 — Planned verification

D15/AC14 requires maximum and one-unit-overflow checks for each envelope, 256 KiB start/default/action/read paths, full old/new batches, Unicode/escaping, exact scale, 1 MiB resulting state, maximum history detail responses and summary pagination, 16 MiB cumulative rollback and new-baseline scalar/JSON bounds. Inspect SQL constraint identity/trust and ISJSON after the selected schema setup on the specifically scoped test target. Actual checks, outcomes and remaining gaps are recorded in implementation.md.

- Unit-only codec/default/mapping tests: R01–03, AC01/02/06/09/10; duplicate rejection, exact numbers, normalization, immutable copies, scalar functionality and inclusive bounds.
- Unit-only expression safety/behavior tests: R04/05, AC03–05/12; all navigation/null/type cases, skipped reads, malicious ASTs, metadata keys, work/allocation exhaustion and F008 golden behavior.
- Guarded SQL Server integration on localhost:1433/apolo_workflow_test: R06–08, AC07–11; real null/round trips, same-transaction visibility, final version, competing replacements, rollback after host effects/evidence failure and replay. Serialize database runs and preserve unrelated data.
- JDK 21/root Maven wrapper compilation and packaging, plus test-host browser walkthrough for exact JSON entry, authorized inspection, refresh/replacement and routing evidence. Record these separately; unit/build success does not prove transaction/provider/UI behavior.
- New-baseline definition, receipt and coordinated caller integration tests and evidence-budget boundary cases. Run default unit discovery only as documented; database tests use the guarded host integration profile, never UnitTest names.

Handoff review additions: verify no-row versus initialized-null persistence; history version 2 across pin loading, completion/recovery and exact task reads; typed Java values versus raw HTTP tokens; envelope-relative depth/count limits; bounded raw factory input; negative fractional indexes; missing-to-null-safe trace origins and skipped computed-index reads; repeated distinct paths from the same variable; and full-size history detail decoding without the old 128 KiB helper ceiling. These are acceptance checks within AC03/04/06/10/11/12/14, not new product scope.

Actual results belong in [implementation.md](implementation.md). The subsequent implementation request authorizes builds, isolated tests and the guarded test database; no development database startup or reset is authorized.
