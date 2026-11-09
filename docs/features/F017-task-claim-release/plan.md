# Plan — F017

**Revision:** 3 — 2026-11-09. Applies to [specification revision 3](spec.md), READY. Planning only.

## P01 — Apply closed contracts

O01–O06 are closed in specification revision 3. Follow the exact definition policy, operation/authorization, inbox/access, history/event and migration/receipt contracts. READY is planning completion; implementation still requires a subsequent owner request. Recheck relevant source changes since baseline 981c175 before implementation. Ordinary implementation choices remain within these contracts.

## P02 — Definition and preserving persistence evolution

Within io.github.leontecristian.workflow.core use dto.definition.document and service.definition for policy parsing/model/validation and capability checks. Inspect DefinitionDocumentCodec and WorkflowDefinitionRuntimeReader, then locate their existing strict parser/validator collaborators. Preserve stored publication bytes and default omitted policy during interpretation.

Use entity.runtime.WorkflowTaskEntity and repository.runtime for assignee_ref and any query changes justified by O03. Append the next ordered migration under workflow-core/src/main/resources/db/changelog/workflow/ and include it from workflow-master.xml. Implement O06 migration 005-task-claim-release with exact identity checks and closed-task compatibility. Plan a coordinated writer upgrade; preserve existing assignment values, task identities and history. No change to host datasource ownership.

Initialize new USER tasks with assignee_ref equal to assignment_ref and new GROUP/ROLE tasks with null assignee_ref. Backfill existing OPEN tasks using the same distinction. Leave all already closed task assignees null under O06. USER-only and mixed-kind resolvers support either policy; REQUIRED needs explicit claiming only for the shared target returned at runtime. Record initial USER ownership in new creation evidence without a synthetic claim event or rewriting historical evidence.

## P03 — Runtime and host authorization

Use core dto.runtime for immutable commands/results and policy/read projections, service.runtime for concrete execution, eligibility, query and codec logic, and exception.runtime for categories. Extend WorkflowExecutionService through its existing transaction, early aggregate claim and receipt mechanisms. Review RuntimeJson, RuntimeHistoryBatch, WorkflowTaskEligibilityService and WorkflowRuntimeQueryService together. Claim/release must not call service progression or create replacement tasks.

Implement service.runtime.WorkflowTaskOwnershipAuthorization using existing dto.runtime.TaskEligibilityContext under O05. Require its business-access check for all new ownership commands and replay, and its administrative decision for new admin-release only. Core enforces claimant/state rules and requires the selected trusted authorization path for administrative release. The host supplies business policy, identity and transaction coordination. Keep original assignment eligibility separate from owner-release authorization so membership removal does not strand that permitted action.

Enforce Claim and Release only for GROUP/ROLE tasks at the core entry points, including administrator requests. Direct USER tasks complete through their initialized assignee under either policy. Shared tasks with an assignee enforce the same exclusive-completion rule. Keep current target eligibility and business access as additional checks.

## P04 — Shared web/UI and sandbox

Use existing io.github.leontecristian.workflow.web integration.runtime, controller.runtime, dto.runtime, service.runtime and configuration packages for adapters, scoped activation, mapping and errors. Review WorkflowRuntimeCommandHost/Controller and WorkflowRuntimeCommandConfiguration for claim/release and replay support without imposing command providers on read-only consumers.

Host adapters belong in io.github.leontecristian.workflow.testhost.service.runtime, including ConsoleRuntimeCommandHost and its authorization/coordinator collaborators. Use existing sandbox membership/security fixtures for owner, other member and authorized administrator. Host administration permission must not automatically become task completion permission.

Update workflow-ui/projects/workflow-ui/src/lib/runtime models, task component, command transport/buffering and the consuming workflow-test-host/frontend wrappers only where affected. Author policy through existing definition JSON editing/import. Implement the O03 existing-inbox union of eligible peers and current assignee, with business access, exact-task reads and bounded continuation.

Use assignee_ref for persistence and assigneeRef in DTO/wire under O04/O06. Present the individual as the assignee, whether established at creation or by claiming. Show Claim/Release only for shared tasks; USER task controls allow authorized direct completion under either policy.

## P05 — Focused verification and completion

Concrete review anchors from the existing implementation are RoutingDefinitionModel, MinimalDefinitionLoader and DefinitionValidationService with core resource schemas/workflow-definition/exclusive-routing-v1.schema.json. RuntimeTaskRow, RuntimeViews and WorkflowTaskView carry persistence projections. RuntimeJson.verifyResult currently assumes every non-start operation closes a task/activity, requiring operation-specific invariants for the new commands while preserving strict historical v3/v4 readers.

