# Agent instructions

These instructions apply throughout this repository. Follow explicit user instructions and any applicable instructions in more specific `AGENTS.md` files. Keep changes focused on the requested task.

## Start with project context

- Inspect `git status --short` and the relevant files before editing. Preserve unrelated user changes; do not reset, overwrite or stage them.
- Read [README.md](README.md), [constitution](docs/constitution.md), [constraints](docs/constraints.md) and [assumptions](docs/assumptions.md) before architectural or behavioral work. Use [design sources](docs/design-sources.md) to find the authoritative detailed design.
- Treat AGREED decisions as constraints. Keep PROPOSED, PROVISIONAL, OPEN and DEFERRED items distinct; examples are not finalized contracts. Resolve material ambiguity before implementing dependent behavior, while continuing independent work.

## Specification-driven changes

- Follow the [development workflow](docs/specification-driven-development.md). Substantial features belong in `docs/features/<feature-id>-<name>/`, with `spec.md`, `plan.md`, `tasks.md` and `evidence.md` as the work progresses.
- Specify observable behavior, errors and acceptance scenarios before dependent code or migrations. Reference applicable CON/ASM identifiers; keep schemas, examples, implementation and evidence consistent. Do not invent unresolved API fields, business rules or persistence mappings.
- Small maintenance changes may use a concise change description instead of a feature document set. Avoid speculative abstractions, extra modules and unrelated refactoring.

## Architecture and implementation

- Follow [coding standards](docs/coding-standards.md) when writing or changing code, especially naming, responsibility boundaries, constructor injection, JPA/Lombok behavior and error handling.
- Keep exactly two Maven modules: `workflow-core` is the ordinary Spring/JPA library JAR; `workflow-test-host` is the executable development host. Use packages inside core for models, persistence, services, expressions and integration.
- Core must not depend on APOLO backend implementation or the test host. Its public contracts use workflow-owned or standard Java types, without exposing persistence entities or security/messaging-provider objects.
- Hosts own endpoints, business data/rules, identity, authorization policy, datasource, transaction infrastructure and external delivery. Core must not create competing runtime infrastructure or change global host configuration.
- Prepare for core to become a sibling module inside APOLO. Preserve one authoritative implementation and the library boundary; defer reparenting and coordinate changes until the source move is requested.
- Follow existing Java/POM conventions; compile against Java 17 APIs/bytecode and use JDK 21 for the current build/runtime. Declare direct dependencies in the module using them. Read current POMs for versions instead of copying old reference values.
- Prefer explicit constructor dependencies and focused classes. Use Lombok deliberately; avoid generated `toString`, equality or hash code that traverses JPA relationships or includes mutable state unintentionally.
- Keep synchronous business changes, workflow/task updates, history, command receipts and durable event handoff in the host transaction. Preserve concurrency protection and idempotency; do not automatically retry business actions or duplicate an effect in host orchestration and a service node.
- Preserve immutable published workflow definitions and pinned workflow versions. Task types use retained catalog rows with codes immutable from creation, editable metadata and authoring selectability, without a business-version/publication lifecycle. Human-node contracts belong to the pinned workflow; activities own catalog references and tasks inherit them. Disabling selectability must not break published workflows, including future starts.
- Support multiple directly assigned tasks per activity within one active process position. Keep detailed completion-policy proposals distinct from agreed scope until specified. Use the selected Hibernate-managed BIGINT/Long JPA versions and an early checked instance write for all existing-instance mutations; child writes alone do not protect the aggregate. Capture the actual final managed version. Provider/host behavior remains subject to verification.
- APOLO stores workflow instance IDs and queries the engine API; its data owns booking links, authorization and review membership. Core's optional business_type/business_id pair is immutable from creation, both present or both null, nonunique and independent of membership/access. Establish required host links within the start transaction before dependent actions. Do not require list-valued variables or a particular host association table.
- Structured task results are bounded JSON under the pinned human-node contract, distinct from task scalar inputs and mutable execution variables. Store the immutable accepted submission once in TASK_COMPLETED.details_json and expose authorized exact-task/activity APIs. Include the full result in versioned idempotency normalization; validate structure in core and business meaning in the host. Preserve same-transaction final-submission visibility, rollback and operational retention. No task-result table/column, automatic variable merging, draft saving or partial-task lifecycle is implied. Exact schema features, bounds and API/codec contracts remain specification work.
- JSON variables supplement scalar types with declared bounded objects/arrays in workflow_variable.json_value. Use authorized whole-value replacements and explicit start/service bindings under the instance claim; fingerprint full values and retain write/routing evidence. Expose only detached/read-only projections with allowlisted bounded object/index navigation. Preserve scalar/null compatibility, immutable submissions, APOLO membership and frozen review material. No implicit merge, nested mutation, arbitrary Java serialization or JSON-path indexing is implied; exact contracts and expression/provider safety remain verification gates.
- Validate trusted actor, authorization, assignment and allowed inputs during execution. Restricted expressions must fail on invalid input/evaluation and cannot access arbitrary application objects.
- Core owns `db/changelog/workflow/workflow-master.xml`; hosts execute migrations. APOLO remains the single production migration owner. Preserve applied changeset identities/paths, add ordered changesets, and keep host fixtures outside core's master. JPA must not independently create/update production schema.
- Keep the optional business decision engine, advanced task/orchestration features and later designer work outside the current slice unless explicitly selected and specified.

## Build and verification

- **Current owner policy: do not add or run automated tests unless the user changes that instruction.** Preserve existing test sources. Document acceptance scenarios and mark unperformed checks NOT RUN.
- Use the Maven wrapper from the repository root with `JAVA_HOME` pointing to an installed JDK 21. Verify the selected JDK rather than assuming the shell default is correct. On Windows: `./mvnw.cmd -Dmaven.test.skip=true package`. On other systems: `./mvnw -Dmaven.test.skip=true package`.
- Do not use plain `test`, `verify` or `package` commands that could run tests. Do not build merely for a documentation-only change; inspect content, links and whitespace instead.
- Start the host or access a database only within the task's authorized scope. Startup runs Liquibase. The current local database is `apolo_workflow_dev` at `localhost:1433` with Windows authentication; see the README for the matching native DLL and launch command.
- For temporary verification, record the process you started and stop that process afterward unless asked to leave it running. Check its identity before stopping it; never kill an unrelated process to free a port. Report final process state.
- Do not treat packaging or connection success as proof of rollback, concurrency, authorization, expression safety or recovery. Record commands, observations and evidence gaps accurately.

## Files, Git and handoff

- Use `rg` for scoped searches. Keep credentials, private runtime data, logs, native DLLs and generated `target/` artifacts out of commits. Avoid machine-specific absolute paths in shared instructions/configuration where relative paths suffice.
- On Windows, use PowerShell file operations with literal paths; verify resolved targets remain inside the intended workspace before directory moves or recursive deletion.
- Review the final diff and run `git diff --check`; check newly added documentation too. When committing, stage only intended files and inspect `git diff --cached --check`. Commit or push when the user requests it.
- Finish with what changed, how it was checked, unresolved issues and whether changes are committed. Keep the constitution and feature records current when behavior or decisions change; do not claim planned functionality is implemented.
