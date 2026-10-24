# Coding standards

These standards apply to Java, Maven configuration and related implementation work in `workflow-core` and `workflow-test-host`. They make the [constitution](constitution.md) and [constraints](constraints.md) practical during coding and review. Feature contracts remain in [docs/features/](features/README.md); these standards do not settle open business rules, schemas or persistence mappings.

Prefer clear, maintainable code that implements the agreed behavior. Apply the guidance proportionately: there are no arbitrary method-length limits, mandatory design patterns or requirements for an interface around every class. Preserve existing conventions and keep unrelated cleanup out of a feature change.

## 1. Name concepts and operations precisely

- Use the design vocabulary consistently: definition, definition version, draft revision, instance, activity, task, task type, outcome and command. Do not use these terms interchangeably.
- Name classes for their responsibility and methods for their operation. Prefer `publishDefinition` or `resolveAssignee` over vague names such as `process`, `handleData` or `doWork` when the domain meaning is known.
- Use standard Java naming: PascalCase types, camelCase methods/fields, uppercase constants and lowercase packages. Prefer full words to unexplained abbreviations.
- Boolean names should read as predicates, such as `isPublished` or `hasSupportedOutcome`. Avoid double negatives and boolean parameters whose meaning is unclear at the call site.
- Give meaningful names to policy values. Define limits, units and defaults in the relevant specification/configuration rather than hiding unexplained numbers in code. Do not turn illustrative design values into constants before they are agreed.

## 2. Keep responsibilities focused

- Give each class a cohesive purpose. Separate graph/routing logic, application orchestration, persistence, document codecs and host adaptation using packages inside core.
- Keep methods at a coherent level of abstraction. Extract a method when it names a useful operation, isolates a policy or removes meaningful repetition, rather than merely to meet a line count.
- Prefer guard clauses for invalid input and unsupported state when they make the normal path easier to follow. Keep branching and loops explicit; avoid nested ternaries and streams with hidden mutations or database calls.
- Thin host controllers translate requests and responses. Application services coordinate commands. Repositories handle persistence. Keep APOLO business policy in host adapters/services.
- Use the smallest visibility needed. Avoid a global utility class, catch-all service or mutable context object that exposes all engine internals.

## 3. Use SOLID, DRY and simplicity with judgment

- Keep reasons for change separate: changing JSON representation should not require rewriting execution policy, and changing a host adapter should not change public workflow contracts.
- Introduce interfaces for actual integration boundaries, substitution needs or multiple implementations. Keep them focused on what callers need; avoid interfaces created only to mirror a single implementation.
- Depend on neutral contracts at the host boundary. Prefer composition to inheritance and avoid deep base-class hierarchies or generic CRUD frameworks without a demonstrated need.
- Centralize repeated business knowledge and invariants. Similar-looking code with different ownership or expected evolution need not share an abstraction.
- Implement the current specified need. Defer speculative extension hooks, new modules, caches and configurable behavior until they solve a concrete problem.

## 4. Make dependencies and side effects visible

- Prefer constructor injection and `final` fields for required service dependencies. Avoid field injection, service locators, static mutable state and fetching arbitrary beans from the application context.
- Separate deterministic validation/calculation from database writes and external effects where practical. A method name and contract should make mutation apparent.
- Keep ordinary Spring singleton services free of per-command mutable fields. Pass command data explicitly; do not share mutable expression contexts between requests.
- Supply time and other variable inputs explicitly where they affect workflow decisions. Do not hide current-time, random or global-state reads inside routing logic.
- Bind host configuration at the host boundary. Keep environment paths, credentials and production infrastructure settings out of core.

## 5. Prefer explicit contracts and controlled mutation

- Use immutable commands, results and value objects where practical. Java records are suitable for simple immutable carriers when their semantics fit; they are not JPA entities. Copy mutable collections when ownership crosses a boundary: a final reference alone does not make its contents immutable.
- Use enums or value types for established domain concepts when they improve correctness. Do not invent public types or wire encodings before the feature contract is settled.
- Define nullability and absence explicitly. Use `Optional` where absence is a legitimate return outcome; avoid nullable `Optional` values or adding `Optional` to entity fields and parameters by default.
- Return empty collections where the contract means no results; preserve the distinction between absent, empty and invalid values when it matters. Do not return `null` or a success-shaped default to hide a failure.
- Validate external data at boundaries and enforce domain invariants where state changes. Client validation does not replace server checks; advisory queries do not replace transactional authorization/state checks.
- Preserve numeric precision with decimal arithmetic and explicit rounding policy. Define date/time types and conversion rules from the specification; do not silently use the workstation timezone or select an unresolved persistence encoding.

## 6. Use Lombok deliberately

