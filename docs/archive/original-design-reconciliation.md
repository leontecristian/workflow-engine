# Original design reconciliation

**Reviewed:** 2026-10-24. **Target:** [consolidated design v0.8](../architecture/workflow-engine-design.md). **Source:** [original technical design, sections 1–51](../archive/original-technical-design.md).

This record accounts for the original design section by section. It restores useful explanation and concrete examples while preserving subsequent F4/F5 and O1/O2/O6 decisions. It is a design comparison, not runtime verification. Exact contracts remain proposed where the consolidated design says so. No code, schema migrations or tests were created by this reconciliation.

Status meanings: **retained** means the idea already existed in the current design; **restored** means concrete explanation or a worked illustration was added back; **adapted** means it is expressed using the current architecture; **superseded/deferred** identifies an intentional departure. A section can have more than one disposition.

## Purpose, language and graph — original sections 1–11

1. **Purpose — retained/adapted.** Current sections 1–2 keep the small embedded, business-independent engine. Planning is a sandbox example; the first real integration is cross-team booking. Standalone business decisions are deferred.
2. **Design principles — retained/restored.** Current sections 2, 7.0 and 11 preserve document definitions and relational runtime. Section 7.0 now explicitly rejects a second authoritative normalized graph; section 11.5 restores the SQL Server-to-typed-definition path. Serialized event/snapshot evidence is not prohibited by the old shorthand “only definitions use JSON.” Timers remain deferred.
3. **Definition lifecycle — retained.** Section 10.2 preserves clone-to-new-draft, immutable publication and pinning. Validation belongs to an exact revision; retirement does not erase active references.
4. **Definition persistence — retained.** Sections 11.1–11.3 retain identity/version metadata, document storage, checksums and constraints. Physical schema names/types still require specification; core owns migrations and the host runs them.
5. **Canonical workflow language — restored.** Section 7.0 restores typed definition models and the dedicated codec boundary. Normal execution uses typed objects rather than raw JSON-tree navigation. Type names remain illustrative.
6. **Core graph model — superseded/adapted.** Section 7.1 uses the five agreed node types. Generic STATE/FINAL are replaced; WAIT/TIMER/FORK/JOIN/SUBPROCESS remain deferred.
7. **STATE node — superseded/adapted.** HUMAN_TASK represents the initial waiting position, SERVICE_TASK represents automatic work, and business drafting remains in the host. Section 7.4 shows these responsibilities explicitly; a generic non-task wait node is not silently reintroduced.
8. **FINAL node — adapted/restored.** END terminates execution with a defined result. Sections 7.4 and 11.5 distinguish terminal business result, instance lifecycle and completion metadata. Final lifecycle encodings remain proposed.
9. **DECISION node — retained.** Sections 7.1, 7.3 and 7.5 retain automatic routing and no successful command stranded in a decision. The original minimum-outgoing-edge count remains a proposed graph rule, not an independently frozen contract.
10. **Transitions — restored/adapted.** Section 7.2 restores edge properties, permission/guard/comment requirements and node-specific behavior. Section 7.4 shows them together. Transition business actions move to SERVICE_TASK; event handoff follows section 13 rather than arbitrary executable edge hooks.
11. **Stable keys versus names — restored.** Section 7.2 explains keys, outcomes, labels/localization and coordinates separately. The example uses distinct edge keys for manager and partner APPROVE. Scope/grammar of keys and localization encoding require specification; a label-only edit still cannot mutate a published document.

## Evaluation, state and context — original sections 12–25

