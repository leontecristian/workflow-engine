# Project constitution and specification guide

This folder governs development of the workflow project through specifications that define observable behavior before implementation.

Read these documents in order:

1. [Project constitution](constitution.md): enduring architectural principles, required guarantees, scope and amendment rules.
2. [Constraints](constraints.md): confirmed architectural, delivery and current working boundaries, with stable CON identifiers.
3. [Assumptions](assumptions.md): unverified expectations, responsible roles, required evidence and consequences if an expectation fails, with stable ASM identifiers.
4. [Specification-driven development](specification-driven-development.md): how to turn a requirement into a specification, implementation plan, tasks and recorded evidence.
5. [Design sources and decision status](design-sources.md): provenance, unresolved decisions and the distinction between agreed design and implemented behavior.
6. [Coding standards](coding-standards.md): practical clean-code guidance for Java, Spring, JPA and Lombok, applied during implementation and review.

The constitution is version **2.2.0**, established on **2026-10-24** and amended on **2026-10-25** to incorporate database design v1.4, including structured task submissions and JSON execution variables, and the project owner's subsequent decisions. It codifies the agreed direction; it does not establish that the planned engine has been implemented.

## Document responsibilities

The constitution defines constraints that all feature specifications must respect. The consolidated design in `apolo-docs` remains the detailed architectural baseline, with scoped superseding decisions from [database design v1.4](../../apolo-docs/workflow-engine/architecture/Workflow_Engine_Database_Design_v1.md) documented in [design sources](design-sources.md). Feature specifications will define exact behavior within those constraints. Plans describe implementation choices; tasks identify bounded changes; evidence records what was actually checked.

The constraints register makes confirmed boundaries easy to reference. The assumptions register identifies expectations that need verification and keeps provisional business choices distinct from mandatory guarantees. Both derive from the existing design; they do not finalize its open contracts or change the constitution version.

New explicit owner decisions take precedence over older documentation. Record their effect in the relevant documents. Where documents conflict and no superseding decision exists, expose the conflict and resolve it with the owner rather than silently choosing a new architecture.

## Documentation and feature structure

Project-wide guidance lives in `docs/`. Individual feature specifications live in its [features subfolder](features/README.md). Create a feature folder when its content is being written:

```text
workflow-engine/
  docs/
    README.md
    constitution.md
    constraints.md
    assumptions.md
    specification-driven-development.md
    design-sources.md
    coding-standards.md
    features/
      README.md
      <feature-id>-<name>/
        spec.md
        plan.md
        tasks.md
        evidence.md
        schemas/       # when machine-readable contracts are needed
        examples/      # when concrete contract examples are needed
```

[F001 — Initial database schema with Liquibase](features/F001-initial-database-schema/spec.md) is the first feature specification, READY for schema implementation with an explicit local verification runbook. The generic folder above describes the convention. Remaining contracts include Workflow Definition v1, Expression Policy v1, the simple task catalog and human-node/host-capability contracts, structured task submissions, JSON execution variables, and runtime command integration. Their exact schemas and unresolved behavior remain to be specified.

Build and local startup instructions remain in the [project README](../README.md). Current work follows the owner's **no-tests** instruction; see the [verification policy](specification-driven-development.md#verification-and-current-no-tests-policy).