ConsoleRuntimeCommandHost currently calls ConsoleRuntimeAccess.task before command/replay. Its membership gate for OPEN tasks needs operation-appropriate business authorization for former-owner release and receipt recovery. Check exact task-instance linkage and ConsoleRuntimeCoordinator/ConsoleRuntimeTransaction recovery too. Review ConsoleViewerAccess before exposing new claim history.

Focused suite anchors are RoutingDefinitionUnitTest, RuntimeCommandsUnitTest, RuntimeReceiptUnitTest, WorkflowRuntimeWebUnitTest, ConsoleSharedAssignmentUnitTest and existing frontend task/adapter tests. SharedAssignmentIT supplies guarded database fixtures. New cases should address the changed boundaries below.

The changed boundaries are policy decoding, ownership persistence, commands/replay, authorization, discovery and task actions. Planned isolated checks cover policy defaults/rejections/pinning, state/permission combinations, serializers and UI actions. Select existing relevant suites first and add focused cases for uncovered AC01–AC08 behavior.

Fresh guarded SQL Server checks are justified for claim races, claim-versus-completion, stale release after reclaim, transactional rollback and changed receipts/migration. Use only localhost:1433/apolo_workflow_test via the guarded host harness, JDK 21 and root Maven wrapper. Serialize shared database runs. Exercise representative old USER/shared records and old receipts, not an unrelated full historical matrix.

Use one focused browser observation with prepared fixtures for changed Claim/Release controls, administrator release and conflict refresh. Cover detailed combinations in automated tests. Package through the selected verification command without repeating an identical successful build. Stop temporary processes started for checks after identity verification and record final state.

Candidate REUSED evidence, subject to final change review, is F016 revision 2 handover for unchanged target identity, membership-provider behavior and legacy creation evidence; F015 revision 2 for unchanged recovery-page internals and independent read-only activation; F011 revision 1 for unchanged stage routing. Claim enforcement, initial USER ownership, new operations and changed evidence formats need fresh evidence. Historical owning-feature gaps remain with those features.

Completion requires closed contracts, implemented R01–R07, relevant AC01–AC08 checks with actual results, reviewed diff and updated handover. A required blocked check stays explicit. This documentation phase checks content, local links and whitespace only.

## Closed-contract implementation details

P02 updates all three strict HUMAN_TASK schemas and both MinimalDefinitionLoader/RoutingDefinitionModel paths, preserving original canonical JSON and capability checks. Add the compiled TaskClaimPolicy and new validator provenance. Use the specified migration 005 and retain original target indexes. Validate null-aware SQL checks against both legacy closed and new open USER rows.

P03 implements ClaimWorkflowTask, ReleaseWorkflowTask and AdministrativelyReleaseWorkflowTask, their WorkflowOperation values, typed replay overloads, validation and documented error precedence. Extend existing RuntimeJson request-v3 normalization with new operation-specific shapes; write result-v5 and retain strict historical v3/v4 readers. Claim/release emits one history fact and durable event with no progression. Implement creation-details v4/event v3, ownership-details/event v1, and type/version-specific decoder and delivery dispatch. Preserve required task/activity links and event byte limits. Keep reason text in authorized history/receipts only.

P04 adds the O04 HTTP mutation/replay routes and adapter methods under existing explicit command activation. Add exact contract permission flags and pinned policy. Extend immutable UI command envelopes for claim/release/admin-release, preserving existing start/complete exports. Provide a reason input required for administrator release (1000 UTF-16 units), owner release without a reason, conflict refresh, and safe uncertain-response handling. Membership removal retains release-only exact-task access and inbox presence while business access holds; avoid expanding general instance/history/submission permissions. Read-only composition needs no ownership adapter.

P05 fresh checks additionally cover accepted O03/O05 choices: eligible-peer visibility, former-member inbox pagination and exact release without completion, own replay after privilege removal, reason rejection/retention and omission from external events, explicit same-owner claim/unassigned-release conflict, old closed rows with null ownership, old receipt replay after later claim changes, and all three definition decoding paths including omitted versus explicit OPTIONAL hashes. These are changed-path checks, not a broad historical acceptance rerun. Current product decisions and engineering contracts are closed; runtime/provider feasibility remains subject to this verification after implementation authorization.