12. **Guards — retained/restored.** Sections 7.2 and 8.3 preserve registered checks and restore a structured blocking-reason example. A custom Java registration annotation is illustrative, not a framework requirement.
13. **Guards versus conditions — retained.** Sections 7.2–7.3 distinguish blocked commands, valid false predicates and evaluation failures. No failure becomes default approval.
14. **SpEL language — retained/strengthened.** Section 5 requires explicit #facts/#vars, allowed syntax, typed values and bounded read-only evaluation. Bare object-property expressions from the original are superseded.
15. **Expression branches — retained/restored.** Section 7.3 preserves exactly-one-match/default semantics. Section 7.4 restores a concrete expression route using review snapshots.
16. **Decision tables — deferred/superseded.** Section 6 preserves optional future guidance. Tables/FIRST are not initial dependencies, and later decisions return business values mapped by the workflow rather than directly owning transition identity. The worked example uses a single OR predicate because the old table's two rows had the same output; this is not a general replacement for priority tables.
17. **Automatic resolution — retained/adapted.** Section 7.5 traverses START/SERVICE_TASK/DECISION to HUMAN_TASK or END. Section 7.4 demonstrates this within command boundaries, with service-output validation and rollback.
18. **Loop protection — retained.** Automatic cycles and runtime bounds remain in sections 7.5/10.4. The number 50 is still proposed. The example's human-interrupted adjustment loop does not weaken automatic-cycle protection.
19. **Instance model — restored.** Section 11.5 restores fields, completion metadata and pinned lookup. Current node replaces current STATE; exact ID/status/token encodings are not chosen.
20. **Business association — retained.** Section 8.1 uses opaque business type/ID references with host-owned entities. No APOLO foreign keys or hard-coded numeric external IDs are required.
21. **Variables — retained/restored.** Sections 8.4 and 11.5 restore scalar declaration/initialization semantics and typed relational value-slot sketches. Nullability and physical encodings remain specification work.
22. **Domain versus workflow data — retained.** Section 8.1 preserves ownership; the complete example stores only review snapshots and leaves the planning form in the host.
23. **Live versus snapshot data — restored.** Section 8.2 restores the 520000/480000 example and provenance/history explanation. Live guards and captured routing facts have different purposes. Refreshing current variables cannot rewrite prior evidence.
24. **Variable declarations — restored.** Section 8.4 explains type declarations, defaults, permitted writers and editor support; section 7.4 provides declarations with service output bindings. Default values never mask invalid input.
25. **Workflow context — restored/adapted.** Section 8.3 restores a neutral command context and limited extension capabilities. Exact signatures remain proposed; expressions receive only scalar projections, not user/provider objects or engine services.

## Tasks, execution and persistence — original sections 26–38

26. **Human tasks — retained/restored.** Section 9 adds activity/versioned task-type semantics; section 11.5 restores task fields. Human tasks are in initial scope, with one assigned task per activity. Exact OPEN/COMPLETED/CANCELLED encoding remains proposed.
27. **Assignment — restored/narrowed.** Section 9.3 restores activation-time resolution and persisted assignment. Logical host resolvers resolve one direct assignee; ROLE does not imply candidate groups or claim/release. Missing assignees fail and reassignment is explicit/audited.
28. **Actions — superseded/adapted.** Registered synchronous actions remain, but live in explicit SERVICE_TASK nodes. Sections 7.4, 8.3 and 12 show typed bindings, single ownership and atomic domain/workflow effects.
29. **Events — retained/strengthened.** Section 13 distinguishes neutral workflow events from host domain events, durable recording from delivery, and operational logs from authoritative history. The example's companion host contracts explain where planning events originate; no unapproved event-hook JSON syntax is asserted.
30. **Execution semantics — retained/restored.** Section 12.2 retains the transactional sequence with idempotency/concurrency improvements. Sections 7.2/17 explicitly restore source-node/active-task checks; section 7.4 walks through start, review, automatic routing, finalization and failures.
31. **Transaction boundary — retained/strengthened.** Section 12 requires rollback of all mandatory effects, including receipt and handoff. Shared database naming alone is insufficient; flush/commit-time participation remains verification work.
32. **Concurrency — retained with open mechanism.** Section 12.3 requires optimistic protection. SQL Server ROWVERSION is proposed pending JPA/host verification, not a finalized mapping just because the original chose it.
33. **Idempotency — strengthened.** Section 12.4 makes it mandatory, adds fingerprints/access checks and committed-result semantics. The original “eventually” wording is superseded.
34. **History/audit — retained/restored.** Sections 11.5/13.2 restore fields, append-only command behavior and human-friendly versus technical timeline views. The worked example shows routing/snapshot evidence. Retention/redaction remain host policy, and append-only does not mean tamper-proof.
35. **Definition loading — restored.** Section 11.5 restores the loader seam, typed codec pipeline and command-local reuse. Subsequent commands load the exact pinned version; no newest-version fallback.
36. **No global cache — restored.** Section 11.5 restores the rationale and optional measured, bounded cache behind the loader. Human-scale traffic is explicitly an expectation rather than a verified capacity result.
37. **Definition validation — retained/restored.** Section 10.4 restores the staged publication checklist and node/binding/capability checks. The original “not on every execution” wording does not remove runtime authorization, input, guard, version or output validation.
38. **Complete workflow — restored/adapted.** Section 7.4 now contains the full proposed five-node JSON graph, bindings, registry/host prerequisites, snapshot routing, permissions, guards, comments, outcomes, layout and command walkthrough. Direct and partner paths share approval finalization. Request-changes/resubmission is explicitly a sandbox illustration; O3 remains provisional. Pre-start draft cancellation belongs to the host; in-workflow cancellation is shown at the requester task.

