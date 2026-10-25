# F002 implementation plan

Applies to [specification revision 1](spec.md). Implementation NOT RUN.

## Implementation sequence

1. Read AGENTS, constitution/constraints/assumptions, coding standards and F001/F002. Confirm the F001 catalog schema and changelog checksums are unchanged. Inspect current scanning/POMs rather than adding duplicate infrastructure.
2. Place the supported service and immutable commands/results in a focused package below `io.github.leontecristian.workflow.core.catalog`. Place JPA models/repository implementation under the core persistence boundary. Choose ordinary class names internally without adding interfaces for every class or a generic CRUD framework.
3. Map only `wf.workflow_task_type`: six columns, generated BIGINT IDENTITY, explicit VARCHAR code, Unicode name/description mapping, BIT selectable, BIGINT/Long @Version. Match F001 lengths/nullability/schema. Hibernate must validate NVARCHAR correctly; follow existing provider capabilities without changing host global settings. No F001 rewrite, new migration, default, trigger, entity relationships or task/definition entities are needed.
4. Limit repository operations to lookup, bounded query, persistence and managed updates. Do not expose an inherited delete/save API as the supported catalog API. Keep the entity free of unrestricted ID/code/version setters, blanket Lombok @Data, mutable-state equality and repository leakage.
5. Implement the exact validation, read/query and mutation contracts. Use database-level bounded filtering/order/cursor predicates, immutable copied collections and limit+1 lookahead. Update a loaded managed entity; capture the before snapshot before changes and after snapshot after flush. Compare full mutable values for no-op detection.
6. Enforce host-owned transactions for mutation, including proxied entry points; reads join an existing transaction or run read-only. Flush inside the mutation boundary to surface ordinary uniqueness/optimistic conflicts and obtain generated/provider values. Translate recognized failures without querying or retrying inside an aborted transaction. Inspect effective provider exception wrapping. Do not catch arbitrary errors and turn them into DUPLICATE_CODE.
7. Document the host integration pattern in public API documentation and a concise repository usage example: authorize trusted actor, host transaction, core call, durable before/after management audit, host commit, external response. The example may be conceptual and must not invent an existing audit API. No development security bypass, new endpoint, audit table or log-only audit sink.
8. Follow [verification.md](verification.md), reconcile tasks/evidence and report the actual scope. Build/startup validates wiring/mapping only; leave unexecuted mutation/race/audit scenarios visible.

## Transaction integration shape

```text
Trusted host administration boundary
  authenticate + authorize
  begin host transaction
    core catalog.create/update (requires transaction)
      validate -> persist/versioned update -> flush -> immutable change
    persist host audit when changed=true
  commit host transaction
  return success

Any failure -> rollback; no independently committed core write
```

This does not supply production host authorization/audit. Failed transaction translation/commit handling belongs to the outer host coordinator. A returned change is not evidence of commit. Catalog management does not borrow workflow instance receipts/history.

## Constitution check

| Reference | Assessment for this plan |
| --- | --- |
| CON-01/02; ASM-01/09 | PASS: same two modules, Java 17 target/JDK 21 runtime and current dependency baseline. No reparenting or production compatibility claim. |
| CON-03/10 | PASS: trusted Java boundary, neutral DTOs, host identity/authorization/audit, no core endpoint or security-provider dependency. Actual host integration remains deferred. |
| CON-04 | PASS: consume F001 table; preserve all applied migrations, one host runner/datasource and JPA validate. |
| CON-05/08; ASM-05 | PASS: bounded catalog slice, permanent codes, editable metadata and authoring-only selectability. No business versions, outcomes or runtime capability claims. |
| CON-06/07; ASM-02/03 | PASS for catalog scope: host transaction and audit handoff, version-checked writes, no auto retry or runtime-receipt misuse. Host audit atomicity and provider race behavior need separate execution evidence. |
| CON-09/11/13/14 | NOT APPLICABLE to implementation: no definition/expression/designer/submission/variable behavior; their existing boundaries remain intact. |
| CON-12; ASM-08 | PASS: documentation checks now; later authorized build/startup/read-only inspection, no automated tests or silent catalog fixtures. |

## Completion boundaries

Implementing the library API and documenting its host preconditions completes the code scope. Production administrative exposure depends on real authorization and transactional audit adapters, which are outside F002. A successful build/host startup may not be reported as proof of AC01–AC09 behavioral guarantees. Keep pending evidence explicit rather than adding scope to make every scenario appear passed.
