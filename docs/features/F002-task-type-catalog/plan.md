# F002 implementation plan

Applies to [specification revision 3](spec.md). Revisions 1 and 2 implementation/evidence are retained. Revision 3 concrete-service simplification and fresh verification are complete, with separate results in [evidence.md](evidence.md).

## Implementation sequence

1. Read AGENTS, constitution/constraints/assumptions, coding standards and F001/F002. Confirm the F001 catalog schema and changelog checksums are unchanged. Inspect current scanning/POMs rather than adding duplicate infrastructure.
2. Follow [exact F002 assignments](../../package-structure.md#exact-f002-assignments): consolidate TaskTypeCatalog/JpaTaskTypeCatalog into concrete TaskTypeCatalogService in `core.service.tasktype`, with the two package-private helpers beside it. Remove the interface and obsolete `impl` package. Keep DTO/exception/entity/repository assignments unchanged. Update declarations, imports, injection sites, Javadoc and README usage together. Preserve all method/transaction behavior, root scanning and Spring proxy compatibility; avoid final service classes/methods that prevent class-based transaction interception. Do not instantiate the service directly at call sites or introduce self-invocation that bypasses transactional entry points.
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
| CON-12; ASM-08 | PASS: authorized build/startup/read-only inspection recorded in evidence, no automated tests or catalog fixtures; behavioral execution remains NOT RUN. |

## Completion boundaries

Revision 3 implementation: TaskTypeCatalogService is the single supported, non-final Spring service. Interface Javadoc and operation documentation were moved onto it; method bodies and transaction annotations are unchanged. The former interface, JpaTaskTypeCatalog and impl package were removed, with helpers package-private beside the service. Fresh startup observed a CGLIB service proxy and the intended transaction metadata, alongside repository/entity discovery and schema validation. This does not execute CRUD, concurrency or audit rollback.

Historical revision 2 relocation: all 14 types used the then-current package assignments (the service interface/impl pair is superseded by revision 3), without compatibility facades or duplicate source copies. The entity's creation constructor, replaceMetadata and snapshot methods are public so the separately packaged service can use them; its Javadoc retains the unsupported-integration boundary. The protected JPA constructor and package-private service helpers remain unchanged. No mapping, query, transaction, validation or error-handling behavior changed. WorkflowConfiguration and host scanning remain rooted at the original core marker.

Historical revision 1 implementation (package placement superseded by revision 2): the supported catalog interface/records live in `core.catalog`; the entity, narrow Spring Data read repository and managed implementation live in `core.persistence.catalog`. Mutations use EntityManager.persist/flush and loaded managed entities, with no repository save/merge/delete surface. Hibernate @Nationalized maps the two NVARCHAR fields without host-global configuration; direct Hibernate and Spring Data Commons dependencies use existing parent-managed versions. Translation inspects Jakarta/Spring/Hibernate cause chains and the named code unique constraint, including SQL Server 2627 details. The effective Hibernate 6.6.13.Final exception hierarchy and SQL Server constraint extraction were inspected locally; execution of failure/race paths remains NOT RUN.

Implementing the library API and documenting its host preconditions completes the code scope. Production administrative exposure depends on real authorization and transactional audit adapters, which are outside F002. A successful build/host startup may not be reported as proof of AC01–AC09 behavioral guarantees. Keep pending evidence explicit rather than adding scope to make every scenario appear passed.