## Editor, integration and delivery — original sections 39–51

39. **Graphical editor — retained/restored.** Section 15 restores node/edge correspondence using five node types and retains the later designer milestone.
40. **Canonical editor/runtime model — restored.** Sections 7.0/15.2 explicitly connect the TypeScript model, JSON/API/storage and typed Java runtime. No independently evolving designer workflow language is introduced.
41. **Layout information — retained/restored.** Sections 10.4/15.2 preserve non-semantic layout; section 7.4 restores concrete coordinates keyed by node identity. Embedded layout is still immutable after publication.
42. **Visual/JSON editing — restored.** Section 15.2 restores synchronized editing and error handling without silently replacing invalid draft text or dropping unsupported semantics.
43. **Editor validation — restored/adapted.** Section 15.2 restores properties by node/edge type and field-level diagnostics. Actions belong on service nodes, not edges; decision tables and their UI remain deferred. Backend validation/authorization are authoritative.
44. **REST direction — retained/restored.** Section 17 restores an available-actions projection with task-type/outcome/comment metadata. Exact endpoints and envelopes remain proposed. Host business endpoints cannot be bypassed by generic workflow mutation APIs.
45. **Security — retained/strengthened.** Sections 5, 8–9, 15 and 17 retain host authentication/authorization and restricted expressions, adding explicit trusted actor, assignment, no-self-approval and active-task compatibility expectations.
46. **Java component structure — restored/adapted.** Section 3.4 restores internal components/packages inside core and synthetic host adapters. It does not reinstate separate persistence/expression/starter artifacts or a decision-table evaluator in initial scope.
47. **V1 exclusions — retained/expanded.** Sections 1.3/22 retain advanced orchestration exclusions and add the later decisions deferring business tables, candidate claiming, delegation and timers. No BPMN, arbitrary scripts, distributed workers or active-instance migration is reintroduced.
48. **Development phases — superseded.** Section 21 retains the newer sequence: contracts, sandbox with human tasks/reliability, cross-team APOLO integration, designer, hardening. Reliability is required for any earlier production release. The current owner instruction prohibits automated tests until changed; verification requirements remain documented.
49. **Architecture summary — adapted/restored.** Sections 2–4, 7.0 and 15.2 together describe embedded hosts, typed execution and persistence. Core is developed separately now and joins APOLO later; infrastructure and domain meaning remain host-owned.
50. **Key decisions — retained or explicitly superseded.** Section 22 is the authoritative register. Typed JSON, relational runtime, version pinning, snapshots and canonical editing remain. Old node names, early decision tables, transition-key business decisions and optional initial human tasks/reliability are superseded.
51. **Next design step — retained/narrowed.** Section 22.3 still requires formal Workflow Definition v1, Expression Policy v1 and registry contracts. The complete example supplies a concrete starting point, not finalized schemas/Java/TypeScript APIs. Decision-definition/table schemas remain deferred.

## Remaining specification work

The restored material makes the design concrete without closing O1/O2/O6/O7 by implication. Formalize JSON field names/key scopes, strict unknown-field behavior, action/fact binding contracts, input/default/nullability rules, numeric/date/time encoding, errors/limits, registry normalization and capability revision handling. Specify the persistence mappings and authorization/transaction integration before implementation.

Future authoritative feature artifacts belong under `workflow-engine/docs/features/<feature-id>-<name>/` in the source project. The complete example is currently embedded in the architecture document so it can be reviewed as a whole; do not maintain a divergent copied definition as if it were a published schema fixture.
