# Specification-driven development

This workflow operationalizes [constitution v3.0.0](constitution.md). Its purpose is to make behavior clear before implementation and leave a useful handover afterward. Keep documentation proportional to the change; the four files below are responsibilities, not a requirement for four lengthy reports.

## Four-document standard and authority

Substantive features use `docs/features/<feature-id>-<name>/`:

- **`spec.md` — what and why.** Defines required behavior, scope and acceptance criteria.
- **`plan.md` — how.** Defines the intended technical approach.
- **`tasks.md` — execution.** Tracks ordered work and completion.
- **`implementation.md` — actual results.** Records what exists, deviations, verification and remaining work; it is the handover for the next session.

The specification governs behavior. A plan is an intention, a checked task records completion of its stated condition, and an implementation record describes observed reality. If implementation discovers a requirement change, update the specification and reconcile the plan and tasks before dependent work. Recording a deviation in implementation.md does not approve a new requirement.

Do not repeat requirements, architecture or status narratives across files. Plans reference requirement IDs; tasks reference the relevant plan steps and requirements; implementation records reference completed tasks and acceptance scenarios. Keep execution status in tasks.md and actual verification results in implementation.md.

Small fixes, simple repository changes and maintenance may use a concise change description with relevant verification instead of a new feature folder. Within a substantive feature, omit irrelevant sections and keep unchanged material linked. Add detail only where complexity, risk or handover needs justify it.

## 1. Specify the behavior

In spec.md, identify the feature, revision/date, status, owner and applicable constitution version. Describe the problem and intended outcome, scope, requirements, observable behavior and acceptance scenarios. Include business rules, inputs/results, errors, constraints and unresolved decisions where relevant.

Use stable requirement/scenario IDs for substantial behavior. Reference applicable [CON constraints](constraints.md), [ASM assumptions](assumptions.md) and [design sources](design-sources.md). Keep AGREED, PROPOSED, PROVISIONAL, OPEN and DEFERRED distinct; examples are not automatically contracts. An acceptance scenario describes a required property and does not authorize an automated test.

Resolve material ambiguity before dependent implementation while continuing independent work. Choose routine engineering details within the agreed contract. Readiness requires clear affected contracts and acceptance criteria, not resolution of unrelated APOLO policies or later designer decisions.

Feature-document statuses remain DRAFT, READY, IMPLEMENTED and SUPERSEDED. IMPLEMENTED means the specified code is present; it does not imply every behavior has been verified or that production acceptance is complete.

## 2. Plan the implementation

In plan.md, reference the specification revision and explain the technical approach, affected modules/packages, consequential decisions and any migrations. Include relevant integration boundaries, failure handling, compatibility and a concise verification procedure. Put intended checks here; actual commands, outcomes and gaps belong in implementation.md.

Review only applicable constitutional concerns and record material findings or open gates. In particular, preserve core/host ownership, the shared transaction and migration boundary, version pinning and catalog compatibility, aggregate concurrency/idempotency, authorization/history/durable handoff, exact data and expression semantics, and platform compatibility. Refer to project guidance rather than copying the full architecture checklist into every feature. Follow the [package conventions](package-structure.md) and [coding standards](coding-standards.md).

Resolve a conflict through a compliant design or the constitution's amendment process before dependent implementation. No additional approval ceremony is needed for ordinary choices within an agreed contract.

## 3. Track execution

Use tasks.md for ordered, actionable work. Each task states its dependencies, affected components and completion condition, with links or IDs to the relevant plan and requirements. Identify enabling tasks only when useful.

Keep progress accurate. A task is complete only when its stated condition is met; a recorded NOT RUN or BLOCKED check is not a PASS. Prefer a small usable slice over speculative infrastructure. Do not add unrelated functionality to make every check appear complete.

## 4. Record actual results and hand over

Update implementation.md as work proceeds. A useful structure is:

```markdown
# Implementation — <feature ID and name>

## 1. Implementation Summary
## 2. Components and Files Changed
## 3. Technical Decisions and Deviations
## 4. Verification
## 5. Known Limitations
## 6. Remaining Work
```

Use this structure proportionately; empty sections and repeated inventories are unnecessary. Record the applicable specification/code revision or worktree state, completed scope and task references, important implementation decisions and deviations. Explain what remains unfinished so a new Codex session can continue from the repository.

The Verification section records:

- Checks actually performed, with relevant commands/manual actions, environment and artifacts.
- Requirement/scenario coverage and PASS, FAIL, NOT RUN or BLOCKED outcomes with reasons.
- Build results, behavioral observations and known verification gaps, clearly separated.
- Process cleanup and outstanding integration or production gates when relevant.

Preserve relevant historical verification, including failed attempts, later corrections and revision boundaries. Clearly date historical observations; do not present them as fresh checks. A build or database connection does not prove rollback, concurrency, authorization, expression safety or recovery.

Before handoff, reconcile spec, plan, tasks, implementation, code and technical artifacts. A requirement-to-task-to-result link should be possible without reconstructing the chat. implementation.md records reality; it never silently becomes the specification.

## Technical artifacts and project references

Keep architecture, database and language designs at project level and link to them. Schemas, migrations, representative examples and focused technical contracts may exist where needed; they are technical deliverables, not additional mandatory process documents. Avoid duplicating their content across feature files.

Do not create a separate evidence.md or verification.md for routine feature tracking. Actual evidence belongs in implementation.md; planned verification belongs in plan.md. Existing detailed runbooks may be retained inside plans, and historical evidence may be retained inside implementation records without shortening away facts. Raw logs/generated artifacts remain outside commits under the existing ignore policy.

## Verification and current no-tests policy

The owner's current instruction remains **do not create or run automated tests unless explicitly authorized**. Preserve existing test sources. Document acceptance scenarios and mark unperformed checks NOT RUN; this policy does not waive required production guarantees.

For authorized implementation builds, select JDK 21 and use the root wrapper:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

Do not build for documentation-only changes; inspect content, links and whitespace. Run application/database checks only within the task's scope. Host startup runs Liquibase and can modify the development database. Record and stop temporary processes you start unless asked to leave them running, after checking their identities. Report final process state.

Keep implementation completion and verified behavior separate. Required rollback, concurrency, idempotency, expression restrictions, authorization and durable recovery need appropriate evidence before production acceptance.

## Feature navigation

Use the [feature index](features/README.md) for current delivery status and sequencing. Specifications link to the relevant [design sources](design-sources.md); this guide does not duplicate the roadmap or change the constitution and architectural constraints.