- Use focused annotations such as `@Getter` and `@RequiredArgsConstructor` when the generated code is clear and appropriate. An explicit constructor is preferable when it must validate or normalize inputs.
- Avoid blanket `@Data` on JPA entities. Generated setters, equality, hash codes and string representations can expose mutation, traverse relationships or depend on unstable state.
- Choose entity equality/hash-code behavior deliberately, accounting for generated identifiers, persistence lifecycle and proxies. Do not use all fields or mutable relationships by default; the exact strategy belongs with the persistence design.
- Exclude relationships, sensitive fields and large payloads from generated `toString`. Do not log an entire entity just because Lombok makes it easy.
- Use builders only when they improve construction clarity. Do not use them to bypass required invariants or generate every possible constructor/accessor without a caller need.

## 7. Treat JPA entities as persistence models

- Keep entities and repositories inside core's persistence boundary. Map to workflow-owned commands/results rather than exposing entities through public integration or HTTP contracts.
- Provide the constructor/access pattern required by the chosen JPA mapping. Do not make entities immutable by applying blanket final fields/classes without considering persistence requirements.
- Prefer meaningful state-changing methods over unrestricted setters where invariants are involved. Do not let arbitrary callers change published content, task assignment or lifecycle state directly.
- Choose fetch plans deliberately. Avoid globally eager relationships as a workaround for lazy loading, fetching an entire history to answer a small query, or per-row repository calls in loops. Use projections, pagination and targeted queries where appropriate.
- Keep cascades and orphan removal explicit and consistent with ownership. A relationship must not accidentally delete immutable definitions, task history or audit records.
- Keep JPA mappings, database constraints and Liquibase changesets aligned. External APOLO references remain logical identifiers. The concurrency-token mapping is still a specification/verification decision.

## 8. Keep transaction behavior understandable

- Put command transaction coordination at clear service boundaries that actually participate in the host transaction. Do not assume an internal self-call applies a Spring transaction interceptor.
- Keep required domain updates, workflow/task state, history, receipts and durable handoff atomic. Avoid independent transactions or asynchronous work for effects that belong in that atomic result.
- Handle failures in a way that preserves rollback. Catch an exception only when there is a clear recovery, translation or cleanup responsibility; do not swallow it and let a partial command commit.
- Recheck trusted identity, permissions, assignment, input allowlists and concurrency during mutation. Explicitly distinguish a normal negative routing result from an evaluation failure.
- Keep retries and command receipt handling consistent with the specified idempotency contract. Do not add automatic business-action retries to conceal concurrency or persistence errors.
- Record external delivery durably and let host infrastructure perform it after commit. Database rollback cannot undo an email or non-transactional external request.

## 9. Make errors and diagnostics useful

- Distinguish invalid input, missing resources, forbidden operations, conflicts and infrastructure failures. Use the specified error contract; avoid unrelated generic exceptions or string parsing to identify failure categories.
- Translate errors at an appropriate boundary and preserve the cause for internal diagnostics. Do not leak provider exceptions, SQL, stack traces or secrets through public responses.
- Avoid broad catch blocks unless they serve a defined boundary/cleanup purpose. Never use an empty catch block or replace a failed routing evaluation with `false` or an approval default.
- Log with parameterized messages and useful command/instance correlation. Avoid logging the same exception at every layer. Operational logs supplement, rather than replace, authoritative workflow/task history.
- Log the minimum needed information. Do not log full business forms, credentials, entity graphs or arbitrary expression contexts.

## 10. Keep comments, dependencies and changes maintainable

- Let names and structure explain what the code does. Comments should explain a non-obvious reason, invariant, transaction assumption or compatibility tradeoff.
- Document public contract preconditions, outcomes, failure behavior and side effects. Avoid boilerplate Javadoc that only repeats the method name.
- Remove obsolete comments and dead code in the area being changed. Use version control instead of retaining commented-out implementations. Link consequential TODOs to a tracked requirement or open decision.
- Use Java 17-compatible APIs and the current POM-managed versions. Add dependencies only for a concrete need in the module that uses them; do not upgrade unrelated libraries or add formatting/static-analysis plugins as incidental cleanup.
- Follow the surrounding formatting and imports. Avoid wildcard imports and unrelated reformatting; keep diffs easy to review.

## Review and verification

For a code change, review whether its names express domain intent, dependencies respect module boundaries, state changes preserve invariants, failure paths preserve atomicity, and public behavior matches the feature specification. Pay particular attention to JPA lifecycle behavior and code generated by Lombok.

The current owner instruction is **no automated tests**. Do not create or run tests unless the user changes that instruction. Preserve existing test sources. Use appropriate inspection and, when needed, the documented Maven build with `-Dmaven.test.skip=true`; record unverified acceptance criteria honestly. Documentation-only changes need link, content and whitespace review, not a build or application startup.

These standards guide focused implementation. A refactor that changes behavior, ownership or a public contract must also update the applicable specification and follow the constitution's decision rules.
