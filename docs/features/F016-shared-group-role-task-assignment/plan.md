# Plan — F016

**Revision:** 2 — 2026-11-07. Applies to [specification revision 2](spec.md), IMPLEMENTED. Implementation authorized by the owner on 2026-11-07.

## P01 — Apply closed contracts

O01–O05 are closed in specification revision 2. Follow its exact DTO/provider, once-per-command membership decision, bounded candidate scan, current business-access/own-receipt rules and versioned evidence migration. Existing code was inspected during readiness; implementation must reconcile any later source changes before edits. The subsequent owner request authorizes T03–T05.

## P02 — Core and persistence

Exact package destinations beneath `io.github.leontecristian.workflow.core`: `dto.runtime` for detached target/context/view contracts; `service.runtime` for resolver/eligibility integration and execution/query logic; `entity.runtime` and `repository.runtime` for storage/query adaptation; `exception.runtime` for affected errors. Definition capability validation remains in existing `service.definition` / `dto.definition` packages where required. Use concrete services; a host eligibility interface is justified by host-supplied identity policy, not by mirroring a service.

Review `ResolvedTask`, `RuntimeHostSupport`, `WorkflowExecutionService`, `WorkflowRuntimeQueryService`, `WorkflowTaskEntity`, repository queries, history mapping and receipt authorization together. Core must enforce shared eligibility even for direct library callers. Preserve transaction ownership and the early checked aggregate write. Implement the specified 004 migration under core's existing `db/changelog/workflow/` master, preserving applied identities and old USER evidence. Include strict v2/v3 creation-history and v3/v4 receipt readers, and v1/v2 creation-event delivery. No host identity tables or security objects enter core contracts. Covers R01–07/R09.

## P03 — Host, HTTP and UI

Use existing `io.github.leontecristian.workflow.web.integration.runtime`, `web.controller.runtime` and `web.dto.runtime` packages, plus `io.github.leontecristian.workflow.web.service.runtime` for existing wire/error mapping. Host adapters stay in `io.github.leontecristian.workflow.testhost.service.runtime` with existing security ownership. Extend the sandbox's membership fixture only as needed for AC02–04; no group-management product.

Update existing workflow-ui runtime models/components and sandbox adapters to show target/eligibility, preserve read-only consumption, and handle completion conflicts. Use O03's 500-candidate bounded keyset query and existing source-continuation semantics. Extend the task contract with canComplete and assignmentLabel. No claim buttons or reserved state. Add the task component diagnosticsEnabled input (default false); keep diagnostic recovery navigation opt-in in the sandbox. Normal success refreshes the task rather than navigating to recovery. Covers R05/R07/R08.

## P04 — Focused verification

Fresh checks are justified by assignment, authorization, storage and completion changes: isolated target/eligibility/query/DTO/UI checks; guarded SQL Server migration, concurrent shared completion and affected rollback/replay compatibility scenarios; one focused shared-task browser observation using prepared fixtures, covering normal completion and sandbox diagnostic opt-in. Exercise before/after-check revocation, own-receipt recovery after removal, business-access denial and a bounded empty page with continuation. AC01–07 define the boundaries, not a full engine acceptance campaign.

Candidate REUSED evidence: F015 revision 2 handover for unchanged shared transport/read-only composition and diagnostic-page internals (not the changed task-page diagnostic visibility/navigation); F011 revision 1 for unchanged sequential progression. Confirm applicability against final changes before recording reuse. Reuse does not establish new membership or migration behavior. Keep old owning-feature gaps there. Use localhost:1433/apolo_workflow_test only for authorized integration checks and stop owned temporary processes afterward.

The prior documentation-only check is historical. This implementation finishes when the scoped requirements and focused checks pass, with actual outcomes and remaining gaps recorded in implementation.md. No repeated builds or broad workflow walkthroughs without a concrete changed boundary or failure.

## Execution reconciliation

The owner implementation request supersedes readiness-only authorization notes. The focused checks above were selected; no unrelated full-engine/browser campaign was added. The SQL Server interruption and restored run are retained in implementation.md. A final queued v1/v2 creation-delivery check was added because this evidence boundary changed; it repeats only the affected compatibility scenario.

Chrome follow-up: reproduce the observed viewer GET-pattern/host start-POST collision in an isolated MVC context, correct only the shared viewer mapping fallthrough, and repeat its affected unit suite. Reuse the existing server for Chrome; restart it from IntelliJ only under the owner’s subsequent explicit authorization. Preserve the original frozen start command and retry it after the fix.

Frontend cache follow-up: use retained task41 for current-bundle rendering and completed-submission access. Do not replay the shared workflow solely to obtain viewer state. If the restarted dev server retains the pre-F016 optimized dependency, stop only its existing IntelliJ frontend run under explicit authorization, clear the generated frontend .angular/cache after checking its resolved path, restart that same configuration, and inspect port4200. Reuse prior completion/membership evidence.
